/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tk.artsakenos.ultragraph.duralex.scraper;

import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import tk.artsakenos.iperunits.file.FileManager;
import tk.artsakenos.iperunits.file.SuperFileText;
import tk.artsakenos.iperunits.file.SuperLog;
import tk.artsakenos.iperunits.serial.Jsonable;
import tk.artsakenos.iperunits.string.SuperString;
import tk.artsakenos.ultragraph.duralex.model.Articolo;
import tk.artsakenos.ultragraph.duralex.model.LegalIndex;
import tk.artsakenos.ultragraph.duralex.model.Sentenza;
import tk.artsakenos.ultrahttp.Client;

import static tk.artsakenos.ultragraph.duralex.DuralexNetwork.TAG;


/**
 * LexScripta è stato il repository di riferimento dal quale ho estrapolato
 * DuraLex!
 *
 * @version Jul 15, 2020
 * @author Andrea
 */
public class Scraper_LexScripta {

    private static final String BASE_URL = "https://lexscripta.it";
    public final Client client = new Client(BASE_URL, "", "");
    private final LegalIndex index = new LegalIndex();

    private String debase(String url) {
        return SuperString.getFrom(url, BASE_URL);
    }

    /**
     * Carica il contenuto html della pagina dell'articolo.
     *
     * @param codice_path e.g., /codici/codice-della-strada
     * @param articolo_endpoint e.g., /articolo-1
     * @param offline se offline carica da file, altrimenti da sito
     * @return la pagina html dell'articolo
     */
    public String load_article_html(String codice_path, String articolo_endpoint, boolean offline) {
        return load_article_html(codice_path + articolo_endpoint, offline);
    }

    /**
     * Carica il contenuto html della pagina dell'articolo.
     *
     * @param articolo_path e.g., /codici/codice-della-strada/articolo-1
     * @param offline se offline carica da file, altrimenti da sito
     * @return la pagina html dell'articolo
     */
    public String load_article_html(String articolo_path, boolean offline) {
        if (offline) {
            String html_content = SuperFileText.getText(TAG + "/html" + articolo_path + ".html");
            return html_content;
        } else {
            Response response = client.get(articolo_path);
            String html_content = Client.toString_Response(response);
            return html_content;
        }
    }

    /**
     * Immagazzina html e json dell'articolo con tutti i dettagli.
     *
     * @param codice_path e.g., /codici/codice-della-strada
     * @param article_html l'html dell'articolo
     * @return un Articolo
     */
    public Articolo get_article(String codice_path, String article_html) {
        Document root = Jsoup.parse(article_html);

        final Articolo articolo = new Articolo();

        Elements elements = root.select("div[typeof=BreadcrumbList]");
        elements = elements.first().getElementsByTag("span");
        articolo.codice_path = codice_path;
        articolo.codice_nome = index.getById(codice_path).name;
        articolo.articolo_numero = elements.get(4).text();

        articolo.sezione_libro_id = elements.get(2).text();
        articolo.sezione_libro_caption = root.select(".breadcrumb a.tooltipped").get(0).attr("data-tooltip");
        articolo.sezione_titolo_id = elements.get(3).text();
        articolo.sezione_titolo_caption = root.select(".breadcrumb a.tooltipped").get(1).attr("data-tooltip");

        articolo.articolo_titolo = root.select("h1 span.Articolo__header__rubrica").text();
        articolo.articolo_testo = root.select("noscript").get(1).text();
        // articolo.articolo_html = root.select("collegamenti").first().attr("testo"); // Conserva gli escape
        articolo.articolo_html = root.select("meta[property=og:description]").first().attr("content");

        articolo.link_self = debase(root.select("head link[rel=canonical]").attr("href"));
        articolo.link_prec = debase(root.select("a#articolo-precedente").attr("href"));
        articolo.link_succ = debase(root.select("a#articolo-successivo").attr("href"));
        articolo.articolo_endpoint = articolo.link_self.substring(articolo.link_self.lastIndexOf("/"));

        // Sentenze di Giurisprudenza
        Elements sentenze = root.select("div.section div.container");
        if (sentenze != null && sentenze.first() != null) {
            sentenze = sentenze.first().getElementsByClass("card-content black-text small-font");
            for (int sn = 0; sn < sentenze.size(); ++sn) {
                Sentenza sentenza = new Sentenza();
                sentenza.cassazione = sentenze.get(sn).select("span.card-title").text();
                sentenza.numero = sentenze.get(sn).select("span.card-subtitle").text();
                sentenza.contenuto = sentenze.get(sn).select("p").text();

                // SuperLog.log(TAG + "@Sentenza", sentenza.toString());
                articolo.articolo_sentenze.add(sentenza);
            }
        }

        // Argomenti
        Elements argomenti = root.select("div.colore-principale div.container ul");
        if (argomenti != null && argomenti.first() != null) {
            argomenti = argomenti.first().getElementsByTag("a");
            for (Element argomento : argomenti) {
                articolo.articolo_argomenti.add(argomento.text());
            }
        }

        // Correlati
        Elements correlati = root.select("li.Correlati__li a.Correlati__link");
        for (Element correlato : correlati) {
            articolo.articolo_correlati.add(correlato.text() + "#" + debase(correlato.attr("href")));
        }

        return articolo;
    }

    public void store_article(Articolo articolo, String article_html) {
        FileManager.mkDir(TAG + articolo.codice_path);
        FileManager.mkDir(TAG + "/html" + articolo.codice_path);
        Jsonable.toFile(TAG + articolo.link_self + ".json", articolo, true);
        if (article_html != null && !article_html.isEmpty()) {
            SuperFileText.setText(TAG + "/html" + articolo.link_self + ".html", article_html);
        }
        SuperLog.log(TAG + "_OK", articolo.toString());
    }

    /**
     * Scorre tutti gli articoli a partire da quello indicato
     *
     * @param codice_path e.g., /codici/codice-della-strada
     * @param articolo_endpoint_from e.g., /articolo-1
     * @param offline indica se recuperare il testo dagli html o online
     */
    public void batch_store(String codice_path, String articolo_endpoint_from, boolean offline) {
        Articolo articolo = new Articolo();
        articolo.codice_path = codice_path;
        articolo.articolo_endpoint = articolo_endpoint_from;
        articolo.link_succ = codice_path + articolo_endpoint_from;
        while (!articolo.link_succ.isEmpty()) {
            String html = load_article_html(articolo.link_succ, offline);
            articolo = get_article(codice_path, html);
            store_article(articolo, html);
        }
    }

    public static void main(String[] args) {
        Scraper_LexScripta ls = new Scraper_LexScripta();
    }

}
