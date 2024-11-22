package de.geeksfactory.opacclient.apis;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.geeksfactory.opacclient.i18n.StringProvider;
import de.geeksfactory.opacclient.networking.HttpClientFactory;
import de.geeksfactory.opacclient.networking.NotReachableException;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.objects.AccountData;
import de.geeksfactory.opacclient.objects.Copy;
import de.geeksfactory.opacclient.objects.Detail;
import de.geeksfactory.opacclient.objects.DetailedItem;
import de.geeksfactory.opacclient.objects.Filter;
import de.geeksfactory.opacclient.objects.LentItem;
import de.geeksfactory.opacclient.objects.Library;
import de.geeksfactory.opacclient.objects.ReservedItem;
import de.geeksfactory.opacclient.objects.SearchRequestResult;
import de.geeksfactory.opacclient.objects.SearchResult;
import de.geeksfactory.opacclient.objects.Volume;
import de.geeksfactory.opacclient.searchfields.DropdownSearchField;
import de.geeksfactory.opacclient.searchfields.SearchField;
import de.geeksfactory.opacclient.searchfields.SearchQuery;
import de.geeksfactory.opacclient.searchfields.TextSearchField;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Function;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Response;

/**
 * OpacApi implementation for hte open source VuFind discovery system (https://vufind.org/),
 * developed by Villanova University.
 *
 * It includes special cases to account for changes in the "smartBib" variant
 * (https://www.subkom.de/smartbib/) developed by subkom GmbH. Account features were only tested
 * with smartBib.
 */
public class VuFind extends OkHttpBaseApi {
    protected static final Pattern idPattern = Pattern.compile("\\/(?:Opacrl)?Record\\/([^/]+)");
    protected static final Pattern buildingCollectionPattern = Pattern.compile("~(?:building|collection):\"([^\"]+)\"");
    protected static final Pattern bibliothecaIdPattern = Pattern.compile("&detmediennr=([^&]*)(?:&detDB=[^&]*)");

    public static final String OPTION_ALL = "_ALL";
    protected static HashMap<String, String> languageCodes = new HashMap<>();
    protected static HashMap<String, SearchResult.MediaType> mediaTypeSelectors = new HashMap<>();

    protected String res_branch;
    protected String res_url;
    protected String res_hashCode;

    static {
        languageCodes.put("de", "de");
        languageCodes.put("en", "en");
        languageCodes.put("el", "el");
        languageCodes.put("es", "es");
        languageCodes.put("it", "it");
        languageCodes.put("fr", "fr");
        languageCodes.put("da", "da");

        mediaTypeSelectors
                .put(".cd, .audio, .musicrecording, .record, .soundrecordingmedium, " +
                                ".soundrecording",
                        SearchResult.MediaType.CD_MUSIC);
        mediaTypeSelectors.put(".audiotape, .cassette, .soundcassette, .kassette",
                SearchResult.MediaType.AUDIO_CASSETTE);
        mediaTypeSelectors.put(".dvdaudio, .sounddisc", SearchResult.MediaType.CD_MUSIC);
        mediaTypeSelectors.put(".dvd, .dvdvideo", SearchResult.MediaType.DVD);
        mediaTypeSelectors.put(".blueraydisc, .bluraydisc", SearchResult.MediaType.BLURAY);
        mediaTypeSelectors.put(".ebook", SearchResult.MediaType.EBOOK);
        mediaTypeSelectors.put(".map, .globe, .atlas", SearchResult.MediaType.MAP);
        mediaTypeSelectors
                .put(".slide, .photo, .artprint, .collage, .drawing, .flashcard, .painting, " +
                                ".photonegative, .placard, .print, .sensorimage, .transparency",
                        SearchResult.MediaType.ART);
        mediaTypeSelectors.put(
                ".microfilm, .video, .videodisc, .vhs, .video, .videotape, .videocassette, " +
                        ".videocartridge, .audiovisualmedia, .filmstrip, .motionpicture, " +
                        ".videoreel, .dvdbluray",
                SearchResult.MediaType.MOVIE);
        mediaTypeSelectors.put(".kit, .sets", SearchResult.MediaType.PACKAGE);
        mediaTypeSelectors.put(".musicalscore, .notatedmusic, .electronicmusicalscore",
                SearchResult.MediaType.SCORE_MUSIC);
        mediaTypeSelectors.put(".manuscript, .book, .articles, .sachbuch, .kinderjugendbuch, " +
                ".roman, .bestseller, .comics", SearchResult.MediaType.BOOK);
        mediaTypeSelectors.put(".journal, .journalnewspaper, .serial, " +
                ".zeitschrift", SearchResult.MediaType.MAGAZINE);
        mediaTypeSelectors.put(".newspaper, .newspaperarticle", SearchResult.MediaType.NEWSPAPER);
        mediaTypeSelectors.put(".software, .cdrom, .chipcartridge, .disccartridge, .dvdrom",
                SearchResult.MediaType.CD_SOFTWARE);
        mediaTypeSelectors
                .put(".electronicnewspaper, .electronic, .electronicarticle, " +
                        "electronicresourcedatacarrier, .electronicresourceremoteaccess, " +
                        ".electronicserial, .electronicjournal, .electronicthesis, " +
                        ".edokument", SearchResult.MediaType.EDOC);
        mediaTypeSelectors.put(".unknown, .sonstiges, .tonies", SearchResult.MediaType.UNKNOWN);
        mediaTypeSelectors.put(".medienpaket", SearchResult.MediaType.PACKAGE);
        mediaTypeSelectors.put(".eaudio", SearchResult.MediaType.EAUDIO);
        mediaTypeSelectors.put(".konsolenspiel", SearchResult.MediaType.GAME_CONSOLE);
        mediaTypeSelectors.put(".karte", SearchResult.MediaType.MAP);
        mediaTypeSelectors.put(".spiel", SearchResult.MediaType.BOARDGAME);
    }

    protected String languageCode = "en";
    protected String opac_url = "";
    protected JSONObject data;
    protected List<SearchQuery> last_query;

    @Override
    public void init(Library lib, HttpClientFactory httpClientFactory, boolean debug) {
        super.init(lib, httpClientFactory, debug);

        this.library = lib;
        this.data = lib.getData();

        try {
            this.opac_url = data.getString("baseurl");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private String getHiddenFilters(boolean string) {
        // SmartBib multilibrary catalog with VuFind 5: needs to pass library name to every URL
        String library = data.optString("library_long");
        boolean hiddenfilters = data.optBoolean("hiddenfilters");
        if (library != null && hiddenfilters) {
            if (string) {
                try {
                    return URLEncoder.encode("hiddenFilters[]", "UTF-8") + "=" +
                            URLEncoder.encode("collection:" + library, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return "";
                }
            } else {
                return "collection:" + library;
            }
        } else {
            if (string) {
                return "";
            } else {
                return null;
            }
        }
    }

    protected HttpUrl.Builder buildSearchParams(List<SearchQuery> query, HttpUrl.Builder builder) {
        builder.addQueryParameter("sort", "relevance");
        builder.addQueryParameter("join", "AND");

        for (SearchQuery singleQuery : query) {
            if (singleQuery.getKey().contains("filter[]")) {
                String type = singleQuery.getKey().replace("filter[]", "");
                if (data.has("library") && singleQuery.getValue().equals(OPTION_ALL)) {
                    if (type.equals("limit_collection")) {
                        // add all values of search field to filter by books in the library
                        DropdownSearchField sf = (DropdownSearchField) singleQuery.getSearchField();
                        for (DropdownSearchField.Option option : sf.getDropdownValues()) {
                            if (!option.getKey().equals(OPTION_ALL)) {
                                builder.addQueryParameter("filter[]", option.getKey());
                            }
                        }
                    } else if (!type.equals("limit_building")) {
                        // don't add any options for branches (if not a specific branch was
                        // selected)
                        builder.addQueryParameter("filter[]", singleQuery.getValue());
                    }
                } else if (!singleQuery.getValue().equals("")) {
                    builder.addQueryParameter("filter[]", singleQuery.getValue());
                }
            } else {
                if (singleQuery.getValue().equals("")) continue;
                builder.addQueryParameter("type0[]", singleQuery.getKey());
                builder.addQueryParameter("bool0[]", "AND");
                builder.addQueryParameter("lookfor0[]", singleQuery.getValue());
            }
        }

        String hiddenfilters = getHiddenFilters(false);
        if (hiddenfilters != null) {
            builder.addQueryParameter("hiddenFilters[]", hiddenfilters);
        }

        return builder;
    }

    @Override
    public SearchRequestResult search(List<SearchQuery> query)
            throws IOException, OpacErrorException, JSONException {
        if (!initialised) start();
        last_query = query;

        HttpUrl.Builder builder = HttpUrl.parse(opac_url + "/Search/Results").newBuilder();
        String html = httpGet(buildSearchParams(query, builder).build().toString(),
                getDefaultEncoding());
        Document doc = Jsoup.parse(html);
        return parse_search(doc, 1);
    }

    protected SearchRequestResult parse_search(Document doc, int page) throws OpacErrorException {
        doc.setBaseUri(opac_url + "/Search/Results");

        if (doc.select("p.error, p.errorMsg, .alert-error").size() > 0) {
            throw new OpacErrorException(doc.select("p.error, p.errorMsg, .alert-error").text());
        } else if (doc.select("div.result").size() == 0 && doc.select(".main p").size() > 0) {
            throw new OpacErrorException(doc.select(".main p").first().text());
        }

        int rescount = -1;
        String rescountStr = null;
        if (doc.select(".resulthead").size() == 1) {
            rescountStr = doc.select(".resulthead strong").get(2).text();
        } else if (doc.select(".search-stats").size() == 1) {
            rescountStr = doc.select(".search-stats strong").get(1).text();
        }
        if (rescountStr != null) {
            // remove 1000s separator (e.g. 1.000 -> 1000)
            rescountStr = rescountStr.replace(",", "")
                                     .replace(".", "");
            rescount = Integer.parseInt(rescountStr);
        }
        List<SearchResult> reslist = new ArrayList<>();

        for (Element row : doc.select("div.result")) {
            SearchResult res = new SearchResult();
            Element z3988el = null;
            if (row.select("span.Z3988").size() == 1) {
                z3988el = row.select("span.Z3988").first();
            } else if (row.parent().tagName().equals("li") &&
                    row.parent().select("span.Z3988").size() > 0) {
                z3988el = row.parent().select("span.Z3988").first();
            }
            if (z3988el != null) {
                List<NameValuePair> z3988data;
                try {
                    StringBuilder description = new StringBuilder();
                    z3988data = URLEncodedUtils.parse(new URI("http://dummy/?"
                            + z3988el.select("span.Z3988").attr("title")), "UTF-8");
                    String title = null;
                    String year = null;
                    String author = null;
                    for (NameValuePair nv : z3988data) {
                        if (nv.getValue() != null) {
                            if (!nv.getValue().trim().equals("")) {
                                switch (nv.getName()) {
                                    case "rft.btitle":
                                    case "rft.atitle":
                                    case "rft.title":
                                        title = nv.getValue();
                                        break;
                                    case "rft.au":
                                    case "rft.creator":
                                        author = nv.getValue();
                                        break;
                                    case "rft.date":
                                        year = nv.getValue();
                                        break;
                                }
                            }
                        }
                    }
                    if (title != null) description.append("<b>").append(title).append("</b>");
                    if (author != null) description.append("<br />").append(author);
                    if (year != null) description.append("<br />").append(year);
                    res.setInnerhtml(description.toString());
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            } else {
                res.setInnerhtml(row.select("a.title").text());
            }

            if (row.hasClass("available") || row.hasClass("internet")) {
                res.setStatus(SearchResult.Status.GREEN);
            } else if (row.hasClass("reservable")) {
                res.setStatus(SearchResult.Status.YELLOW);
            } else if (row.hasClass("not-available")) {
                res.setStatus(SearchResult.Status.RED);
            } else if (row.select(".status.available").size() > 0) {
                res.setStatus(SearchResult.Status.GREEN);
            } else if (row.select(".status .label-success").size() > 0) {
                res.setStatus(SearchResult.Status.GREEN);
            } else if (row.select(".status .label-important").size() > 0) {
                res.setStatus(SearchResult.Status.RED);
            } else if (row.select(".status.checkedout").size() > 0) {
                res.setStatus(SearchResult.Status.RED);
            }

            for (Map.Entry<String, SearchResult.MediaType> entry : mediaTypeSelectors.entrySet()) {
                if (row.select(entry.getKey()).size() > 0) {
                    res.setType(entry.getValue());
                    break;
                }
            }

            for (Element img : row.select("img")) {
                String src = img.absUrl("src");
                if (src.contains("over")) {
                    if (!src.contains("Unavailable")) {
                        res.setCover(src);
                    }
                    break;
                }
            }

            res.setPage(page);
            String href = row.select("a.title").first().absUrl("href");
            try {
                URL idurl = new URL(href);
                String path = idurl.getPath();
                Matcher matcher = idPattern.matcher(path);
                if (matcher.find()) {
                    if (matcher.group().contains("/OpacrlRecord/")) {
                        res.setId("Opacrl:" + matcher.group(1));
                    } else {
                        res.setId(matcher.group(1));
                    }
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            reslist.add(res);
        }

        return new SearchRequestResult(reslist, rescount, page);
    }

    @Override
    public SearchRequestResult filterResults(Filter filter, Filter.Option option)
            throws IOException, OpacErrorException {
        return null;
    }

    @Override
    public SearchRequestResult searchGetPage(int page)
            throws IOException, OpacErrorException, JSONException {
        HttpUrl.Builder builder = HttpUrl.parse(opac_url + "/Search/Results").newBuilder();
        buildSearchParams(last_query, builder);
        builder.addQueryParameter("page", String.valueOf(page));
        String html = httpGet(builder.build().toString(), getDefaultEncoding());
        Document doc = Jsoup.parse(html);
        return parse_search(doc, page);
    }

    @Override
    public DetailedItem getResultById(String id, String homebranch)
            throws IOException, OpacErrorException {
        if (!initialised) start();

        if (data.has("library")) {
            // Migration from Bibliotheca IDs to smartBib IDs (implemented for Kreis Recklinghausen)
            // does not always work for e-books, as their IDs have changed
            Matcher matcher = bibliothecaIdPattern.matcher(id);
            if (matcher.matches()) {
                String library = data.optString("library");
                id = library + "." + matcher.group(1);
            }
        }

        String url = getShareUrl(id, null);
        String html = httpGet(url, getDefaultEncoding());
        Document doc = Jsoup.parse(html);
        doc.setBaseUri(url);

        Document detailsDoc = null;
        if (doc.select("a[href$=Description#tabnav]").size() == 1) {
            // smartBib description on separate page
            String detailsUrl =
                    httpGet(doc.select("a[href$=Description#tabnav]").first().absUrl("href"),
                            getDefaultEncoding());
            detailsDoc = Jsoup.parse(detailsUrl);
            detailsDoc.setBaseUri(detailsUrl);
        } else if (doc.select(".description").size() > 0 &&
                doc.select(".description-tab").size() == 0) {
            // load description tab via AJAX, seen in bibnet.smartbib.de
            detailsDoc = loadAjaxTab(id, "description");
        }

        Document volumesDoc = null;
        if (doc.select(".volumes").size() > 0 && doc.select(".volumes-tab").size() == 0) {
            // load volumes tab via AJAX, seen in bibnet.smartbib.de
            volumesDoc = loadAjaxTab(id, "volumes");
        }

        try {
            return parseDetail(id, doc, data, detailsDoc, volumesDoc);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private Document loadAjaxTab(String id, String tab) {
        FormBody body = new FormBody.Builder().add("tab", tab).build();
        try {
            String volumesHtml =
                    httpPost(opac_url + "/Record/" + id + "/AjaxTab", body, getDefaultEncoding());
            return Jsoup.parse(volumesHtml);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    static DetailedItem parseDetail(String id, Document doc, JSONObject data, Document detailsDoc,
            Document volumesDoc)
            throws OpacErrorException, JSONException {
        if (doc.select("p.error, p.errorMsg, .alert-error").size() > 0) {
            throw new OpacErrorException(doc.select("p.error, p.errorMsg, .alert-error").text());
        }

        DetailedItem res = new DetailedItem();
        res.setId(id);

        Elements title = doc.select(".record h1, .record [itemprop=name], .record [property=name]");
        if (title.size() > 0) {
            res.setTitle(title.first().text());
        }
        for (Element img : doc.select(".record img, #cover img")) {
            String src = img.absUrl("src");
            if (src.contains("over")) {
                if (!src.contains("Unavailable")) {
                    res.setCover(src);
                }
                break;
            }
        }

        String head = null;
        StringBuilder value = new StringBuilder();
        for (Element tr : doc.select(".record table").first().select("tr")) {
            if (tr.children().size() == 1) {
                if (tr.child(0).tagName().equals("th")) {
                    if (head != null) {
                        res.addDetail(new Detail(head, value.toString()));
                        value = new StringBuilder();
                    }
                    head = tr.child(0).text();
                } else {
                    if (!value.toString().equals("")) value.append("\n");
                    value.append(tr.child(0).text());
                }
            } else {
                String text = tr.child(1).text();
                if (tr.child(1).select("a").size() > 0) {
                    String href = tr.child(1).select("a").attr("href");
                    if (!href.trim().startsWith("/") && !text.contains(data.getString("baseurl"))) {
                        text += " " + href;
                    }
                }
                res.addDetail(new Detail(tr.child(0).text(), text));
            }
        }
        if (head != null) res.addDetail(new Detail(head, value.toString()));

        try {
            parseVolumes(res, doc, volumesDoc);
            if (res.getVolumes().size() == 0) {
                parseCopies(res, doc, data);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (detailsDoc != null) {
            // smartBib details
            Elements rows = detailsDoc.select(".description-tab table tr");
            for (Element row : rows) {
                res.addDetail(new Detail(row.select("th").text(),
                        row.select("td").text()));
            }
        }

        if (doc.select(".placehold").size() > 0) {
            res.setReservable(true);
            res.setReservation_info(
                    doc.select(".placehold").first().absUrl("href").replace("#tabnav", ""));
        } else if (doc.select(
                ".alert:contains(zum Vormerken), .alert:contains(For hold and recall information)")
                      .size() > 0) {
            res.setReservable(true);
            res.setReservation_info(null);
        }

        return res;
    }

    private static void parseVolumes(DetailedItem res, Document doc, Document volumesDoc) {
        // only tested in Münster
        // e.g. https://www.stadt-muenster.de/opac2/Record/0900944
        // and Kreis Recklinghausen
        // e.g. https://www.bib-kreisre.de/Record/HERT.0564417
        Element tab;
        if (volumesDoc != null) {
            tab = volumesDoc;
        } else {
            tab = doc.select(".recordsubcontent, .tab-container, .volumes-tab").first();
        }
        if (tab == null) return;

        Element table = tab.select("table").first();
        for (Element link : table.select("tr a")) {
            Volume volume = new Volume();
            Matcher matcher = idPattern.matcher(link.attr("href"));
            if (matcher.find()) volume.setId(matcher.group(1));
            volume.setTitle(link.text());
            res.addVolume(volume);
        }
    }

    static void parseCopies(DetailedItem res, Document doc, JSONObject data) throws JSONException {
        String copystyle = data.optString("copystyle");
        if ("doublestacked".equals(copystyle)) {
            // e.g. http://vopac.nlg.gr/Record/393668/Holdings#tabnav
            // for Athens_GreekNationalLibrary
            Element container = doc.select(".tab-container").first();
            String branch = "";
            for (Element child : container.children()) {
                if (child.tagName().equals("h5")) {
                    branch = child.text();
                } else if (child.tagName().equals("table")) {
                    int i = 0;
                    String callNumber = "";
                    for (Element row : child.select("tr")) {
                        if (i == 0) {
                            callNumber = row.child(1).text();
                        } else {
                            Copy copy = new Copy();
                            copy.setBranch(branch);
                            copy.setShelfmark(callNumber);
                            copy.setBarcode(row.child(0).text());
                            copy.setStatus(row.child(1).text());
                            res.addCopy(copy);
                        }
                        i++;
                    }
                }
            }

        } else if ("stackedtable".equals(copystyle)) {
            // e.g. http://search.lib.auth.gr/Record/376356
            // or https://katalog.ub.uni-leipzig.de/Record/0000196115
            // or https://www.stadt-muenster.de/opac2/Record/0367968
            Element container = doc.select(".recordsubcontent, .tab-container").first();
            // .tab-container is used in Muenster.
            String branch = "";
            JSONObject copytable = data.getJSONObject("copytable");
            for (Element child : container.children()) {
                if (child.tagName().equals("div")) {
                    child = child.child(0);
                }
                if (child.tagName().equals("h3")) {
                    branch = child.text();
                } else if (child.tagName().equals("table")) {
                    if (child.select("caption").size() > 0) {
                        // Leipzig_Uni
                        branch = child.select("caption").first().ownText();
                    }
                    int i = 0;
                    String callNumber = null;
                    if ("headrow".equals(copytable.optString("signature"))) {
                        callNumber = child.select("tr").get(0).child(1).text();
                    }
                    for (Element row : child.select("tr")) {
                        if (i < copytable.optInt("_offset", 0)) {
                            i++;
                            continue;
                        }
                        Copy copy = new Copy();
                        if (callNumber != null) {
                            copy.setShelfmark(callNumber);
                        }
                        copy.setBranch(branch);
                        Iterator<?> keys = copytable.keys();
                        while (keys.hasNext()) {
                            String key = (String) keys.next();
                            if (key.startsWith("_")) continue;
                            if (copytable.optString(key, "").contains("/")) {
                                // Leipzig_Uni
                                String[] splitted = copytable.getString(key).split("/");
                                int col = Integer.parseInt(splitted[0]);
                                int line = Integer.parseInt(splitted[1]);
                                int j = 0;
                                for (Node node : row.child(col).childNodes()) {
                                    if (node instanceof Element) {
                                        if (((Element) node).tagName().equals("br")) {
                                            j++;
                                        } else if (j == line) {
                                            copy.set(key, ((Element) node).text());
                                        }
                                    } else if (node instanceof TextNode && j == line &&
                                            !((TextNode) node).text().trim().equals("")) {
                                        copy.set(key, ((TextNode) node).text());
                                    }
                                }
                            } else {
                                // Thessaloniki_University
                                if (copytable.optInt(key, -1) == -1) continue;
                                String value = row.child(copytable.getInt(key)).text();
                                copy.set(key, value);
                            }
                        }
                        res.addCopy(copy);
                        i++;
                    }
                }
            }
        } else if ("smartbib".equals(copystyle)) {
            Element table = doc.select(".holdings-tab table").first();
            if (table == null) return;

            List<String> headers = new ArrayList<>();
            boolean ok = true;
            for (Element th : table.select("th")) {
                switch (th.text()) {
                    case "Bibliothek":
                    case "Bücherei":
                        headers.add("branch");
                        break;
                    case "Bereich":
                        headers.add("department");
                        break;
                    case "Standort":
                        headers.add("location");
                        break;
                    case "Buchungsnr.":
                    case "Signatur":
                        headers.add("barcode");
                        break;
                    case "Verfügbarkeit":
                    case "Status":
                        headers.add("status");
                        break;
                    case "Vorbestellungen":
                        headers.add("reservations");
                        break;
                    default:
                        ok = false;
                        break;
                }
            }

            if (ok) {
                for (Element tr : table.select("tr")) {
                    Elements columns = tr.select("td");
                    if (columns.size() == 0) continue; // header
                    Copy copy = new Copy();
                    for (int i = 0; i < columns.size(); i++) {
                        String text = columns.get(i).text();
                        if (text.isEmpty()) continue;
                        copy.set(headers.get(i), text);
                    }
                    res.addCopy(copy);
                }
            } else {
                // headers couldn't be detected, fall back to default
                for (Element tr : table.select("tr")) {
                    Elements columns = tr.select("td");
                    if (columns.size() == 0) continue; // header
                    Copy copy = new Copy();
                    copy.setBranch(columns.get(0).text());
                    copy.setLocation(columns.get(1).text());
                    copy.setShelfmark(columns.get(2).text());
                    copy.setStatus(columns.get(3).text());
                    copy.setReservations(columns.get(4).text());
                    res.addCopy(copy);
                }
            }
        }
    }

    @Override
    public DetailedItem getResult(int position) throws IOException, OpacErrorException {
        return null;
    }

    public void start() throws IOException {
        super.start();
        FormBody body = new FormBody.Builder().add("mylang", languageCode).build();
        httpPost(opac_url + "/Search/Advanced?" + getHiddenFilters(true), body,
                getDefaultEncoding());
    }

    @Override
    public List<SearchField> parseSearchFields()
            throws IOException, OpacErrorException, JSONException {
        start();
        String html = httpGet(opac_url + "/Search/Advanced?mylang=" + languageCode + "&" +
                        getHiddenFilters(true),
                getDefaultEncoding());
        Document doc = Jsoup.parse(html);

        List<SearchField> fields = new ArrayList<>();

        Elements options = doc.select("select#search_type0_0, select[name=type0[]]").first().select("option");
        for (Element option : options) {
            TextSearchField field = new TextSearchField();
            field.setDisplayName(option.text());
            field.setId(option.val());
            field.setHint("");
            field.setData(new JSONObject());
            field.getData().put("meaning", option.val());
            fields.add(field);
        }
        if (fields.size() == 0) {
            // Weird JavaScript, e.g. view-source:http://vopac.nlg.gr/Search/Advanced
            Pattern pattern_key = Pattern
                    .compile("searchFields\\[\"([^\"]+)\"\\] = \"([^\"]+)\";");
            for (Element script : doc.select("script")) {
                if (!script.html().contains("searchFields")) continue;
                for (String line : script.html().split("\n")) {
                    Matcher matcher = pattern_key.matcher(line);
                    if (matcher.find()) {
                        TextSearchField field = new TextSearchField();
                        field.setDisplayName(matcher.group(2));
                        field.setId(matcher.group(1));
                        field.setHint("");
                        field.setData(new JSONObject());
                        field.getData().put("meaning", field.getId());
                        fields.add(field);
                    }
                }
            }
        }

        String library = null;
        if (data.has("library")) {
            library = data.getString("library");
        }

        Elements selects = doc.select("select");
        for (Element select : selects) {
            if (!select.attr("name").equals("filter[]")) continue;

            DropdownSearchField field = new DropdownSearchField();
            if (select.parent().select("label").size() > 0) {
                field.setDisplayName(select.parent().select("label").first()
                                           .text());
            } else if (select.parent().parent().select("label").size() == 1) {
                field.setDisplayName(select.parent().parent().select("label").first()
                                           .text());
            }
            String id = select.attr("id");
            field.setId(select.attr("name") + id);
            String meaning = id;
            if (library != null &&
                    (meaning.equals("limit_collection") || meaning.equals("limit_building"))) {
                if (data.optBoolean("hiddenfilters") && meaning.equals("limit_collection")) {
                    // smartBib VuFind 5 does not need the library selection field (library fixed)
                    continue;
                } else {
                    field.addDropdownValue(OPTION_ALL,
                            stringProvider.getString(StringProvider.ALL));
                }
            } else {
                field.addDropdownValue("", "");
            }
            for (Element option : select.select("option")) {
                if (library != null && id.equals("limit_collection")) {
                    // smartBib: only collections that either belong to the selected library or are shared for all
                    Matcher matcher = buildingCollectionPattern.matcher(option.val());
                    if (matcher.matches()) {
                        String collection = matcher.group(1);
                        if (collection.contains(".") && !collection.startsWith(library + "."))
                            continue;
                    }
                } else if (library != null && id.equals("limit_building")) {
                    // smartBib: only branches that belong to the selected library
                    Matcher matcher = buildingCollectionPattern.matcher(option.val());
                    if (matcher.matches()) {
                        String branch = matcher.group(1);
                        if (!branch.startsWith(library + ".")) continue;
                    }
                }

                if (option.val().contains(":")) {
                    meaning = option.val().split(":")[0];
                }
                field.addDropdownValue(option.val(), option.text());
            }
            field.setData(new JSONObject());
            field.getData().put("meaning", meaning);
            fields.add(field);
        }

        return fields;
    }

    @Override
    public String getShareUrl(String id, String title) {
        if (id.contains(":")) {
            String[] parts = id.split(":");
            return opac_url + "/" + parts[0] + "Record/" + parts[1] + "?" + getHiddenFilters(true);
        } else {
            return opac_url + "/Record/" + id + "?" + getHiddenFilters(true);
        }
    }

    @Override
    public int getSupportFlags() {
        return SUPPORT_FLAG_ENDLESS_SCROLLING | SUPPORT_FLAG_WARN_RESERVATION_FEES |
                SUPPORT_FLAG_ACCOUNT_PROLONG_ALL;
    }

    @Override
    public Set<String> getSupportedLanguages() throws IOException {
        Set<String> langs = new HashSet<>();
        String html = httpGet(opac_url + "/Search/Advanced",
                getDefaultEncoding());
        Document doc = Jsoup.parse(html);
        if (doc.select("select[name=mylang]").size() > 0) {
            for (Element opt : doc.select("select[name=mylang] option")) {
                if (languageCodes.containsValue(opt.val())) {
                    for (Map.Entry<String, String> lc : languageCodes.entrySet()) {
                        if (lc.getValue().equals(opt.val())) {
                            langs.add(lc.getKey());
                            break;
                        }
                    }
                } else {
                    langs.add(opt.val());
                }
            }
        }
        return langs;
    }

    protected String getDefaultEncoding() {
        return "UTF-8";
    }

    @Override
    public void setLanguage(String language) {
        languageCode = languageCodes.containsKey(language) ? languageCodes.get(language) : language;
    }

    @Override
    public ReservationResult reservation(DetailedItem item, Account account,
            int useraction, String selection) throws IOException {
        if (res_url == null || !res_url.contains(item.getId())) {
            res_url = item.getReservation_info();
            if (res_url == null) { // not yet logged in
                try {
                    login(account);
                } catch (OpacErrorException e) {
                    res_branch = null;
                    res_hashCode = null;
                    res_url = null;
                    return new ReservationResult(MultiStepResult.Status.ERROR, e.getMessage());
                }
                String shareUrl = getShareUrl(item.getId(), null);
                Document doc = Jsoup.parse(httpGet(shareUrl, getDefaultEncoding()));
                doc.setBaseUri(shareUrl);
                if (doc.select(".placehold").size() > 0) {
                    res_url =
                            doc.select(".placehold").first().absUrl("href").replace("#tabnav", "");
                } else {
                    res_branch = null;
                    res_hashCode = null;
                    res_url = null;
                    return new ReservationResult(MultiStepResult.Status.ERROR,
                            stringProvider.getString(StringProvider.NO_COPY_RESERVABLE));
                }
            }
            //res_url = res_url.replace("&layout=lightbox&lbreferer=false", "");
        }

        if (useraction == 0) {
            Document doc = Jsoup.parse(httpGet(res_url, getDefaultEncoding()));

            if (doc.select("form[name=loginForm]").size() > 0) {
                // message "you need to be logged in first"
                try {
                    login(account);
                } catch (OpacErrorException e) {
                    res_branch = null;
                    res_hashCode = null;
                    res_url = null;
                    return new ReservationResult(MultiStepResult.Status.ERROR, e.getMessage());
                }
                return reservation(item, account, useraction, selection);
            }

            Element pickup = doc.select("#pickUpLocation").first();

            // does a pickup branch need to be selected?
            if (pickup != null) {
                List<Map<String, String>> options = new ArrayList<>();
                for (Element option : pickup.select("option")) {
                    Map<String, String> opt = new HashMap<>();
                    opt.put("key", option.val());
                    opt.put("value", option.text());
                    options.add(opt);
                }

                if (options.size() == 1) {
                    return reservation(item, account, ReservationResult.ACTION_BRANCH,
                            options.get(0).get("key"));
                } else {
                    ReservationResult res =
                            new ReservationResult(MultiStepResult.Status.SELECTION_NEEDED);
                    res.setSelection(options);
                    res.setActionIdentifier(ReservationResult.ACTION_BRANCH);
                    return res;
                }
            } else {
                return reservation(item, account, ReservationResult.ACTION_BRANCH, null);
            }
        } else if (useraction == 1) {
            res_branch = selection;

            FormBody.Builder form = new FormBody.Builder();
            form.add("confirmed", "false");
            if (res_branch != null) form.add("gatheredDetails[pickUpLocation]", res_branch);
            form.add("placeHold", "Confirm");
            Document doc = Jsoup.parse(httpPost(res_url, form.build(), getDefaultEncoding()));

            Element hashcode = doc.select("input[name=gatheredDetails[hashcode]]").first();
            if (hashcode != null) res_hashCode = hashcode.val();

            ReservationResult res =
                    new ReservationResult(MultiStepResult.Status.CONFIRMATION_NEEDED);
            List<String[]> details = new ArrayList<>();
            details.add(new String[]{doc.select(".alert").text()});
            res.setDetails(details);
            return res;
        } else if (useraction == MultiStepResult.ACTION_CONFIRMATION) {
            FormBody.Builder form = new FormBody.Builder();
            form.add("confirmed-checkbox", "true");
            form.add("confirmed", "true");
            if (res_branch != null) form.add("gatheredDetails[pickUpLocation]", res_branch);
            if (res_hashCode != null) form.add("gatheredDetails[hashcode]", res_hashCode);
            form.add("placeHold", "Confirm");
            Document doc = Jsoup.parse(httpPost(res_url, form.build(), getDefaultEncoding()));

            res_branch = null;
            res_url = null;

            return new ReservationResult(MultiStepResult.Status.OK);
        }
        return null;
    }

    @Override
    public ProlongResult prolong(String media, Account account, int useraction,
            String selection) throws IOException {
        try {
            login(account);
        } catch (OpacErrorException e) {
            return new ProlongResult(MultiStepResult.Status.ERROR, e.getMessage());
        }

        String[] split = media.split("\\|");
        String prolongData = split[1];
        String recordId = split[0];

        FormBody.Builder builder = new FormBody.Builder();
        builder.add("renewSelected", "Verlängern");
        builder.add("renewSelectedIDS[]", prolongData);
        Document doc = Jsoup.parse(httpPost(opac_url + "/MyResearch/CheckedOut", builder.build(),
                getDefaultEncoding()));

        // try to find the prolonged record to get success or error message
        Element record;
        Element checkbox = doc.select("input[type=checkbox][value=" + prolongData + "]").first();
        if (checkbox != null) {
            record = checkbox.parent().parent();
        } else {
            // selecting using # seems to not work if there is a . in the id,
            // so use div[id=...] instead
            record = doc.select("div[id=" + recordId + "]").first();
        }

        if (record == null) {
            return new ProlongResult(MultiStepResult.Status.ERROR,
                    stringProvider.getString(StringProvider.INTERNAL_ERROR));
        }

        if (record.select(".alert-success").size() == 1) {
            return new ProlongResult(MultiStepResult.Status.OK);
        } else {
            return new ProlongResult(MultiStepResult.Status.ERROR,
                    record.select(".alert-renew").text());
        }
    }

    @Override
    public ProlongAllResult prolongAll(Account account, int useraction, String selection)
            throws IOException {
        try {
            login(account);
        } catch (OpacErrorException e) {
            return new ProlongAllResult(MultiStepResult.Status.ERROR, e.getMessage());
        }

        Document doc =
                Jsoup.parse(httpGet(opac_url + "/MyResearch/CheckedOut", getDefaultEncoding()));

        FormBody.Builder builder = new FormBody.Builder();
        builder.add("renewSelected", "Verlängern");
        builder.add("selectAll", "on");

        for (Element input : doc.select("#renewals input[type=hidden]")) {
            builder.add(input.attr("name"), input.val());
        }

        doc = Jsoup.parse(httpPost(opac_url + "/MyResearch/CheckedOut", builder.build(),
                getDefaultEncoding()));

        List<Map<String, String>> results = new ArrayList<>();
        for (Element record : doc.select("#record")) {
            Map<String, String> data = new HashMap<>();
            data.put(ProlongAllResult.KEY_LINE_TITLE,
                    record.children().get(1).select("strong").first().text());
            String result = record.select(".alert-success").size() > 0 ?
                    record.select(".alert-success").text() : record.select(".alert-renew").text();
            data.put(ProlongAllResult.KEY_LINE_MESSAGE, result);
            results.add(data);
        }

        return new ProlongAllResult(MultiStepResult.Status.OK, results);
    }

    @Override
    public ProlongAllResult prolongMultiple(List<String> media,
            Account account, int useraction, String selection) throws IOException {
        return null;
    }

    @Override
    public CancelResult cancel(String media, Account account, int useraction,
            String selection) throws IOException, OpacErrorException {
        try {
            login(account);
        } catch (OpacErrorException e) {
            return new CancelResult(MultiStepResult.Status.ERROR, e.getMessage());
        }

        FormBody.Builder builder = new FormBody.Builder();
        builder.add("cancelSelected", "Ausgewählte Vorbestellungen löschen");
        builder.add("cancelSelectedIDS[]", media);
        Document doc = Jsoup.parse(httpPost(opac_url + "/MyResearch/Holds", builder.build(),
                getDefaultEncoding()));

        Elements record =
                doc.select("input[type=checkbox][value=" + media + "]");
        if (record.size() == 0) {
            return new CancelResult(MultiStepResult.Status.OK);
        } else {
            return new CancelResult(MultiStepResult.Status.ERROR,
                    doc.select(".flash-message.alert").text());
        }
    }

    @Override
    public AccountData account(Account account)
            throws IOException, JSONException, OpacErrorException {
        login(account);

        AccountData data = new AccountData(account.getId());

        Function<Response, Document> parse = r -> {
            try {
                return Jsoup.parse(r.body().string());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        CompletableFuture<List<LentItem>> lentFuture =
                asyncGet(opac_url + "/MyResearch/CheckedOut", false)
                        .thenApplyAsync(parse).thenApplyAsync(VuFind::parse_lent);
        CompletableFuture<List<ReservedItem>> reservationsFuture =
                asyncGet(opac_url + "/MyResearch/Holds", false)
                        .thenApplyAsync(parse).thenApplyAsync(VuFind::parse_reservations);
        CompletableFuture<Void> finesFuture =
                asyncGet(opac_url + "/MyResearch/Fines", false)
                        .thenApplyAsync(parse).thenAccept(doc -> {
                    Element table = doc.select("#content table").first();
                    if (table != null) {
                        Element fees = table.select("tr").last().select("td").last();
                        data.setPendingFees(fees.text().trim());
                    }
                    String validUntil = doc.select(
                            ".list-group-item:contains(Valid until), " +
                                    ".list-group-item:contains(Gültig bis)").text();
                    validUntil = validUntil.replaceAll("Valid until\\s*:", "")
                                           .replaceAll("Gültig bis\\s*:", "").trim();
                    data.setValidUntil(!validUntil.equals("") ? validUntil : null);
                });

        try {
            data.setLent(lentFuture.get());
            data.setReservations(reservationsFuture.get());
            finesFuture.get();
        } catch (InterruptedException ignored) {

        } catch (ExecutionException e) {
            if (e.getCause() != null) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                } else if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                } else {
                    throw new NotReachableException();
                }
            } else {
                throw new NotReachableException();
            }
        }

        return data;
    }

    static List<LentItem> parse_lent(Document doc) {
        List<LentItem> lent = new ArrayList<>();
        for (Element record : doc.select("div[id^=record]")) {
            LentItem item = new LentItem();
            if (record.select("span.Z3988").size() > 0) {
                // vuFind 5 / smartBib new
                String z3988data = record.select("span.Z3988").attr("title");
                HttpUrl url = HttpUrl.parse("http://dummy/?" + z3988data);
                if (url != null) {
                    for (int i = 0; i < url.querySize(); i++) {
                        String key = url.queryParameterName(i);
                        String value = url.queryParameterValue(i);
                        switch (key) {
                            case "rft.btitle":
                            case "rft.atitle":
                            case "rft.title":
                                if (value.isEmpty()) value = "Kein Titel verfügbar";
                                item.setTitle(value);
                                break;
                            case "rft.au":
                            case "rft.creator":
                                item.setAuthor(value);
                                break;
                            case "rft.format":
                                item.setFormat(value);
                                break;
                        }
                    }
                }

                Element link = record.select("a[href*=Record]").first();
                if (link != null) {
                    item.setId(link.attr("href"));
                }

                Element due = record.select(
                        "strong:contains(Rückgabedatum), strong:contains(Due date), " +
                                "strong:contains(Fecha de Vencimiento), strong:contains(Délais " +
                                "d\\'emprunt), strong:contains(Data di scadenza)")
                                    .first();
                if (due != null) {
                    String text = due.text().replaceAll("[^\\d.]", "");
                    if (!text.isEmpty()) {
                        item.setDeadline(
                                DateTimeFormat.forPattern("dd.MM.yyyy").parseLocalDate(text));
                    }
                }

                for (TextNode node : record.select(".media-body").first().textNodes()) {
                    if (node.text().matches("\\s*\\d+\\s*/\\s*\\d+\\s*")) {
                        Element label = (Element) node.previousSibling();
                        item.setStatus(label.text() + node.text());
                    }
                }

                Element alert = record.select(".alert").first();
                if (alert != null) {
                    item.setStatus(alert.text());
                }
            } else {
                // old style
                String type = null;
                boolean title = true;
                boolean statusAfter = false;
                Element dataColumn = record.select("> div").last();
                for (Node node : dataColumn.childNodes()) {
                    if (node instanceof Element && ((Element) node).tagName().equals("strong")) {
                        Element el = (Element) node;
                        if (title) {
                            item.setTitle(el.text());
                            title = false;
                        } else if (statusAfter) {
                            // return date
                            String text = el.text().replace("Bis", "").replace("Until", "").trim();
                            item.setDeadline(
                                    DateTimeFormat.forPattern("dd.MM.yyyy").parseLocalDate(text));
                        } else {
                            type = el.text().replace(":", "").trim();
                            String nextText = el.nextElementSibling().text();
                            if (nextText.startsWith("Bis") || nextText.startsWith("Until")) {
                                statusAfter = true;
                            }
                        }
                    } else if (node instanceof TextNode) {
                        String text = ((TextNode) node).text().trim();
                        if (text.equals("")) continue;
                        if (text.endsWith(",")) text = text.substring(0, text.length() - 1).trim();

                        if (statusAfter) {
                            String[] split = text.split(",");
                            if (split.length == 2) {
                                text = split[0].trim();
                                item.setStatus(split[1].trim());
                            }
                        }

                        if (type == null) {
                            item.setAuthor(text.replaceFirst("\\[([^\\]]*)\\]", "$1"));
                        } else {
                            switch (type) {
                                case "Zweigstelle":
                                case "Borrowing Location":
                                    item.setLendingBranch(text);
                                    break;
                                case "Medientyp":
                                case "Media type":
                                    item.setFormat(text);
                                    break;
                                case "Buchungsnummer":
                                case "barcode":
                                    item.setBarcode(text);
                                    break;
                            }
                        }
                    }
                }
            }

            Element checkbox = record.select("input[type=checkbox]").first();
            if (checkbox != null && !"".equals(checkbox.val())) {
                item.setProlongData(record.attr("id") + "|" + checkbox.val());
                item.setRenewable(!checkbox.hasAttr("disabled"));
            } else {
                item.setRenewable(false);
            }

            lent.add(item);
        }
        return lent;
    }

    static List<ReservedItem> parse_reservations(Document doc) {
        List<ReservedItem> reserved = new ArrayList<>();
        for (Element record : doc.select("div[id^=record]")) {
            ReservedItem item = new ReservedItem();

            if (record.select("a.title").size() > 0) {
                // smartBib, physical books
                item.setTitle(record.select("a.title").text());
                String[] urlParts = record.select("a.title").attr("href").split("/");
                item.setId(urlParts[urlParts.length - 1]);
            } else {
                // smartBib, eBooks
                Node firstNode = record.select("div").get(2).childNodes().get(0);
                if (firstNode instanceof TextNode) {
                    item.setTitle(((TextNode) firstNode).text());
                }
            }

            if (record.select("a[href*=Author]").size() > 0) {
                item.setAuthor(record.select("a[href*=Author]").text());
            }

            if (record.select(".format").size() > 0) {
                // smartBib, physical books
                item.setFormat(record.select(".format").text());
                // we could also recognize the format through mediaTypeSelectors here
            } else {
                // smartBib, eBooks
                List<Node> nodes = record.select("div").get(2).childNodes();
                if (nodes.size() >= 3 && nodes.get(2) instanceof TextNode
                        && ((TextNode) nodes.get(2)).text().contains("Mediengruppe:")) {
                    item.setFormat(((TextNode) nodes.get(2)).text().replace("Mediengruppe:",
                            "").trim());
                }
            }

            Node branch = record.select("strong:contains(Zweigstelle), strong:contains(Pickup library)").first();
            if (branch != null && branch.nextSibling() instanceof TextNode) {
                item.setBranch(((TextNode) branch.nextSibling()).text().trim());
            }

            Elements available = record.select(
                    "strong:contains(Abholbereit), " +
                            "strong:contains(Available for pickup), " +
                            ".text-success");
            if (available.size() > 0) {
                item.setStatus(available.first().text().replace(":", "").trim());
            }

            Element checkbox = record.select("input[type=checkbox]").first();
            if (checkbox != null && !checkbox.hasAttr("disabled")) {
                item.setCancelData(checkbox.val());
            }

            reserved.add(item);
        }
        return reserved;
    }

    private void login(Account account) throws IOException, OpacErrorException {
        Document doc = Jsoup.parse(httpGet(opac_url + "/MyResearch/Home", getDefaultEncoding()));
        Element loginForm = doc.select("form[name=loginForm]").first();

        if (loginForm == null) return;

        FormBody.Builder builder = new FormBody.Builder()
                .add("username", account.getName())
                .add("password", account.getPassword());

        for (Element hidden : loginForm.select("input[type=hidden]")) {
            builder.add(hidden.attr("name"), hidden.val());
        }

        if (data.has("library")) {
            Element field = loginForm
                    .select("select[name=target], select[name=library_select]").first();
            String name = field != null ? field.attr("name") : "library_select";
            builder.add(name, data.optString("library"));
        }

        Element submit = loginForm.select("input[type=submit]").first();
        if (submit != null) {
            builder.add(submit.attr("name"), submit.attr("value"));
        }

        doc = Jsoup.parse(
                httpPost(opac_url + "/MyResearch/Home", builder.build(), getDefaultEncoding()));
        if (doc.select(".flash-message").size() > 0 &&
                doc.select("form[name=loginForm]").size() > 0) {
            throw new OpacErrorException(doc.select(".flash-message").text());
        }
    }

    @Override
    public void checkAccountData(Account account)
            throws IOException, JSONException, OpacErrorException {
        login(account);
    }
}
