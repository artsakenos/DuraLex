package tk.artsakenos.ultragraph.duralex.scraper;

import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import tk.artsakenos.iperunits.file.FileManager;
import tk.artsakenos.iperunits.file.SuperFileText;
import tk.artsakenos.iperunits.file.SuperLog;
import tk.artsakenos.iperunits.serial.Jsonable;
import tk.artsakenos.iperunits.string.SuperString;
import tk.artsakenos.iperunits.system.Mouse;
import tk.artsakenos.ultragraph.duralex.model.Articolo;
import tk.artsakenos.ultragraph.duralex.model.LegalIndex;
import tk.artsakenos.ultrahttp.Client;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static tk.artsakenos.iperunits.file.SuperLog.log;
import static tk.artsakenos.ultragraph.duralex.DuralexNetwork.TAG;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * @author Andrea
 * @version Jul 13, 2020
 */
public class Scraper_Normattiva {

    private static final String T_LEGGE = "Legge";
    private static final String T_DECRETO = "Decreto";
    private static final String BASE_URL = "https://www.normattiva.it";
    private static final Client client = new Client(BASE_URL, "", "");

    public Scraper_Normattiva() {
        client.timeout = 120;
    }

    /**
     * Scarica tutti gli articoli di una data legge. Possono essere più di un
     * migliaio, con un sacco di sottovoci.
     * <p>
     * Può restituire un array vuoto (articolo numero esistente ma vuoto per
     * qualche errore), o un array null (significa che quel numero articolo non
     * c'è perché siamo alla fine).
     *
     * @param index        il LegalIndex utilizzato per controllare se un articolo è
     *                     già stato scaricato, null altrimenti
     * @param tipologia    e.g., T_LEGGE, T_DECRETO
     * @param anno         e.g., 1996. Dal 1861 ad oggi.
     * @param numero       e.g., 675
     * @param url_bypasser Se url_bypasser è settato viene utilizzato quello,
     *                     non i link convenzionali. Come nel caso di
     *                     https://www.normattiva.it/uri-res/N2Ls?urn:nir:stato:legge:2001-01-23;1
     *                     che si incasina e va navigato imponendo il passaggio di:
     *                     https://www.normattiva.it/atto/caricaDettaglioAtto?atto.dataPubblicazioneGazzetta=2001-01-11&atto.codiceRedazionale=001G0014
     */
    @SuppressWarnings("UnusedAssignment")
    public TreeSet<Articolo> downloadDecreti(LegalIndex index, String tipologia, int anno, int numero, String url_bypasser) {
        final TreeSet<Articolo> decreti = new TreeSet<>();

        String url_mainframe = "";
        if (tipologia.equals(T_LEGGE)) {
            url_mainframe += "/uri-res/N2Ls?urn:nir:stato:legge:";
        }
        if (tipologia.equals(T_DECRETO)) {
            url_mainframe += "/uri-res/N2Ls?urn:nir:stato:decreto.legge:";
        }
        url_mainframe += anno + "-12-31;" + numero; // e.g., 1996-12-31;675
        // All'url_mainframe non gliene frega niente della data, gli basta il numero progressivo per l'anno...
        String codice_path = "/decreti/" + tipologia.toLowerCase();
        String articolo_html_index_path = TAG + "/html" + codice_path + "/" + anno + "/n." + numero + "_0_index.html";
        FileManager.mkDir(TAG + "/html" + codice_path + "/" + anno);

        Response response = null;
        String html = null;

        response = client.get(url_mainframe);
        // Giusto un azzeccagarbuglio software per peggiorare l'accessibilità all'uomo comune.
        String cookie = response.header("Set-Cookie");
        client.headers.put("Cookie", cookie);

        if (url_bypasser != null && !url_bypasser.isEmpty()) {
            url_mainframe = url_bypasser;
            response = client.get(url_mainframe);
        }

        // Pagina Principale con gli iframe menu e articolo in vista, e i commenti nostalgici dello sviluppatore.
        html = Client.toString_Response(response);

        Document document = Jsoup.parse(html);
        if (document.select("iframe").isEmpty()) {
            // TODO: Potrebbe essere una di quelle duplicazioni da malati mentali, da trattare: /uri-res/N2Ls?urn:nir:stato:legge:2001-01-23;1
            log(TAG, "Attenzione, incasinamento articolo (" + tipologia + ") Anno:" + anno + "; Numero:" + numero + "; " + client.getBaseUrl() + url_mainframe, true);
            // In questo caso si apre quel link, e.g., https://www.normattiva.it/uri-res/N2Ls?urn:nir:stato:legge:2001-12-31;2 si sceglie un link e si piazza nel bypasser.
            return null;
        }
        String alberoArticoliUrl = document.select("iframe").get(0).attr("src");
        String articoloDefault_link = document.select("iframe").get(1).attr("src");
        String articoloDefault_testo = "";
        String sezione_titolo_id = document.select("div#testa_atto p.grassetto").text();
        String sezione_titolo_caption = document.select("div#testa_atto span.riferimento").text();
        String articolo_titolo = document.select("div#testa_atto").html();
        articolo_titolo = SuperString.getFrom(articolo_titolo, "</p>", "\n").trim();

        String html_indice;
        if (FileManager.fileExists(articolo_html_index_path)) {
            html_indice = SuperFileText.getText(articolo_html_index_path);
        } else {
            // Frame Sinistro con Elenco degli articoli
            response = client.get(alberoArticoliUrl);
            html_indice = Client.toString_Response(response);
            // Salviamo l'indice html
            SuperFileText.setText(articolo_html_index_path, html_indice);
        }

        document = Jsoup.parse(html_indice);
        Elements elements = document.select("a");

        if (elements.isEmpty()) {
            // Se è empty non ci sono articolo_frame_link, bisogna aprire l'articolo di default.
            response = client.get(articoloDefault_link);
            articoloDefault_testo = Client.toString_Response(response);
            Element element = new Element("html");
            element.append(articoloDefault_testo);
            elements.add(element);
        }

        for (Element element : elements) {
            // Ci sono gruppi di due articolo_frame_link:
            // /atto/caricaAggiornamentiNodoArticolo?art.progressivo=0&art.idArticolo=1 [...]
            // /atto/caricaArticolo?art.progressivo=0&art.idArticolo=45&art.versione=1&art.codiceRedazionale=097G0004&art.dataPubblicazioneGazzetta=1997-01-08&atto.tipoProvvedimento=LEGGE&art.idGruppo=13&art.idSottoArticolo1=10&art.idSottoArticolo=1&art.flagTipoArticolo=0#art
            // Il decreto è nel secondo.
            String articolo_frame_link;
            String html_decreto;
            String articolo_numero = "N." + numero;

            // Se c'è solo un articolo questo viene messo in articoloDefault_testo perché la root ha solo un frame
            if (articoloDefault_testo.isEmpty()) {
                articolo_frame_link = element.attr("href");
                if (!articolo_frame_link.contains("/atto/caricaArticolo")) {
                    // Ci sono coppie articolo_frame_link, uno per la pagina di default che non mi interessa.
                    continue;
                }
                articolo_numero += " " + element.text();
                String articolo_endpoint = "/" + anno + "/" + articolo_numero.replaceAll(" ", "_").toLowerCase();
                // Se ho già scaricato il sotto articolo, bypasso
                if (FileManager.fileExists(TAG + codice_path + articolo_endpoint + ".json")) {
                    log(TAG, "Articolo " + articolo_endpoint + " già presente!");
                    continue;
                }

                // A volte si verifica questa cosa assurda... ma nel sito sono corretti, boh, lo correggo qui!
                articolo_frame_link = articolo_frame_link.replace("&.id", "&art.id");
                articolo_frame_link = articolo_frame_link.replace("&.versione", "&art.versione");
                articolo_frame_link = articolo_frame_link.replace("&.flag", "&art.flag");
                articolo_frame_link = articolo_frame_link.replace("&.codice", "&art.codice");
                articolo_frame_link = articolo_frame_link.replace("&.data", "&art.data");

                response = client.get(articolo_frame_link);
                html_decreto = Client.toString_Response(response);
            } else {
                articolo_frame_link = articoloDefault_link;
                html_decreto = element.toString();
                articolo_numero += " 1";
            }
            // articolo_frame_link = articolo_frame_link.replace(BASE_URL, "");

            // Inizia il parsing
            Articolo decreto = fillDecreto(html_decreto, articolo_numero, articolo_frame_link, articolo_titolo, sezione_titolo_id, sezione_titolo_caption, anno, tipologia);
            decreti.add(decreto);
        }

        if (decreti.isEmpty()) {
            log(TAG, "Attenzione, Articoli vuoti? (" + tipologia + ") " + numero + " per l'anno " + anno + ": " + client.getBaseUrl() + url_mainframe);
            return decreti;
        }

        return decreti;
    }

    public Articolo fillDecreto(
            String html_decreto,
            String articolo_numero,
            String articolo_frame_link,
            String articolo_titolo,
            String sezione_titolo_id,
            String sezione_titolo_caption,
            int anno,
            String tipologia) {
        // Inizia il parsing
        Document document = Jsoup.parse(html_decreto);
        Elements elements = document.select("pre .rosso");
        String dal = "";
        String al = "";
        if (!elements.isEmpty()) {
            dal = elements.get(0).text().trim();
        }
        if (elements.size() > 1) {
            al = elements.get(1).text().trim();
        }

        elements = document.select("pre.rosso");
        String note_rosse = elements.first().html();
        String decreto_testo = document.select(".wrapper_pre pre").first().html();
        decreto_testo = decreto_testo.replaceAll("\r", "").trim();

        Articolo decreto = new Articolo();
        decreto.articolo_titolo = articolo_titolo;
        decreto.articolo_endpoint = "/" + anno + "/" + articolo_numero.replaceAll(" ", "_").toLowerCase();
        decreto.link_self = articolo_frame_link;
        decreto.articolo_numero = articolo_numero;
        decreto.articolo_html = decreto_testo;
        decreto.articolo_testo = Jsoup.clean(decreto_testo, Safelist.none());
        decreto.articolo_validita = dal + "⇒" + al;
        decreto.articolo_note = note_rosse;
        decreto.sezione_titolo_id = sezione_titolo_id;
        decreto.sezione_titolo_caption = sezione_titolo_caption;
        decreto.codice_nome = tipologia;
        decreto.codice_path = "/decreti/" + tipologia.toLowerCase();
        decreto.articolo_argomenti = null;
        decreto.articolo_correlati = null;
        decreto.articolo_sentenze = null;

        // Salvataggio del decreto
        Jsonable.toFile(decreto.pathJson(TAG), decreto, true);
        SuperFileText.setText(decreto.pathHtml(TAG), html_decreto);
        log(TAG, "Saved: " + decreto);
        return decreto;
    }

    // -------------------------------------------------------------------------
    private final Set<String> COMPLETED_TASK = Collections.synchronizedSet(new TreeSet<>());
    private final String COMPLETED_JSON = "COMPLETED_TASK.json";

    /**
     * Scarica da Normattiva questi anni. from e to inclusivi.
     *
     * @param from
     * @param to
     */
    public void download(int from, int to) {
        COMPLETED_TASK.addAll(Jsonable.fromFile(COMPLETED_JSON, String.class, "", ""));
        final LegalIndex index = new LegalIndex();
        index.load_decreti_information(TAG, "/decreti/legge/");
        log(TAG + "_INDEX", index.toString());

        int MAX_THREADS = 20;
        final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);

        for (int anno = from; anno <= to; ++anno) {
            final int f_anno = anno;
            executor.submit(() -> {
                int errors = 0;
                for (int numero = 1; numero < 5000; ++numero) {
                    String completed_key = f_anno + "_" + numero;
                    if (COMPLETED_TASK.contains(completed_key)) {
                        continue;
                    }
                    TreeSet<Articolo> loadDecreti = null;
                    try {
                        loadDecreti = downloadDecreti(index, T_LEGGE, f_anno, numero, null);
                    } catch (Exception e) {
                    }
                    if (loadDecreti == null) {
                        SuperLog.log(TAG, "RIPROVANDO (" + errors + "): " + completed_key);
                        if ((numero > 50) && (++errors > 10)) {
                            numero = 5000; // Finiamo
                        }
                    }
                    if (Mouse.isMouse00()) {
                        numero = 5000;
                    }
                    COMPLETED_TASK.add(completed_key);
                    // showArticoli(loadDecreti);
                }
                synchronized (this) {
                    Jsonable.toFile(COMPLETED_JSON, COMPLETED_TASK, true);
                }
            });
        }
        SuperLog.log(TAG, "It's All Good Man!");
    }

    /**
     * Salva le versioni ALL & COMPACT dei decreti.
     *
     * @param from
     * @param to
     */
    public void save(int from, int to) {
        final LegalIndex index = new LegalIndex();
        index.load_decreti_information(TAG, "/decreti/legge");
        for (int anno = from; anno <= to; ++anno) {
            String path = TAG + "/decreti/legge/" + anno;
            index.getById("/decreti/legge").articoli.clear();
            index.saveJson(path, false);
            index.saveCsv(path);
        }
    }

    /**
     * Carica i decreti dai file Json.
     *
     * @param jsonFolder Il folder dove si trovano i file, dovrebbero essere nel
     *                   formato: /DuraLex_decreti_legge_%d_All.json
     * @param year_from  Anno di partenza (inclusivo)
     * @param year_to    Anno di arrivo (inclusivo)
     * @return tutti gli articoli
     */
    public static ArrayList<Articolo> load(String jsonFolder, int year_from, int year_to) {
        String path = jsonFolder + "/DuraLex_decreti_legge_%d_All.json";
        // C'è anche un formato Compact, che corrisponde al legalIndex e contiene solo l'HTML
        final ArrayList<Articolo> articoli = new ArrayList<>();

        for (int year = year_from; year <= year_to; ++year) {
            String spath = String.format(path, year);
            List<Articolo> fromFile = Jsonable.fromFile(spath, Articolo.class, "", "");
            articoli.addAll(fromFile);
            log(TAG, "Loaded year " + year + ". Until Now Loaded " + articoli.size() + " Articoli.");
        }

        return articoli;
    }

    // -------------------------------------------------------------------------
    public void test() {
        final int test = 4;

        if (test == 1) { // Non c'è bisogno, basta partire dal link che crea il casino e passarlo in bypasser.
            String main_link = "https://www.normattiva.it/uri-res/N2Ls?urn:nir:stato:legge:2001-01-23;1";
            String articolo_frame_link = "https://www.normattiva.it/atto/caricaArticolo?art.progressivo=0&art.idArticolo=1&art.versione=1&art.codiceRedazionale=001G0014&art.dataPubblicazioneGazzetta=2001-01-11&atto.tipoProvvedimento=DECRETO-LEGGE&art.idGruppo=0&art.idSottoArticolo1=10&art.idSottoArticolo=1&art.flagTipoArticolo=0#art";
            Response response = client.get(main_link);
            String cookie = response.header("Set-Cookie");
            client.headers.put("Cookie", cookie);
            response = client.get(articolo_frame_link);
            String html_decreto = Client.toString_Response(response);
            String articolo_numero = "N.1 1";
            String articolo_titolo = "Disposizioni urgenti per la distruzione del materiale specifico a rischio per encefalopatie spongiformi bovine e delle proteine animali ad alto rischio, nonche' per l'ammasso pubblico temporaneo delle proteine animali a basso rischio.((Ulteriori interventi urgenti per fronteggiare l'emergenza derivante dall'encefalopatia spongiforme bovina)).";
            String sezione_titolo_id = "DECRETO-LEGGE 11 gennaio 2001, n. 1";
            String sezione_titolo_caption = "(GU n.8 del 11-1-2001 )";

            fillDecreto(html_decreto, articolo_numero, articolo_frame_link, articolo_titolo, sezione_titolo_id, sezione_titolo_caption, 2001, T_LEGGE);
        }

        if (test == 2) {
            downloadDecreti(null, T_LEGGE, 2001, 5, null);
            downloadDecreti(null, T_LEGGE, 2001, 6, null);
            downloadDecreti(null, T_LEGGE, 2001, 7, null);
        }

        if (test == 3) {
            downloadDecreti(null, T_LEGGE, 2001, 1, "https://www.normattiva.it/atto/caricaDettaglioAtto?atto.dataPubblicazioneGazzetta=2001-01-11&atto.codiceRedazionale=001G0014");
        }

    }

    @SuppressWarnings("UnnecessaryReturnStatement")
    public static void main(String[] args) {
        final Scraper_Normattiva normattiva = new Scraper_Normattiva();
        SuperLog.log(TAG, "\n"
                + "* stats :=                           gives some stats;\n"
                + "* download <anno_from> <anno_to>:=   downloads;\n"
                + "* save     <anno_from> <anno_to>:=   save completed;\n");

        if (args.length > 0) {
            if (args[0].equals("stats")) {
                final LegalIndex index = new LegalIndex();
                index.load_decreti_information(TAG, "/decreti/legge/");
                log(TAG + "_INDEX", index.toString());
            }
            if (args[0].equals("download")) {
                int from = Integer.parseInt(args[1]);
                int to = Integer.parseInt(args[2]);
                normattiva.download(from, to);
            }
            if (args[0].equals("save")) {
                int from = Integer.parseInt(args[1]);
                int to = Integer.parseInt(args[2]);
                normattiva.save(from, to);
            }
            return;
        }

        // normattiva.download(1900, 1919);
        // normattiva.save(1900, 1919);
        // normattiva.test();
        // normattiva.load("decreti 2000-2019/compact", 2003, 2019);

    }

}
