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

import de.geeksfactory.opacclient.i18n.StringProvider
import de.geeksfactory.opacclient.networking.HttpClientFactory
import de.geeksfactory.opacclient.objects.*
import de.geeksfactory.opacclient.searchfields.DropdownSearchField
import de.geeksfactory.opacclient.searchfields.SearchField
import de.geeksfactory.opacclient.searchfields.SearchQuery
import de.geeksfactory.opacclient.searchfields.TextSearchField
import de.geeksfactory.opacclient.utils.*
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * OpacApi implementation for SLUB. https://slub-dresden.de
 *
 * @author Steffen Rehberg, Jan 2019
 */
open class SLUB : OkHttpBaseApi() {
    protected lateinit var baseurl: String
    protected lateinit var illRenewUrl: String
    private val ENCODING = "UTF-8"
    protected lateinit var query: List<SearchQuery>

    private val mediaTypes = mapOf(
            "Article, E-Article" to SearchResult.MediaType.EDOC,
            "Book, E-Book" to SearchResult.MediaType.BOOK,
            "Video" to SearchResult.MediaType.EVIDEO,
            "Thesis" to SearchResult.MediaType.BOOK,
            "Manuscript" to SearchResult.MediaType.BOOK,
            "Musical Score" to SearchResult.MediaType.SCORE_MUSIC,
            "Website" to SearchResult.MediaType.URL,
            "Journal, E-Journal" to SearchResult.MediaType.NEWSPAPER,
            "Map" to SearchResult.MediaType.MAP,
            "Audio" to SearchResult.MediaType.EAUDIO,
            "Electronic Resource (Data Carrier)" to SearchResult.MediaType.EAUDIO,
            "Image" to SearchResult.MediaType.ART,
            "Microform" to SearchResult.MediaType.MICROFORM,
            "Visual Media" to SearchResult.MediaType.ART,
            "Electronic Resource" to SearchResult.MediaType.CD_SOFTWARE, // typically CD-ROM or DVD-ROM
            "Physical Object" to SearchResult.MediaType.DEVICE,
            "Norm" to SearchResult.MediaType.EDOC
    )

    private val fieldCaptions = mapOf(
            "format" to "Medientyp",
            "title" to "Titel",
            "contributor" to "Beteiligte",
            "publisher" to "Erschienen",
            "ispartof" to "Erschienen in",
            "identifier" to "ISBN",
            "language" to "Sprache",
            "subject" to "Schlagwörter",
            "description" to "Beschreibung",
            "linksRelated" to "Info",
            "linksAccess" to "Zugang",
            "linksGeneral" to "Link"
    )

    private val ACTION_COPY = OpacApi.MultiStepResult.ACTION_USER + 1
    private val pickupPoints = mapOf(
            "a01" to "Zentralbibliothek Ebene 0 SB-Regal",
            "a02" to "Zentralbibliothek, Ebene -1, IP Musik Mediathek",
            "a03" to "Zentralbibliothek, Ebene -1, Lesesaal Sondersammlungen",
            "a05" to "Zentralbibliothek, Ebene -2, Lesesaal Kartensammlung",
            "a06" to "ZwB Rechtswissenschaft",
            "a07" to "ZwB Erziehungswissenschaft",
            "a08" to "ZwB Medizin",
            "a09" to "ZwB Forstwissenschaft",
            "a10" to "Bereichsbibliothek Drepunct",
            "a13" to "Zentralbibliothek, Servicetheke",
            "a14" to "Zentralbibliothek, Ebene -1, SB-Regal Zeitungen"
    )

    private val pickupLocations = mapOf(
            "zell1" to "Zentralbibliothek",
            "bebel1" to "ZwB Erziehungswissenschaften",
            "berg1" to "ZwB Rechtswissenschaft",
            "fied1" to "ZwB Medizin",
            "tha1" to "ZwB Forstwissenschaft",
            "zell9" to "Bereichsbibliothek Drepunct"
    )

    override fun init(library: Library, factory: HttpClientFactory, debug: Boolean) {
        super.init(library, factory, debug)
        baseurl = library.data.getString("baseurl")
        illRenewUrl = library.data.getString("illrenewurl")
    }

    override fun search(query: List<SearchQuery>): SearchRequestResult {
        this.query = query
        return searchGetPage(1)
    }

    override fun searchGetPage(page: Int): SearchRequestResult {
        val queryUrlB = "$baseurl/?type=1369315142&tx_find_find%5Bformat%5D=data&tx_find_find%5Bdata-format%5D=app".toHttpUrl()
                .newBuilder()
                .addQueryParameter("tx_find_find[page]", page.toString())
        for (sq in query) {
            if (sq.value.isNotEmpty()) {
                if (sq.searchField is DropdownSearchField) {  // access_facet
                    queryUrlB.addQueryParameter("tx_find_find[facet][access_facet][${sq.value}]", "1")
                } else {
                    queryUrlB.addQueryParameter("tx_find_find[q][${sq.key}]", sq.value)
                }
            }
        }
        val queryUrl = queryUrlB.build()
        if (queryUrl.querySize <= 4) {
            throw OpacApi.OpacErrorException(stringProvider.getString(StringProvider.NO_CRITERIA_INPUT))
        }
        try {
            return parseSearchResults(JSONObject(httpGet(queryUrl.toString(), ENCODING)))
        } catch (e: JSONException) {
            throw OpacApi.OpacErrorException(stringProvider.getFormattedString(
                    StringProvider.UNKNOWN_ERROR_WITH_DESCRIPTION,
                    "search returned malformed JSON object: ${e.message}"))
        }
    }

    internal fun parseSearchResults(json: JSONObject): SearchRequestResult {
        val searchresults = json.getJSONArray("docs").map<JSONObject, SearchResult> {
            SearchResult().apply {
                innerhtml = "<b>${it.getString("title")}</b><br>${it.getJSONArray("author").optString(0)}"
                it.getString("creationDate").run {
                    if (this != "null") {
                        innerhtml += "<br>(${this})"
                    }
                }
                type = mediaTypes[it.getJSONArray("format").optString(0)]
                        ?: SearchResult.MediaType.NONE
                id = "id/${it.getString("id")}"
            }
        }
        //TODO: get status (one request per item!)
        return SearchRequestResult(searchresults, json.getInt("numFound"), 1)
    }

    override fun filterResults(filter: Filter, option: Filter.Option): SearchRequestResult {
        TODO("not implemented")
    }

    override fun getResultById(id: String, homebranch: String?): DetailedItem {
        val json: JSONObject
        val url = when {
            id.startsWith("id/") ->
                "$baseurl/$id/"
            id.startsWith("bc/") || id.startsWith("rsn/") ->
                httpHead("$baseurl/$id/", false).request.url.toString()
            id.startsWith("http://slubdd.de/katalog?libero_mab") ->
                httpHead(id, false).request.url.toString()
            else -> // legacy case: id identifier without prefix
                "$baseurl/id/$id/"
        } + "?type=1369315142&tx_find_find%5Bformat%5D=data&tx_find_find%5Bdata-format%5D=app"
        try {
            json = JSONObject(httpGet(url, ENCODING))
        } catch (e: JSONException) {
            throw OpacApi.OpacErrorException(stringProvider.getFormattedString(
                    StringProvider.UNKNOWN_ERROR_WITH_DESCRIPTION,
                    "search returned malformed JSON object: ${e.message}"))
        }
        return parseResultById(json)
    }

    internal fun parseResultById(json: JSONObject): DetailedItem {
        val colorcodes = mapOf("1" to SearchResult.Status.GREEN, "2" to SearchResult.Status.YELLOW, "3" to SearchResult.Status.RED)
        val dateFormat = DateTimeFormat.forPattern("dd.MM.yyyy")
        var hasReservableCopies = false
        fun getCopies(copiesArray: JSONArray, df: DateTimeFormatter): List<Copy> =
                copiesArray.map<JSONObject, Copy> {
                    Copy().apply {
                        barcode = it.getString("barcode")
                        branch = it.getString("location")
                        department = Parser.unescapeEntities(it.getString("sublocation"), false)
                        shelfmark = it.getString("shelfmark")
                        it.getString("statusphrase").run {
                            if (isNotEmpty()) {
                                status = Parser.unescapeEntities(this, false)
                            } else {
                                it.getString("link").run {
                                    if(isNotEmpty()){
                                        status = stringProvider.getFormattedString(
                                                StringProvider.INQUIRE_AVAILABILITY, this )
                                    }
                                }
                            }
                        }
                        statusCode = colorcodes.getOrElse(it.getString("colorcode")) { SearchResult.Status.UNKNOWN }
                        it.getString("duedate").run {
                            if (isNotEmpty()) {
                                returnDate = df.parseLocalDate(this)
                            }
                        }
                        // stack requests and reservations for items on loan are both handled as "reservations" in libopac
                        if (it.getString("bestellen") == "1") {
                            resInfo = "stackRequest\t$barcode"
                            hasReservableCopies = true
                        }
                        if (it.getString("vormerken") == "1") {
                            resInfo = "reserve\t$barcode"
                            hasReservableCopies = true
                        }
                        // reservations: only available for reserved copies, not for reservable copies
                        // url: not for accessible online resources, only for lendable online copies
                    }
                }
        return DetailedItem().apply {
            this.id = "id/${json.getString("id")}"
            val record = json.getJSONObject("record")
            for (key in record.keys()) {
                var value = when (val v = record.get(key as String)) {
                    is String -> v
                    is Int -> v.toString()
                    is JSONArray -> 0.until(v.length()).map {
                        when (val arrayItem = v.get(it)) {
                            is String -> arrayItem
                            is JSONObject -> arrayItem.optString("title").also {
                                // if item is part of multiple collections, collectionsId holds the last one
                                arrayItem.optString("id", null)?.let {
                                    collectionId = "id/$it"
                                }
                            }
                            else -> null
                        }
                    }.joinToString("; ")
                    else -> ""
                }
                if (value.isNotEmpty()) {
                    value = Parser.unescapeEntities(value, false)
                    if (key == "title") {
                        title = value
                    }
                    addDetail(Detail(fieldCaptions[key], value))
                }
            }
            json.getString("thumbnail")?.run {
                if (this != "") {
                    cover = this
                }
            }
            // links
            for (link in listOf("linksRelated", "linksAccess", "linksGeneral")) {
                json.getJSONArray(link).forEach<JSONObject> {
                    // assuming that only on of material, note or hostlabel is set
                    val key = with(it.optString("material") + it.optString("note") + it.optString("hostLabel")) {
                        if (isEmpty()) fieldCaptions[link] else this
                    }
                    if (key == "Cover") {
                        it.getString("uri").run {
                            cover = if (startsWith("http://swbplus.bsz-bw.de/") && endsWith("cov.htm")) {
                                replace(".htm", ".jpg")
                            } else {
                                this
                            }
                        }
                    } else {
                        addDetail(Detail(key, it.getString("uri")))
                    }
                }
            }
            // copies
            val cps = json.get("copies")
            if (cps is JSONArray) {
                getCopies(cps, dateFormat).let { copies = it }
            } else {  // multiple arrays
                val copiesList = mutableListOf<Copy>()
                for (key in (cps as JSONObject).keys()) {
                    val cpsi = cps.get(key as String)
                    if (cpsi is JSONArray) {
                        copiesList.addAll(getCopies(cpsi, dateFormat))
                    }
                }
                copies = copiesList
            }
            isReservable = hasReservableCopies
            // volumes
            json.getJSONObject("parts").optJSONArray("records")?.forEach<JSONObject> {
                volumes.add(Volume(it.optString("id"),
                        "${it.optString("part")} ${Parser.unescapeEntities(it.optString("name"), false)}"))
            }
            // references
            json.getJSONArray("references").forEach<JSONObject> {
                val link = it.optString("link")
                if (link.startsWith("http://slubdd.de/katalog?libero_mab")){
                    addVolume(Volume(link, "${it.optString("text")}:\n${it.optString("name")}"))
                } else {
                    addDetail(Detail(it.optString("text"), "${it.optString("name")} (${it.optString("target")})"))
                }
            }
        }
    }

    override fun getResult(position: Int): DetailedItem? {
        // not used (getResultById is implemented and every search result has an id set)
        return null
    }

    override fun reservation(item: DetailedItem, account: Account, useraction: Int, selection: String?): OpacApi.ReservationResult {
        var action = useraction
        var selected = selection ?: ""
        // step 1: select copy to request/reserve if there are multiple requestable/reservable copies
        //         if there's just one requestable/reservable copy then go to step 2
        if (action == 0 && selection == null) {
            val reservableCopies = item.copies.filter { it.resInfo != null }
            when (reservableCopies.size) {
                0 -> return OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.ERROR,
                        stringProvider.getString(StringProvider.NO_COPY_RESERVABLE))
                1 -> {
                    action = ACTION_COPY
                    selected = reservableCopies.first().resInfo
                }
                else -> {
                    val options = reservableCopies.map { copy ->
                        mapOf("key" to copy.resInfo,
                                "value" to "${copy.branch}: ${copy.status}")
                    }
                    return OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.SELECTION_NEEDED,
                            stringProvider.getString(StringProvider.ITEM_COPY)).apply {
                        actionIdentifier = ACTION_COPY
                        this.selection = options
                    }
                }
            }
        }
        // step 2: select pickup branch (reservations) or pickup point (stack requests)
        //         if there's just one pickup point then go to step 3
        if (action == ACTION_COPY) {
            val pickupLocations: Map<String, String>
            if (selected.startsWith("reserve")) {
                pickupLocations = this.pickupLocations.toMap()
            } else {
                val data = selected.split('\t')
                if (data.size != 2) {
                    OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.ERROR,
                            stringProvider.getString(StringProvider.INTERNAL_ERROR))
                }
                try {
                    val json = requestAccount(account, "pickup", mapOf("tx_slubaccount_account[barcode]" to data[1]))
                    pickupLocations = json.optJSONArray("PickupPoints")?.run {
                        0.until(length()).map { optString(it) }.associateWith {
                            pickupPoints.getOrElse(it) { it }
                        }
                    } ?: emptyMap()
                } catch (e: OpacApi.OpacErrorException) {
                    return OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.ERROR, e.message)
                }
            }
            when (pickupLocations.size) {
                0 -> return OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.ERROR,
                        stringProvider.getString(StringProvider.INTERNAL_ERROR))
                1 -> {
                    action = OpacApi.ReservationResult.ACTION_BRANCH
                    selected += "\t${pickupLocations.keys.first()}"
                }
                else -> return OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.SELECTION_NEEDED).apply {
                    actionIdentifier = OpacApi.ReservationResult.ACTION_BRANCH
                    this.selection = pickupLocations.map {
                        mapOf("key" to "$selected\t${it.key}",
                                "value" to it.value)
                    }
                }
            }
        }
        // step 3. make stack request or reservation
        if (action == OpacApi.ReservationResult.ACTION_BRANCH) {
            val data = selected.split('\t')
            if (data.size == 3) {
                val pickupParameter = if (data[0] == "stackRequest") "pickupPoint" else "PickupBranch"
                return try {
                    val json = requestAccount(account, data[0],
                            mapOf("tx_slubaccount_account[barcode]" to data[1],
                                    "tx_slubaccount_account[$pickupParameter]" to data[2]))
                    OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.OK, json.optString("message"))
                } catch (e: OpacApi.OpacErrorException) {
                    OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.ERROR, e.message)
                }
            }
        }
        return OpacApi.ReservationResult(OpacApi.MultiStepResult.Status.ERROR,
                stringProvider.getString(StringProvider.INTERNAL_ERROR))
    }

    override fun prolong(media: String, account: Account, useraction: Int, selection: String?): OpacApi.ProlongResult {
        val data = media.split('\t')
        if (data.size != 2) {
            OpacApi.ProlongResult(OpacApi.MultiStepResult.Status.ERROR, "internal error")
        }
        return try {
            if (data[0] == "FL") {   // inter-library loan (FL = Fernleihe) renewal request
                val formBody = FormBody.Builder()
                        .add("bc", data[1])
                        .add("uid", account.name)
                        .add("clang", "DE")
                        .add("action", "send")
                        .build()
                val result = httpPost(illRenewUrl, formBody, ENCODING).html
                OpacApi.ProlongResult(OpacApi.MultiStepResult.Status.OK, result.select("p:last-of-type").text())
            } else {                // regular, i.e. SLUB item renewal
                requestAccount(account, "renew", mapOf("tx_slubaccount_account[renewals][0]" to data[1]))
                OpacApi.ProlongResult(OpacApi.MultiStepResult.Status.OK)
            }
        } catch (e: Exception) {
            OpacApi.ProlongResult(OpacApi.MultiStepResult.Status.ERROR, e.message)
        }
    }

    override fun prolongAll(account: Account, useraction: Int, selection: String?): OpacApi.ProlongAllResult {
        return OpacApi.ProlongAllResult(OpacApi.MultiStepResult.Status.UNSUPPORTED)
    }

    override fun prolongMultiple(media: List<String>,
                                 account: Account, useraction: Int, selection: String?): OpacApi.ProlongAllResult {
        val (illMedia, localMedia) = media.partition { it.startsWith("FL") }
        val messages = mutableListOf<String>()
        var hasError = false
        if (illMedia.isNotEmpty()) {
            messages.add(StringProvider.RENEW_ILL_SEPARATELY)
        }
        if (localMedia.isNotEmpty()) {
            try {
                val parameters = localMedia.mapIndexed { i, e -> "tx_slubaccount_account[renewals][$i]" to e.split('\t')[1] }.toMap()
                val result = requestAccount(account, "renew", parameters)
                result.optJSONObject("APIError")?.getString("message")?.let {
                    messages.add(it)
                }
            } catch (e: java.lang.Exception) {
                messages.add(e.message.toString())
                hasError = true
            }
        }
        return OpacApi.ProlongAllResult(if (hasError) OpacApi.MultiStepResult.Status.ERROR else OpacApi.MultiStepResult.Status.OK,
                if (messages.isNotEmpty()) messages.joinToString("\n") else null
        )
    }

    override fun cancel(media: String, account: Account, useraction: Int, selection: String?): OpacApi.CancelResult {
        return try {
            requestAccount(account, "delete", mapOf("tx_slubaccount_account[delete][0]" to media))
            OpacApi.CancelResult(OpacApi.MultiStepResult.Status.OK)
        } catch (e: Exception) {
            OpacApi.CancelResult(OpacApi.MultiStepResult.Status.ERROR, e.message)
        }
    }

    override fun account(account: Account): AccountData {
        val json = requestAccount(account, "account")
        return parseAccountData(account, json)
    }

    internal fun parseAccountData(account: Account, json: JSONObject): AccountData {
        val fmt = DateTimeFormat.shortDate()
        fun getReservations(items: JSONObject?): MutableList<ReservedItem> {
            val types = listOf("hold", "request_ready", "readingroom", "request_progress", "reserve")
            // "requests" is a copy of "request_ready" + "readingroom" + "request_progress"
            val reservationsList = mutableListOf<ReservedItem>()
            for (type in types) {
                items?.optJSONArray(type)?.forEach<JSONObject> {
                    reservationsList.add(ReservedItem().apply {
                        title = it.optString("about").replace("¬", "")
                        author = it.optJSONArray("X_author")?.optString(0)
                        format = it.optString("X_medientyp")
                        if (format != "FL"){
                            id = "bc/${it.optString("label")}"
                        }
                        status = when (type) {  // TODO: maybe we need time (LocalDateTime) too make an educated guess on actual ready date for stack requests
                            "hold" -> stringProvider.getFormattedString(StringProvider.HOLD,
                                    fmt.print(LocalDate(it.optString("X_date_reserved").substring(0, 10))))
                            "request_ready" -> stringProvider.getFormattedString(StringProvider.REQUEST_READY,
                                    fmt.print(LocalDate(it.optString("X_date_requested").substring(0, 10))))
                            "readingroom" -> stringProvider.getFormattedString(StringProvider.READINGROOM,
                                    fmt.print(LocalDate(it.optString("X_date_provided").substring(0, 10))))
                            "request_progress" -> stringProvider.getFormattedString(StringProvider.REQUEST_PROGRESS,
                                    fmt.print(LocalDate(it.optString("X_date_requested").substring(0, 10))))
                            "reserve" -> stringProvider.getFormattedString(StringProvider.RESERVED_POS,
                                    it.optInt("X_queue_number"))
                            else -> null
                        }
                        branch = it.optString("X_pickup_desc", null)
                        if (type == "reserve") {
                            cancelData = "${it.optString("label")}_${it.getInt("X_delete_number")}"
                        }
                    })
                }
            }
            items?.optJSONArray("ill")?.forEach<JSONObject> {
                if (it.getString("Status") !in listOf("6", "11", "13", "16")){
                    reservationsList.add(ReservedItem().apply {
                        title = it.optString("Titel").replace("¬", "")
                        author = it.optString("Autor")
                        //id = it.optString("Fernleih_ID") --> this id is of no use whatsoever
                        it.optString("Medientyp").run {
                            format = if (length > 0 && this != "*") this else "FL"
                        }
                        branch = it.optString("Zweigstelle").let {
                            pickupLocations.getOrElse(it) { it }
                        }
                        status = it.optString("Status_DESC")
                    })
                }
            }

            return reservationsList
        }

        return AccountData(account.id).apply {
            pendingFees = json.getJSONObject("fees").getString("topay_list")
            validUntil = json.getJSONObject("memberInfo").getString("expires")
                    ?.substring(0, 10)?.let { fmt.print(LocalDate(it)) }
            lent = json.optJSONObject("items")?.optJSONArray("loan")    // TODO: plus permanent loans? (need example)
                    ?.map<JSONObject, LentItem> {
                        LentItem().apply {
                            title = it.optString("about").replace("¬", "")
                            author = it.optJSONArray("X_author")?.optString(0)
                            setDeadline(it.optString("X_date_due"))
                            format = it.optString("X_medientyp")
                            if (format != "FL") {
                                id = "bc/${it.optString("label")}"
                            }
                            barcode = it.optString("X_barcode")
                            if (it.optInt("X_is_renewable") == 1) {
                                isRenewable = true
                                prolongData = "$format\t$barcode"
                            } else {
                                isRenewable = false
                                status = when {
                                    it.optInt("X_is_flrenewable") == 1 -> stringProvider.getString(StringProvider.NOT_YET_RENEWABLE)
                                    it.optInt("X_is_reserved") != 0 -> stringProvider.getString(StringProvider.RESERVED)
                                    it.optInt("renewals") > 0 -> stringProvider.getFormattedString(
                                            StringProvider.RENEWED, it.optInt("renewals"))
                                    else -> null
                                }
                            }
                        }
                    } ?: emptyList()
            reservations = getReservations(json.optJSONObject("items"))
        }
    }

    internal fun requestAccount(account: Account, action: String, parameters: Map<String, String>? = null): JSONObject {
        val formBody = FormBody.Builder()
                .add("type", "1")
                .add("tx_slubaccount_account[controller]", "API")
                .add("tx_slubaccount_account[action]", action)
                .add("tx_slubaccount_account[username]", account.name)
                .add("tx_slubaccount_account[password]", account.password)
        parameters?.forEach { formBody.add(it.key, it.value) }
        try {
            return JSONObject(httpPost("$baseurl/mein-konto/", formBody.build(), ENCODING)).also {
                if (!(it.optInt("status") == 1 || it.optBoolean("status"))) {
                    throw OpacApi.OpacErrorException(stringProvider.getFormattedString(
                            StringProvider.UNKNOWN_ERROR_ACCOUNT_WITH_DESCRIPTION,
                            it.optString("message", "error requesting account data")))
                }
            }
        } catch (e: JSONException) {
            throw OpacApi.OpacErrorException(stringProvider.getFormattedString(
                    StringProvider.UNKNOWN_ERROR_ACCOUNT_WITH_DESCRIPTION,
                    "accountRequest didn't return JSON object: ${e.message}"))
        }
    }

    override fun checkAccountData(account: Account) {
        requestAccount(account, "validate")
    }

    override fun getShareUrl(id: String?, title: String?): String {
        return "$baseurl/$id"
    }

    override fun getSupportFlags(): Int {
        return OpacApi.SUPPORT_FLAG_ENDLESS_SCROLLING or OpacApi.SUPPORT_FLAG_ACCOUNT_PROLONG_MULTIPLE
    }

    override fun getSupportedLanguages(): Set<String>? {
        //TODO("not implemented")
        return null
    }

    override fun parseSearchFields(): List<SearchField> {
        val doc = httpGet(baseurl, ENCODING).html
        return doc.select("ul#search-in-field-recreated-list li").map {
            TextSearchField().apply {
                id = it["id"]
                displayName = it.text
            }
        } + listOf(DropdownSearchField().apply {
            id = "access_facet"
            displayName = "Zugang"
            dropdownValues = listOf(
                    DropdownSearchField.Option("", ""),
                    DropdownSearchField.Option("Local Holdings", "physisch"),
                    DropdownSearchField.Option("Electronic Resources", "digital")
            )
        })
    }

    override fun setLanguage(language: String?) {
        //TODO("not implemented")
    }

    override fun cleanUrl(url: String): String {
        return url
    }
}
