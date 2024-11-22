/*
 * Copyright (C) 2020 by Steffen Rehberg under the MIT license:
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package de.geeksfactory.opacclient.apis

import com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import de.geeksfactory.opacclient.i18n.DummyStringProvider
import de.geeksfactory.opacclient.networking.HttpClientFactory
import de.geeksfactory.opacclient.objects.*
import de.geeksfactory.opacclient.searchfields.DropdownSearchField
import de.geeksfactory.opacclient.searchfields.SearchQuery
import de.geeksfactory.opacclient.searchfields.TextSearchField
import okhttp3.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Suite
import org.mockito.AdditionalMatchers.or
import org.mockito.ArgumentMatcher
import org.mockito.hamcrest.MockitoHamcrest.argThat
import org.mockito.Mockito.*


/**
 * Tests for SLUB API
 *
 * @author Steffen Rehberg, Jan 2020
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
        SLUBAccountTest::class,
        SLUBSearchTest::class,
        SLUBMiscellaneousTests::class,
        SLUBAccountMockTest::class,
        SLUBReservationMockTest::class,
        SLUBAccountValidateMockTest::class,
        SLUBSearchMockTest::class,
        SLUBSearchFieldsMockTest::class,
        SLUBGetResultByIdMockTest::class,
        SLUBProlongMockTest::class,
        SLUBProlongMultipleMockTest::class,
        SLUBCancelMockTest::class
)
class SLUBAllTests

class SLUBAccountTest : BaseHtmlTest() {
    private var slub = SLUB()

    init {
        slub.stringProvider = DummyStringProvider()
    }

    @Test
    fun testParseEmptyAccountData() {
        val json = JSONObject(readResource("/slub/account/empty-account.json"))

        val accountdata = slub.parseAccountData(Account(), json)

        assertEquals("1,23 EUR", accountdata.pendingFees)
        assertEquals(DateTimeFormat.shortDate().print(LocalDate("2020-03-31")), accountdata.validUntil)
        assertTrue(accountdata.lent.isEmpty())
        assertTrue(accountdata.reservations.isEmpty())
    }

    @Test
    fun testParseAccountData() {
        val json = JSONObject(readResource("/slub/account/account.json"))
        val fmt = DateTimeFormat.shortDate()
        val lentitem1 = LentItem().apply {
            title = "Der neue Kosmos-Baumführer"
            author = "Bachofer, Mark"
            setDeadline("2019-06-03")
            format = "B"
            id = "bc/31626878"
            barcode = "31626878"
            isRenewable = true
            prolongData = "$format\t$barcode"
        }
        val reserveditem1 = ReservedItem().apply {
            // reserve
            title = "Pareys Buch der Bäume"
            author = "Mitchell, Alan"
            format = "B"
            id = "bc/30963742"
            status = "reserved_pos 1"
            cancelData = "30963742_1"
        }
        val reserveditem2 = ReservedItem().apply {
            // hold
            title = "Welcher Baum ist das?"
            author = "Mayer, Joachim ¬[VerfasserIn]¬"
            format = "B"
            id = "bc/34778398"
            branch = "ZwB Forstwissenschaft"
            status = String.format("hold %s", fmt.print(LocalDate("2019-05-10")))
        }
        val reserveditem3 = ReservedItem().apply {
            // request ready
            title = "Englische Synonyme als Fehlerquellen"
            author = "Meyer, Jürgen"
            format = "B"
            id = "bc/20550495"
            branch = "Zentralbibliothek Ebene 0 SB-Regal"
            status = String.format("request_ready %s", fmt.print(LocalDate("2019-05-04")))
        }
        val accountdata = slub.parseAccountData(Account(), json)

        assertEquals(2, accountdata.lent.size)
        assertEquals(3, accountdata.reservations.size)
        assertThat(lentitem1, samePropertyValuesAs(accountdata.lent[0]))
        assertThat(accountdata.reservations, hasItems(sameBeanAs(reserveditem1),
                sameBeanAs(reserveditem2), sameBeanAs(reserveditem3)))
    }

    @Test
    fun testParseAccountDataIll() {
        val fmt = DateTimeFormat.shortDate()
        val json = JSONObject(readResource("/slub/account/account-ill.json"))
        // hold request filed --> show ill in reservations
        val reserveditem1 = ReservedItem().apply {
            title = "Title1"
            author = "Author1"
            format = "FL"
            branch = "Zentralbibliothek"
            status = "Bestellung ausgelöst"
        }
        // ready for pickup --> show hold in reservations
        val reserveditem2 = ReservedItem().apply {
            title = "Title2"
            author = ""
            format = "FL"
            status = String.format("hold %s", fmt.print(LocalDate("2020-12-04")))
            branch = "Zentralbibliothek Servicetheke"
        }
        // on loan not yet renewable --> show loan in lent items
        val lentitem1 = LentItem().apply {
            title = "Title5"
            author = ""
            setDeadline("2021-01-08")
            format = "FL"
            barcode = "12047398N"
            isRenewable = false
            status = "not_yet_renewable"
        }
        // on loan renewable --> show loan in lent items
        val lentitem2 = LentItem().apply {
            title = "Title6"
            author = ""
            setDeadline("2021-01-08")
            format = "FL"
            barcode = "12047398N"
            isRenewable = true
            prolongData = "FL\t12047398N"
        }
        val accountdata = slub.parseAccountData(Account(), json)

        assertEquals(2, accountdata.reservations.size)
        assertThat(accountdata.reservations, hasItems(sameBeanAs(reserveditem1),
                sameBeanAs(reserveditem2)))
        assertEquals(2, accountdata.lent.size)
        assertThat(accountdata.lent, hasItems(sameBeanAs(lentitem1),
                sameBeanAs(lentitem2)))
    }

    @Test
    fun testParseAccountDataStatus() {
        val json = JSONObject(readResource("/slub/account/account-status.json"))

        val accountdata = slub.parseAccountData(Account(), json)

        assertEquals("renewed 9", accountdata.lent[0].status)
        assertEquals("reserved", accountdata.lent[1].status)
        assertEquals(null, accountdata.lent[2].status)
        assertEquals(false, accountdata.lent[0].isRenewable)
        assertEquals(false, accountdata.lent[1].isRenewable)
        assertEquals(true, accountdata.lent[2].isRenewable)
    }
}

class SLUBSearchTest : BaseHtmlTest() {
    private var slub = SLUB()

    init {
        slub.stringProvider = DummyStringProvider()
    }

    @Test
    fun testParseEmptySearchResults() {
        val json = JSONObject(readResource("/slub/search/empty-search.json"))

        val searchresults = slub.parseSearchResults(json)

        assertEquals(0, searchresults.total_result_count)
        assertTrue(searchresults.results.isEmpty())
    }

    @Test
    fun testParseSearchResults() {
        val json = JSONObject(readResource("/slub/search/simple-search.json"))
        val result1 = SearchResult().apply {
            innerhtml = "<b>Mastering software testing with JUnit 5 comprehensive guide to develop high quality Java applications Boni García</b><br>Garcia, Boni<br>(2017)"
            type = SearchResult.MediaType.BOOK
            id = "id/0-1014939550"
        }
        val result2 = SearchResult().apply {
            innerhtml = """<b>Title with " and &</b><br><br>(2222)"""
            type = SearchResult.MediaType.NONE
            id = "id/123"
        }

        val searchresults = slub.parseSearchResults(json)

        assertEquals(2, searchresults.total_result_count)
        assertThat(result1, samePropertyValuesAs(searchresults.results[0]))
        assertThat(result2, samePropertyValuesAs(searchresults.results[1]))
    }

    @Test
    fun testParseSearchResultsWithNullCreationDate() {
        val json = JSONObject(readResource("/slub/search/search-null_creation_date.json"))
        val result1 = SearchResult().apply {
            innerhtml = "<b>Tu en hagiois patros hēmōn Maximu tu homologetu Hapanta = S.P.N. Maximi Confessoris Opera omnia eruta, Latine transl., notisque ill. opera et studio Francisci Combefis. Adauxit Franciscus Oehler. Accurante et denuo recognoscente J.-P. Migne</b><br>Maximus Confessor"
            type = SearchResult.MediaType.BOOK
            id = "id/0-1093989777"
        }

        val searchresults = slub.parseSearchResults(json)

        assertEquals(1, searchresults.total_result_count)
        assertThat(result1, samePropertyValuesAs(searchresults.results[0]))
    }

    @Test
    fun testParseResultById() {
        val json = JSONObject(readResource("/slub/search/simple-item.json"))
        val expected = DetailedItem().apply {
            addDetail(Detail("Medientyp", "Buch"))
            addDetail(Detail("Titel", "Unit-Tests mit JUnit"))
            title = "Unit-Tests mit JUnit"
            addDetail(Detail("Beteiligte", "Hunt, Andrew; Thomas, David [Autor/In]"))
            addDetail(Detail("Erschienen", "München Wien Hanser 2004 "))
            addDetail(Detail("Erschienen in", "Pragmatisch Programmieren; 2"))
            addDetail(Detail("ISBN", "3446228241; 3446404694; 9783446404694; 9783446228245"))
            addDetail(Detail("Sprache", "Deutsch"))
            addDetail(Detail("Schlagwörter", "Quellcode; Softwaretest; JUnit"))
            addDetail(Detail("Inhaltsverzeichnis", "http://d-nb.info/970689268/04"))
            id = "id/0-1182402208"
            copies = arrayListOf(Copy().apply {
                barcode = "31541466"
                department = "Ausgabe über Ausleihtheke"
                branch = "Bereichsbibliothek DrePunct"
                status = "Ausleihbar"
                statusCode = SearchResult.Status.GREEN
                shelfmark = "ST 233 H939"
            })
            collectionId = "id/0-1183957874"
            cover = "http://vub.de/cover/data/isbn:9783836240956/medium/true/de/vub/cover.jpg" // cover as "note"
        }

        val item = slub.parseResultById(json)

        //details are in unspecified order, see https://stackoverflow.com/a/4920304/3944322
        assertThat(item, sameBeanAs(expected).ignoring("details"))
        assertThat(HashSet(item.details), sameBeanAs(HashSet(expected.details)))
    }

    @Test
    fun testParseResultByIdWithoutStatusphrase() {
        val json = JSONObject(readResource("/slub/search/item_inquery_availability.json"))
        val expected = DetailedItem().apply {
            addDetail(Detail("Medientyp", "Buch"))
            addDetail(Detail("Titel", "Astronomie: die kosmische Perspektive"))
            title = "Astronomie: die kosmische Perspektive"
            addDetail(Detail("Beteiligte",
                    "Bennett, Jeffrey O. [Sonstige Person, Familie und Körperschaft]; Lesch, Harald [Hrsg.]"))
            addDetail(Detail("Erschienen", "München [u.a.] Pearson 2010 "))
            addDetail(Detail("Erschienen in", "Always learning"))
            addDetail(Detail("ISBN", "9783827373601; 3827373603"))
            addDetail(Detail("Sprache", "Deutsch; Englisch"))
            addDetail(Detail("Schlagwörter", "Astronomie"))
            addDetail(Detail("Beschreibung",
                    "CD-ROM-Beil. enth.: Planetariumssoftware SkyGazer Educational-Edition; " +
                    "Hier auch später erschienene, unveränderte Nachdrucke; " +
                    "Auflagenzählung bezieht sich auf die engl. Orig.-Ausg"))
            addDetail(Detail("Inhaltsverzeichnis", "http://d-nb.info/992365538/04"))
            addDetail(Detail("Cover", "http://swbplus.bsz-bw.de/bsz304732311cov.htm"))
            id = "id/0-590996231"
            copies = arrayListOf(Copy().apply {
                barcode = "34163775"
                department = ""
                branch = "Zentralbibliothek"
                status = "inquire_availability https://tu-dresden.de/bu/umwelt/geo/ipg/astro"
                statusCode = SearchResult.Status.UNKNOWN
                shelfmark = "US 1020 B471(5)"
            })
        }

        val item = slub.parseResultById(json)

        assertThat(item, sameBeanAs(expected).ignoring("details"))
        assertThat(HashSet(item.details), sameBeanAs(HashSet(expected.details)))
    }

    @Test
    fun testParseResultByIdCoverAsMaterial() {
        val json = JSONObject(readResource("/slub/search/detaill-cover-jpg-as-material.json"))
        val item = slub.parseResultById(json)
        assertEquals("http://www.netread.com/jcusers2/bk1388/319/9781472533319/image/lgcover.9781472533319.jpg", item.cover)
    }

    @Test
    fun testParseResultByIdCoverSWBPlus() {
        val json = JSONObject(readResource("/slub/search/detail-with-cover-html-swbplus.json"))
        val item = slub.parseResultById(json)
        assertEquals("http://swbplus.bsz-bw.de/bsz352428775cov.jpg", item.cover)
    }

    @Test
    fun testParseResultByIdCopiesInMultipleArrays() {
        val json = JSONObject(readResource("/slub/search/item-copies_in_multiple_arrays.json"))
        val copyFirst = Copy().apply {
            barcode = "10418078"
            department = "Magazin Zeitschriften"
            branch = "Zentralbibliothek"
            status = "Bestellen zur Benutzung im Haus, kein Versand per Fernleihe, nur Kopienlieferung"
            statusCode = SearchResult.Status.YELLOW
            shelfmark = "19 4 01339 0 0024 1 01"
            resInfo = "stackRequest\t10418078"
        }
        val copyLast = Copy().apply {
            barcode = "33364639"
            department = "Magazin Zeitschriften"
            branch = "Zentralbibliothek"
            status = "Bestellen zur Benutzung im Haus, kein Versand per Fernleihe, nur Kopienlieferung"
            statusCode = SearchResult.Status.YELLOW
            shelfmark = "19 4 01339 1 1969 1 01"
            resInfo = "stackRequest\t33364639"
        }

        val item = slub.parseResultById(json)

        assertEquals(19, item.copies.size)
        // the copies arrays may occur in any order
        assertThat(item.copies, hasItems(sameBeanAs(copyFirst), sameBeanAs(copyLast)))
    }

    @Test
    fun testParseResultByIdMultipleParts() {
        val json = JSONObject(readResource("/slub/search/item-multiple_parts_item.json"))
        val volumes = listOf(
                Volume("0-1453040935", "[3]: Principles of digital image processing"),
                Volume("0-1347927328", "[2]: Principles of digital image processing"),
                Volume("0-1347930884", "[1]: Principles of digital image processing")
        )

        // is part of "Undergraduate topics in computer science" but no id (--> collectionid) given
        val item = slub.parseResultById(json)

        assertThat(volumes, sameBeanAs(item.volumes))
        assertNull(item.collectionId)
    }

    @Test
    fun testParseResultByIdUmlaute() {
        val json = JSONObject(readResource("/slub/search/item-with_umlaute_in_title_and_volumes.json"))
        val volume = Volume("0-1149529121", "(inse,5): in 6 Bänden")

        val item = slub.parseResultById(json)

        assertEquals("Urania-Tierreich: in 6 Bänden", item.title)
        assertThat(item.volumes, hasItem(sameBeanAs(volume)))
    }

    @Test
    fun testParseResultByIdThumbnail() {
        val json = JSONObject(readResource("/slub/search/item-fotothek.json"))
        val expected = DetailedItem().apply {
            addDetail(Detail("Medientyp", "Foto"))
            addDetail(Detail("Titel", "Maya"))
            title = "Maya"
            addDetail(Detail("Sprache", "Kein linguistischer Inhalt"))
            addDetail(Detail("Schlagwörter", "Skulptur; Statue; Ortskatalog zur Kunst und Architektur"))
            id = "id/dswarm-67-b2FpOmRldXRzY2hlZm90b3RoZWsuZGU6YTg0NTA6Om9ianwzMzA1NTgxMHxkZl9oYXVwdGthdGFsb2dfMDEwMDMzNg"
            cover = "http://fotothek.slub-dresden.de/thumbs/df_hauptkatalog_0100336.jpg"
            addDetail(Detail("In der Deutschen Fotothek ansehen", "http://www.deutschefotothek.de/obj33055810.html"))
        }

        val item = slub.parseResultById(json)

        assertThat(item, sameBeanAs(expected).ignoring("details"))
        assertThat(HashSet(expected.details), sameBeanAs(HashSet(item.details)))
    }

    @Test
    fun testParseResultByIdLinks() {
        val json = JSONObject(readResource("/slub/search/item-links.json"))
        val expected = DetailedItem().apply {
            addDetail(Detail("Medientyp", "Buch"))
            addDetail(Detail("Titel", "JUnit-Profiwissen: effizientes Arbeiten mit der Standardbibliothek für automatisierte Tests in Java"))
            title = "JUnit-Profiwissen: effizientes Arbeiten mit der Standardbibliothek für automatisierte Tests in Java"
            addDetail(Detail("Beteiligte", "Tamm, Michael [Autor/In]"))
            addDetail(Detail("Erschienen", "Heidelberg dpunkt.Verl. 2013 "))
            addDetail(Detail("ISBN", "3864900204; 9783864900204"))
            addDetail(Detail("Sprache", "Deutsch"))
            addDetail(Detail("Schlagwörter", "Java; JUnit"))
            addDetail(Detail("Beschreibung", "Literaturverz. S. 351"))
            id = "id/0-727434322"
            addDetail(Detail("Inhaltsverzeichnis", "http://www.gbv.de/dms/tib-ub-hannover/727434322.pdf"))
            addDetail(Detail("Inhaltstext", "http://deposit.d-nb.de/cgi-bin/dokserv?id=4155321&prov=M&dok_var=1&dok_ext=htm"))
            addDetail(Detail("Zugang zur Ressource (via ProQuest Ebook Central)", "http://wwwdb.dbod.de/login?url=http://slub.eblib.com/patron/FullRecord.aspx?p=1575685"))
            addVolume(Volume("http://slubdd.de/katalog?libero_mab216187885", "Online-Ausgabe:\nTamm, Michael: JUnit-Profiwissen"))
        }

        val item = slub.parseResultById(json)

        assertThat(item, sameBeanAs(expected).ignoring("details"))
        assertThat(HashSet(expected.details), sameBeanAs(HashSet(item.details)))
    }

    @Test
    fun testParseResultByIdLinksGeneralNoLabel() {
        val json = JSONObject(readResource("/slub/search/item-links_general_no_label.json"))
        val expected = DetailedItem().apply {
            addDetail(Detail("Medientyp", "ElectronicThesis"))
            addDetail(Detail("Titel", "A Study of Classic Maya Rulership"))
            title = "A Study of Classic Maya Rulership"
            addDetail(Detail("Sprache", "Englisch"))
            addDetail(Detail("Schlagwörter", "Archaeology; Latin American history; Ancient history; Native American studies"))
            id = "id/ai-34-b2FpOnBxZHRvYWkucHJvcXVlc3QuY29tOjM0Nzc2Mzg"
            addDetail(Detail("Link", "http://pqdtopen.proquest.com/#viewpdf?dispub=3477638"))
        }

        val item = slub.parseResultById(json)

        assertThat(item, sameBeanAs(expected).ignoring("details"))
        assertThat(HashSet(expected.details), sameBeanAs(HashSet(item.details)))
    }

    @Test
    fun testParseResultByIdResinfo() {
        val json = JSONObject(readResource("/slub/search/item-for-reserve&request.json"))
        val expected = DetailedItem().apply {
            addDetail(Detail("Medientyp", "Buch"))
            addDetail(Detail("Titel", "Der Fürstenzug zu Dresden: Denkmal und Geschichte des Hauses Wettin"))
            title = "Der Fürstenzug zu Dresden: Denkmal und Geschichte des Hauses Wettin"
            addDetail(Detail("Beteiligte", "Blaschke, Karlheinz [Autor/In]; Beyer, Klaus G. [Ill.]"))
            addDetail(Detail("Erschienen", "Leipzig Jena Berlin Urania-Verl. 1991 "))
            addDetail(Detail("ISBN", "3332003771; 9783332003772"))
            addDetail(Detail("Sprache", "Deutsch"))
            addDetail(Detail("Schlagwörter", "Walther, Wilhelm; Albertiner; Albertiner; Fries; Walther, Wilhelm"))
            addDetail(Detail("Beschreibung", "Literaturverz. S. 222 - 224"))
            id = "id/0-276023927"
            copies = arrayListOf(
                    Copy().apply {
                        barcode = "10059731"
                        department = "Freihand"
                        branch = "Zentralbibliothek"
                        status = "Ausgeliehen, Vormerken möglich"
                        statusCode = SearchResult.Status.RED
                        shelfmark = "LK 24099 B644"
                        returnDate = LocalDate(2020, 2, 5)
                        resInfo = "reserve\t10059731"
                    },

                    Copy().apply {
                        barcode = "30523028"
                        department = "Freihand"
                        branch = "ZwB Erziehungswissenschaften"
                        status = "Benutzung nur im Haus, Versand per Fernleihe möglich"
                        statusCode = SearchResult.Status.GREEN
                        shelfmark = "NR 6400 B644 F9"
                    },
                    Copy().apply {
                        barcode = "20065307"
                        department = "Magazin"
                        branch = "Zentralbibliothek"
                        status = "Ausleihbar, bitte bestellen"
                        statusCode = SearchResult.Status.YELLOW
                        shelfmark = "65.4.653.b"
                        resInfo = "stackRequest\t20065307"
                    }
            )
            isReservable = true
        }

        val item = slub.parseResultById(json)

        assertThat(item, sameBeanAs(expected).ignoring("details"))
        assertThat(HashSet(expected.details), sameBeanAs(HashSet(item.details)))
    }
}

class SLUBMiscellaneousTests : BaseHtmlTest() {
    private var slub = SLUB()

    init {
        slub.init(Library().apply {
            data = JSONObject().apply {
                put("baseurl", "https://test.de")
                put("illrenewurl", "https://test-renew.de")
            }
        }, HttpClientFactory("test"), false)
    }

    @Test
    fun testGetShareUrl() {
        val expected = slub.getShareUrl("id/123", "not used")
        assertEquals(expected, "https://test.de/id/123")
    }
}

@RunWith(Parameterized::class)
class SLUBAccountMockTest(@Suppress("unused") private val name: String,
                          private val response: String,
                          private val expectedMessage: String?,
                          private val expectedException: Class<out Exception?>?,
                          private val expectedExceptionMsg: String?) : BaseHtmlTest() {
    private val slub = spy(SLUB::class.java)

    init {
        slub.init(Library().apply {
            data = JSONObject().apply {
                put("baseurl", "https://test.de")
                put("illrenewurl", "https://test-renew.de")
            }
        }, HttpClientFactory("test"), false)
    }

    private val account = Account().apply {
        name = "x"
        password = "x"
    }

    @Test
    fun testCheckAccountData() {
        doReturn(response).`when`(slub).httpPost(any(), any(), any())
        if (expectedException != null) {
            val thrown = assertThrows(expectedExceptionMsg, expectedException
            ) { slub.requestAccount(account, "", null) }
            assertTrue(thrown!!.message!!.contains(expectedExceptionMsg!!))
        } else {
            val actual = slub.requestAccount(account, "", null)
            assertEquals(expectedMessage, actual.optString("message"))
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
                // validate: status as string
                arrayOf("String - OK", "{\"status\":\"1\",\"message\":\"credentials_are_valid\"}", "credentials_are_valid", null, null),
                arrayOf("String - Error", "{\"message\":\"error_credentials_invalid\",\"arguments\":{\"controller\":\"API\",\"action\":\"validate\",\"username\":\"123456\"},\"status\":\"-1\"}", null, OpacApi.OpacErrorException::class.java, "error_credentials_invalid"),
                // POST not accepted, malformed request, e.g. invalid action
                arrayOf("Malformed", "<!doctype html><title>.</title>", null, OpacApi.OpacErrorException::class.java, "Request didn't return JSON object"),
                // delete: status as int or string
                arrayOf("Int/string - OK", "{\"status\":1,\"message\":\"Reservation deleted\"}", "Reservation deleted", null, null),
                arrayOf("Int/string - Error", "{\"status\":\"-1\",\"message\":\"Item not reserved\"}", null, OpacApi.OpacErrorException::class.java, "Item not reserved"),
                // pickup: status as boolean
                arrayOf("Boolean - OK", "{\"status\":true,\"message\":\"n\\/a\"}", "n/a", null, null),
                arrayOf("Boolean - Error", "{\"status\":false,\"message\":\"Ungültige Barcodenummer\"}", null, OpacApi.OpacErrorException::class.java, "Ungültige Barcodenummer")
        )
    }
}

@RunWith(Parameterized::class)
class SLUBReservationMockTest(@Suppress("unused") private val name: String,
                              private val item: DetailedItem,
                              private val useraction: Int,
                              private val selection: String?,
                              private val responsePickup: String?,
                              private val responseReserveOrRequest: String?,
                              private val expectedResult: OpacApi.ReservationResult) : BaseHtmlTest() {
    private val slub = spy(SLUB::class.java)

    init {
        slub.init(Library().apply {
            data = JSONObject().apply {
                put("baseurl", "https://test.de")
                put("illrenewurl", "https://test-renew.de")
            }
        }, HttpClientFactory("test"), false)
    }

    private val account = Account().apply {
        name = "123456"
        password = "x"
    }

    @Test
    fun testReservation() {
        if (responsePickup != null) {
            doReturn(responsePickup).`when`(slub).httpPost(any(),
                    argThat(IsRequestBodyWithAction("pickup")), any())
        }
        if (responseReserveOrRequest != null) {
            doReturn(responseReserveOrRequest).`when`(slub).httpPost(any(),
                    or(argThat(IsRequestBodyWithAction("stackRequest")),
                            argThat(IsRequestBodyWithAction("reserve"))), any())
        }

        val result = slub.reservation(item, account, useraction, selection)
        assertThat(result, sameBeanAs(expectedResult))
    }

    companion object {
        // this item has already been tested in testParseResultByIdResinfo so we can rely on parseResultById here
        private val json = JSONObject(BaseHtmlTest().readResource("/slub/search/item-for-reserve&request.json"))
        private val itemRequestAndReserve = SLUB().parseResultById(json)
        private val itemRequest = SLUB().parseResultById(json)
        private val itemReserve = SLUB().parseResultById(json)
        private val itemNone = SLUB().parseResultById(json)

        init {
            itemRequest.apply {
                copies = copies.filter {
                    it.resInfo?.startsWith("stackRequest") ?: false
                }
            }
            itemReserve.apply {
                copies = copies.filter {
                    it.resInfo?.startsWith("reserve") ?: false
                }
            }
            itemNone.apply { copies = copies.filter { it.resInfo == null } }
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
                arrayOf("Single reservable copy (with multiple pickup branches)",
                        itemReserve,
                        0,
                        null,
                        null,
                        null,
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.SELECTION_NEEDED).apply {
                            actionIdentifier = OpacApi.ReservationResult.ACTION_BRANCH
                            this.selection = listOf(
                                    mapOf("key" to "reserve\t10059731\tzell1", "value" to "Zentralbibliothek"),
                                    mapOf("key" to "reserve\t10059731\tbebel1", "value" to "ZwB Erziehungswissenschaften"),
                                    mapOf("key" to "reserve\t10059731\tberg1", "value" to "ZwB Rechtswissenschaft"),
                                    mapOf("key" to "reserve\t10059731\tfied1", "value" to "ZwB Medizin"),
                                    mapOf("key" to "reserve\t10059731\ttha1", "value" to "ZwB Forstwissenschaft"),
                                    mapOf("key" to "reserve\t10059731\tzell9", "value" to "Bereichsbibliothek Drepunct")
                            )
                        }
                ),
                arrayOf("Make reservation (for selected pickup branch)",
                        itemReserve,
                        OpacApi.ReservationResult.ACTION_BRANCH,
                        "reserve\t10059731\tbebel1",
                        null,
                        "{\"status\":1,\"message\":\"Ihre Vormerkung wurde vorgenommen|Diese Vormerkung läuft ab am 23 Apr 2020|Position in der Vormerkerliste 1\",\"arguments\":{\"controller\":\"API\",\"action\":\"reserve\",\"barcode\":\"10059731\",\"username\":\"123456\",\"PickupBranch\":\"zell1\"}}",
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.OK, "Ihre Vormerkung wurde vorgenommen|Diese Vormerkung läuft ab am 23 Apr 2020|Position in der Vormerkerliste 1")
                ),
                arrayOf("Single requestable copy with single pickup point",
                        itemRequest,
                        0,
                        null,
                        "{\"status\":true,\"message\":\"n\\/a\",\"PickupPoints\":[\"a01\"],\"arguments\":{\"controller\":\"API\",\"action\":\"pickup\",\"barcode\":\"20065307\",\"username\":\"123456\"}}",
                        "{\"status\":true,\"message\":\"Magazinbestellung wurde erfolgreich hinzugefügt.\",\"requestID\":\"2116982\",\"pickupPoint\":\"Zentralbibliothek Ebene 0 SB-Regal\",\"arguments\":{\"controller\":\"API\",\"action\":\"stackRequest\",\"barcode\":\"20065307\",\"username\":\"123456\",\"pickupPoint\":\"a01\"}}",
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.OK, "Magazinbestellung wurde erfolgreich hinzugefügt.")
                ),
                arrayOf("Single requestable copy with multiple pickup points",
                        itemRequest,
                        0,
                        null,
                        "{\"status\":true,\"message\":\"n\\/a\",\"PickupPoints\":[\"a01\",\"a13\"],\"arguments\":{\"controller\":\"API\",\"action\":\"pickup\",\"barcode\":\"20065307\",\"username\":\"123456\"}}",
                        null,
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.SELECTION_NEEDED).apply {
                            actionIdentifier = OpacApi.ReservationResult.ACTION_BRANCH
                            this.selection = listOf(
                                    mapOf("key" to "stackRequest\t20065307\ta01", "value" to "Zentralbibliothek Ebene 0 SB-Regal"),
                                    mapOf("key" to "stackRequest\t20065307\ta13", "value" to "Zentralbibliothek, Servicetheke")
                            )
                        }
                ),
                arrayOf("Single requestable copy with multiple pickup points including unknown ones",
                        itemRequest,
                        0,
                        null,
                        "{\"status\":true,\"message\":\"n\\/a\",\"PickupPoints\":[\"a01\",\"xxx\"],\"arguments\":{\"controller\":\"API\",\"action\":\"pickup\",\"barcode\":\"20065307\",\"username\":\"123456\"}}",
                        null,
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.SELECTION_NEEDED).apply {
                            actionIdentifier = OpacApi.ReservationResult.ACTION_BRANCH
                            this.selection = listOf(
                                    mapOf("key" to "stackRequest\t20065307\ta01", "value" to "Zentralbibliothek Ebene 0 SB-Regal"),
                                    mapOf("key" to "stackRequest\t20065307\txxx", "value" to "xxx")
                            )
                        }
                ),
                arrayOf("Make stack request for selected pickup point",
                        itemRequest,
                        OpacApi.ReservationResult.ACTION_BRANCH,
                        "stackRequest\t20065307\ta01",
                        null,
                        "{\"status\":true,\"message\":\"Magazinbestellung wurde erfolgreich hinzugefügt.\",\"requestID\":\"2116982\",\"pickupPoint\":\"Zentralbibliothek Ebene 0 SB-Regal\",\"arguments\":{\"controller\":\"API\",\"action\":\"stackRequest\",\"barcode\":\"20065307\",\"username\":\"123456\",\"pickupPoint\":\"a01\"}}",
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.OK, "Magazinbestellung wurde erfolgreich hinzugefügt.")
                ),
                arrayOf("Multiple requestable or reservable copies",
                        itemRequestAndReserve,
                        0,
                        null,
                        null,
                        null,
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.SELECTION_NEEDED, "item_copy").apply {
                            actionIdentifier = OpacApi.ReservationResult.ACTION_USER + 1
                            this.selection = listOf(
                                    mapOf("key" to "reserve\t10059731", "value" to "Zentralbibliothek: Ausgeliehen, Vormerken möglich"),
                                    mapOf("key" to "stackRequest\t20065307", "value" to "Zentralbibliothek: Ausleihbar, bitte bestellen"))
                        }
                ),
                arrayOf("Selected reservable copy (with multiple pickup branches)",
                        itemRequestAndReserve,
                        OpacApi.ReservationResult.ACTION_USER + 1, // == ACTION_COPY
                        "reserve\t10059731",
                        null,
                        null,
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.SELECTION_NEEDED).apply {
                            actionIdentifier = OpacApi.ReservationResult.ACTION_BRANCH
                            this.selection = listOf(
                                    mapOf("key" to "reserve\t10059731\tzell1", "value" to "Zentralbibliothek"),
                                    mapOf("key" to "reserve\t10059731\tbebel1", "value" to "ZwB Erziehungswissenschaften"),
                                    mapOf("key" to "reserve\t10059731\tberg1", "value" to "ZwB Rechtswissenschaft"),
                                    mapOf("key" to "reserve\t10059731\tfied1", "value" to "ZwB Medizin"),
                                    mapOf("key" to "reserve\t10059731\ttha1", "value" to "ZwB Forstwissenschaft"),
                                    mapOf("key" to "reserve\t10059731\tzell9", "value" to "Bereichsbibliothek Drepunct")
                            )
                        }
                ),
                arrayOf("Selected requestable copy with single pickup point",
                        itemRequestAndReserve,
                        OpacApi.ReservationResult.ACTION_USER + 1,  // == ACTION_COPY
                        "stackRequest\t20065307",
                        "{\"status\":true,\"message\":\"n\\/a\",\"PickupPoints\":[\"a01\"],\"arguments\":{\"controller\":\"API\",\"action\":\"pickup\",\"barcode\":\"20065307\",\"username\":\"123456\"}}",
                        "{\"status\":true,\"message\":\"Magazinbestellung wurde erfolgreich hinzugefügt.\",\"requestID\":\"2116982\",\"pickupPoint\":\"Zentralbibliothek Ebene 0 SB-Regal\",\"arguments\":{\"controller\":\"API\",\"action\":\"stackRequest\",\"barcode\":\"20065307\",\"username\":\"123456\",\"pickupPoint\":\"a01\"}}",
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.OK, "Magazinbestellung wurde erfolgreich hinzugefügt.")
                ),
                // "selected requestable copy with multiple pickup points" doesn't need to be tested as it's the same process as "Selected reservable copy (with multiple pickup branches)"
                arrayOf("No requestable or reservable copies",
                        itemNone,
                        0,
                        null,
                        null,
                        null,
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.ERROR, "no_copy_reservable")
                ),
                arrayOf("Error getting pickup points",
                        itemRequest,
                        0,
                        null,
                        "",
                        null,
                        OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.ERROR, "unknown_error_account_with_description accountRequest didn't return JSON object: A JSONObject text must begin with '{' at character 0")
                )
        )
    }
}

class SLUBAccountValidateMockTest : BaseHtmlTest() {
    private val slub = spy(SLUB::class.java)

    init {
        slub.init(Library().apply {
            data = JSONObject().apply {
                put("baseurl", "test")
                put("illrenewurl", "https://test-renew.de")
            }
        }, HttpClientFactory("test"), false)
    }

    private val account = Account().apply {
        name = "x"
        password = "x"
    }

    @Test
    fun testCheckAccountData() {
        val response = "{\"status\":\"1\",\"message\":\"credentials_are_valid\"}"
        doReturn(response).`when`(slub).httpPost(any(), any(), any())

        slub.checkAccountData(account)
        verify(slub).httpPost(any(), argThat(IsRequestBodyWithAction("validate")), any())
    }
}

@RunWith(Parameterized::class)
class SLUBSearchMockTest(@Suppress("unused") private val name: String,
                         private val query: List<SearchQuery>,
                         private val expectedQueryUrl: String?,
                         private val response: String?,
                         private val expectedResultCount: Int?,
                         private val expectedException: Class<out Exception?>?,
                         private val expectedExceptionMsg: String?) : BaseHtmlTest() {
    private val slub = spy(SLUB::class.java)

    init {
        slub.init(Library().apply {
            data = JSONObject().apply {
                put("baseurl", "https://test.de")
                put("illrenewurl", "https://test-renew.de")
            }
        }, HttpClientFactory("test"), false)
    }

    @Test
    fun testSearch() {
        doReturn(response).`when`(slub).httpGet(any(), any())
        if (expectedException != null) {
            val thrown = assertThrows(expectedExceptionMsg, expectedException
            ) { slub.search(query) }
            assertTrue(thrown!!.message!!.contains(expectedExceptionMsg!!))
        } else {
            val actual = slub.search(query)
            assertEquals(expectedResultCount, actual.total_result_count)
            verify(slub).httpGet(expectedQueryUrl, "UTF-8")
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
                arrayOf("Empty query",
                        emptyList<SearchQuery>(),
                        null,
                        null,
                        0,
                        OpacApi.OpacErrorException::class.java,
                        "no_criteria_input"
                ),
                arrayOf("Drop-down and text field",
                        listOf(
                                SearchQuery(TextSearchField().apply {
                                    id = "title"
                                    displayName = "Titel"
                                }, "Kotlin - Das umfassende Praxis-Handbuch"),
                                SearchQuery(DropdownSearchField().apply {
                                    id = "access_facet"
                                    displayName = "Zugang"
                                    dropdownValues = listOf(
                                            DropdownSearchField.Option("Local+Holdings", "physisch"),
                                            DropdownSearchField.Option("Electronic+Resources", "digital")
                                    )
                                }, "Electronic+Resources")
                        ),
                        "https://test.de/?type=1369315142&tx_find_find%5Bformat%5D=data&tx_find_find%5Bdata-format%5D=app&tx_find_find%5Bpage%5D=1&tx_find_find%5Bq%5D%5Btitle%5D=Kotlin - Das umfassende Praxis-Handbuch&tx_find_find%5Bfacet%5D%5Baccess_facet%5D%5BElectronic%2BResources%5D=1".replace(" ", "%20"),  // correct for  addEncodedQueryParameter
                        "{\"numFound\":1,\"start\" : 0,\"docs\" : [{\"id\":\"0-1688062912\",\"format\":[\"Book, E-Book\"],\"title\":\"Kotlin - Das umfassende Praxis-Handbuch Szwillus, Karl.\",\"author\":[\"Szwillus, Karl\"],\"creationDate\":\"2019\",\"imprint\":[\"[Erscheinungsort nicht ermittelbar]: mitp Verlag, 2019\"]}]}",
                        1,
                        null,
                        null
                )
        )
    }
}

class SLUBGetResultByIdMockTest : BaseHtmlTest() {
    private val slub = spy(SLUB::class.java)
    private val mockHeadResponse = Response.Builder()
            .request(Request.Builder()
                    .url("https://test.de/id/123/")
                    .build())
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("")
            .build()
    val mockGetResponse = """{"record":{"title":"The title"},"id":"123","thumbnail":"","links":[],"linksRelated":[],
                            |"linksAccess":[],"linksGeneral":[],"references":[],"copies":[],"parts":{}}""".trimMargin()

    init {
        slub.init(Library().apply {
            data = JSONObject().apply {
                put("baseurl", "https://test.de")
                put("illrenewurl", "https://test-renew.de")
            }
        }, HttpClientFactory("test"), false)
    }

    @Test
    fun testJSONError() {
        doReturn("xxx").`when`(slub).httpGet(any(), any())
        val thrown = assertThrows(null, OpacApi.OpacErrorException::class.java
        ) { slub.getResultById("id/0", null) }
        assertTrue(thrown!!.message!!.contains("search returned malformed JSON object:"))
    }

    @Test
    fun testIdIdentifier() {
        doReturn(mockGetResponse).`when`(slub).httpGet(any(), any())
        val actual = slub.getResultById("id/123", null)
        verify(slub).httpGet("https://test.de/id/123/?type=1369315142&tx_find_find%5Bformat%5D=data&tx_find_find%5Bdata-format%5D=app", "UTF-8")
        verify(slub, never()).httpHead(any(), anyBoolean())
        assertEquals("id/123", actual.id)
    }

    @Test
    fun testBcIdentifier() {
        doReturn(mockHeadResponse).`when`(slub).httpHead(any(), anyBoolean())
        doReturn(mockGetResponse).`when`(slub).httpGet(any(), any())
        val actual = slub.getResultById("bc/456", null)
        verify(slub).httpHead(eq("https://test.de/bc/456/"), eq(false))
        verify(slub).httpGet("https://test.de/id/123/?type=1369315142&tx_find_find%5Bformat%5D=data&tx_find_find%5Bdata-format%5D=app", "UTF-8")
        assertEquals("id/123", actual.id)
    }

    @Test
    fun testRsnIdentifier() {
        doReturn(mockHeadResponse).`when`(slub).httpHead(any(), anyBoolean())
        doReturn(mockGetResponse).`when`(slub).httpGet(any(), any())
        val actual = slub.getResultById("rsn/456", null)
        verify(slub).httpHead(eq("https://test.de/rsn/456/"), eq(false))
        verify(slub).httpGet("https://test.de/id/123/?type=1369315142&tx_find_find%5Bformat%5D=data&tx_find_find%5Bdata-format%5D=app", "UTF-8")
        assertEquals("id/123", actual.id)
    }

    @Test
    fun testLibeorIdentifier() {
        doReturn(mockHeadResponse).`when`(slub).httpHead(any(), anyBoolean())
        doReturn(mockGetResponse).`when`(slub).httpGet(any(), any())
        val actual = slub.getResultById("http://slubdd.de/katalog?libero_mab456", null)
        verify(slub).httpHead(eq("http://slubdd.de/katalog?libero_mab456"), eq(false))
        verify(slub).httpGet("https://test.de/id/123/?type=1369315142&tx_find_find%5Bformat%5D=data&tx_find_find%5Bdata-format%5D=app", "UTF-8")
        assertEquals("id/123", actual.id)
    }

    @Test
    fun testLegacyIdentifier() {
        // id without prefix, e.g. from old favorites list
        doReturn(mockGetResponse).`when`(slub).httpGet(any(), any())
        val actual = slub.getResultById("123", null)
        verify(slub).httpGet("https://test.de/id/123/?type=1369315142&tx_find_find%5Bformat%5D=data&tx_find_find%5Bdata-format%5D=app", "UTF-8")
        verify(slub, never()).httpHead(any(), anyBoolean())
        assertEquals("id/123", actual.id)
    }
}

@RunWith(Parameterized::class)
class SLUBProlongMockTest(@Suppress("unused") private val name: String,
                          private val media: String,
                          private val expectedQueryUrl: String?,
                          private val expectedRequestBody: RequestBody,
                          private val response: String?,
                          private val expectedResult: OpacApi.MultiStepResult,
                          private val expectedException: Class<out Exception?>?,
                          private val expectedExceptionMsg: String?) : BaseHtmlTest() {
    private val slub = spy(SLUB::class.java)

    init {
        slub.init(Library().apply {
            data = JSONObject().apply {
                put("baseurl", "https://test.de")
                put("illrenewurl", "https://test-renew.de")
            }
        }, HttpClientFactory("test"), false)
    }

    private val account = Account().apply {
        name = "123456"
        password = "x"
    }

    @Test
    fun testProlong() {
        doReturn(response).`when`(slub).httpPost(any(), any(), any())
        if (expectedException != null) {
            val thrown = assertThrows(expectedExceptionMsg, expectedException
            ) { slub.prolong(media, account, 0, null) }
            assertTrue(thrown!!.message!!.contains(expectedExceptionMsg!!))
        } else {
            val actualResult = slub.prolong(media, account, 0, null)
            assertThat(actualResult, sameBeanAs(expectedResult))
            verify(slub).httpPost(eq(expectedQueryUrl), argThat(sameBeanAs(expectedRequestBody)), eq("UTF-8"))
        }
    }

    companion object {
        private val illRenewResponse = BaseHtmlTest().readResource("/slub/account/ill-renew.html")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
                arrayOf("Regular item",
                        "B\t20148242",
                        "https://test.de/mein-konto/",
                        FormBody.Builder()
                                .add("type", "1")
                                .add("tx_slubaccount_account[controller]", "API")
                                .add("tx_slubaccount_account[action]", "renew")
                                .add("tx_slubaccount_account[username]", "123456")
                                .add("tx_slubaccount_account[password]", "x")
                                .add("tx_slubaccount_account[renewals][0]", "20148242")
                                .build(),
                        "{\"status\":\"1\",\"arguments\":{\"controller\":\"API\",\"action\":\"renew\",\"username\":\"123456\",\"renewals\":[\"20148242\"]}}",
                        OpacApi.ProlongResult(OpacApi.MultiStepResult.Status.OK),
                        null,
                        null
                ),
                arrayOf("Interlibrary loan item",
                        "FL\t12022302N",
                        "https://test-renew.de",
                        FormBody.Builder()
                                .add("bc", "12022302N")
                                .add("uid", "123456")
                                .add("clang", "DE")
                                .add("action", "send")
                                .build(),
                        illRenewResponse,
                        OpacApi.ProlongResult(OpacApi.MultiStepResult.Status.OK, "Ihr Verlängerungswunsch wurde gesendet."),
                        null,
                        null
                )
        )
    }
}

@RunWith(Parameterized::class)
class SLUBProlongMultipleMockTest(@Suppress("unused") private val name: String,
                                  private val media: List<String>,
                                  private val expectedRequestBody: RequestBody,
                                  private val response: String?,
                                  private val expectedResult: OpacApi.ProlongAllResult) : BaseHtmlTest() {
    private val slub = spy(SLUB::class.java)

    init {
        slub.init(Library().apply {
            data = JSONObject().apply {
                put("baseurl", "https://test.de")
                put("illrenewurl", "https://test-renew.de")
            }
        }, HttpClientFactory("test"), false)
    }

    private val account = Account().apply {
        name = "123456"
        password = "x"
    }

    @Test
    fun testProlong() {
        doReturn(response).`when`(slub).httpPost(any(), any(), any())
        val actualResult = slub.prolongMultiple(media, account, 0, null)
        assertThat(actualResult, sameBeanAs(expectedResult))
        verify(slub).httpPost(eq("https://test.de/mein-konto/"), argThat(sameBeanAs(expectedRequestBody)), eq("UTF-8"))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
                arrayOf("Local items",
                        listOf("B\t20148242", "B\t12345678"),
                        FormBody.Builder()
                                .add("type", "1")
                                .add("tx_slubaccount_account[controller]", "API")
                                .add("tx_slubaccount_account[action]", "renew")
                                .add("tx_slubaccount_account[username]", "123456")
                                .add("tx_slubaccount_account[password]", "x")
                                .add("tx_slubaccount_account[renewals][0]", "20148242")
                                .add("tx_slubaccount_account[renewals][1]", "12345678")
                                .build(),
                        """{"status":"1","arguments":{"controller":"API","action":"renew","username":"123456","renewals":["20148242","12345678"]}}""",
                        OpacApi.ProlongAllResult(OpacApi.MultiStepResult.Status.OK)
                ),
                arrayOf("Interlibrary loan and local items",
                        listOf("B\t20148242", "FL\t12022302N", "B\t12345678"),
                        FormBody.Builder()
                                .add("type", "1")
                                .add("tx_slubaccount_account[controller]", "API")
                                .add("tx_slubaccount_account[action]", "renew")
                                .add("tx_slubaccount_account[username]", "123456")
                                .add("tx_slubaccount_account[password]", "x")
                                .add("tx_slubaccount_account[renewals][0]", "20148242")
                                .add("tx_slubaccount_account[renewals][1]", "12345678")
                                .build(),
                        """{"status":"1","arguments":{"controller":"API","action":"renew","username":"123456","renewals":["20148242","12345678"]}}""",
                        OpacApi.ProlongAllResult(OpacApi.MultiStepResult.Status.OK, "renew_ill_separately")
                ),
                arrayOf("API renew error",
                        listOf("B\t20148242"),
                        FormBody.Builder()
                                .add("type", "1")
                                .add("tx_slubaccount_account[controller]", "API")
                                .add("tx_slubaccount_account[action]", "renew")
                                .add("tx_slubaccount_account[username]", "123456")
                                .add("tx_slubaccount_account[password]", "x")
                                .add("tx_slubaccount_account[renewals][0]", "20148242")
                                .build(),
                        """{"status":"1","arguments":{"controller":"API","action":"renew","username":"123456","renewals":["20148242","12345678"]},
                           "APIError":{"status":0,"message":"20148242: Verlängerung nicht möglich"}}""",
                        OpacApi.ProlongAllResult(OpacApi.MultiStepResult.Status.OK, "20148242: Verlängerung nicht möglich")
                )
        )
    }
}

@RunWith(Parameterized::class)
class SLUBCancelMockTest(@Suppress("unused") private val name: String,
                         private val media: String,
                         private val response: String?,
                         private val expectedResult: OpacApi.MultiStepResult) : BaseHtmlTest() {
    private val slub = spy(SLUB::class.java)

    init {
        slub.init(Library().apply {
            data = JSONObject().apply {
                put("baseurl", "https://test.de")
                put("illrenewurl", "https://test-renew.de")
            }
        }, HttpClientFactory("test"), false)
    }

    private val account = Account().apply {
        name = "123456"
        password = "x"
    }

    @Test
    fun testCancel() {
        val expectedRequestBody = FormBody.Builder()
                .add("type", "1")
                .add("tx_slubaccount_account[controller]", "API")
                .add("tx_slubaccount_account[action]", "delete")
                .add("tx_slubaccount_account[username]", "123456")
                .add("tx_slubaccount_account[password]", "x")
                .add("tx_slubaccount_account[delete][0]", media)
                .build()
        doReturn(response).`when`(slub).httpPost(any(), any(), any())
        val actualResult = slub.cancel(media, account, 0, null)
        assertThat(actualResult, sameBeanAs(expectedResult))
        verify(slub).httpPost(eq("https://test.de/mein-konto/"), argThat(sameBeanAs(expectedRequestBody)), eq("UTF-8"))
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
                arrayOf("OK",
                        "31481285_1",
                        "{\"status\":1,\"message\":\"Reservation Deleted\",\"arguments\":{\"controller\":\"API\",\"action\":\"delete\",\"username\":\"123456\",\"delete\":[\"31481285_1\"]}} ",
                        OpacApi.CancelResult(OpacApi.MultiStepResult.Status.OK)
                ),
                arrayOf("Error (item was not reserved)",
                        "31481285_1",
                        "{\"status\":\"-1\",\"message\":\"Item not reserved\",\"arguments\":{\"controller\":\"API\",\"action\":\"delete\",\"username\":\"123456\",\"delete\":[\"31481285_1\"]}}",
                        OpacApi.CancelResult(OpacApi.MultiStepResult.Status.ERROR, "unknown_error_account_with_description Item not reserved")
                )
        )
    }
}

class SLUBSearchFieldsMockTest : BaseHtmlTest() {
    private val slub = spy(SLUB::class.java)

    init {
        slub.init(Library().apply {
            data = JSONObject().apply {
                put("baseurl", "test")
                put("illrenewurl", "https://test-renew.de")
            }
        }, HttpClientFactory("test"), false)
    }

    @Test
    fun testParseSearchFields() {
        val html = readResource("/slub/SLUB Dresden Startseite.html")
        doReturn(html).`when`(slub).httpGet(any(), any())

        val searchFields = slub.parseSearchFields()
        assertEquals(10, searchFields.filterIsInstance(TextSearchField::class.java).size)
        assertEquals(1, searchFields.filterIsInstance(DropdownSearchField::class.java).size)
    }
}

class IsRequestBodyWithAction(private val action: String) : ArgumentMatcher<RequestBody> {
    override fun matches(arg: RequestBody?): Boolean {
        val fb = arg as FormBody?
        for (i in 0 until (fb?.size ?: 0)) {
            if (fb!!.value(i) == action)
                return true
        }
        return false
    }
}

class IsRequestBodyWithActionTest {
    val fb: FormBody = FormBody.Builder().add("name", "value").build()

    @Test
    fun `matcher matches`() = assertTrue(IsRequestBodyWithAction("value").matches(fb))

    @Test
    fun `matcher doesn't match`() = assertFalse(IsRequestBodyWithAction("").matches(fb))

    @Test
    fun `matcher doesn't match empty formbody`() = assertFalse(IsRequestBodyWithAction("").matches(FormBody.Builder().build()))
}
