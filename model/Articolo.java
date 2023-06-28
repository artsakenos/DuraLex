/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tk.artsakenos.ultragraph.duralex.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import tk.artsakenos.iperunits.file.FileManager;
import tk.artsakenos.iperunits.serial.Jsonable;
import tk.artsakenos.iperunits.string.SuperString;

import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import static tk.artsakenos.iperunits.file.SuperLog.log;
import static tk.artsakenos.ultragraph.duralex.DuralexNetwork.TAG;

/**
 * @author Andrea
 * @version Jul 15, 2020
 */
@SuppressWarnings("unused")
@JsonInclude(Include.NON_NULL)
public class Articolo extends Jsonable implements Comparable<Articolo> {

    public long id;                     // Serve per i grafi VIS. Viene aggiuto nel momento in cui serve.

    public String codice_nome;          // Codice della Strada, Legge, Decreto
    public String codice_path;          // /codici/codice-della-strada, /decreti/legge, decreti/decreto
    public String sezione_libro_id;
    public String sezione_libro_caption;
    public String sezione_titolo_id;
    public String sezione_titolo_caption;
    public String articolo_endpoint;    // /articolo-3, /1996/675_2
    public String articolo_numero;      // Art. 1, Art. 675.2
    public String articolo_titolo;
    public String articolo_testo;       // Il testo semplice, html stripped
    public String articolo_html;        // Il testo in html o con gli accapi (/n)
    public String articolo_bow;         // Il B.O.W. nel caso servisse un placeholder per le operazioni di IA
    public String articolo_validita;    // In Vigore dal al
    public String articolo_note;        // Note o Flag
    public String link_prec;
    public String link_self;            // Il link internet (senza baseurl) della risorsa
    public String link_succ;
    public List<String> articolo_argomenti = new LinkedList<>();
    public List<String> articolo_correlati = new LinkedList<>();
    public List<Sentenza> articolo_sentenze = new LinkedList<>();

    @Override
    public String toString() {
        String capTesto = articolo_testo == null ? articolo_html : articolo_testo;
        capTesto = SuperString.capLength(capTesto, 30).replaceAll("\n", " ");
        String capTitolo = SuperString.capLength(articolo_titolo, 30).replaceAll("\n", " ");
        return ""
                + "[" + codice_nome + " # " + articolo_endpoint + "] "
                + "(" + sezione_libro_id + "/" + sezione_titolo_id + ") "
                + "" + capTitolo + "::" + capTesto;
    }

    @Override
    public int compareTo(Articolo articolo) {
        return this.link_self.compareTo(articolo.link_self);
    }

    /**
     * Restituisce il json path del file. Crea la directory ospite se non
     * esiste!
     *
     * @param base la dir di base. Deve essere relativo (e.g., DuraLex).
     * @return The Json Path
     */
    public String pathJson(String base) {
        if (base == null || base.isEmpty()) {
            base = TAG;
        }
        String path = base + codice_path + articolo_endpoint + ".json";
        String dir = path.substring(0, path.lastIndexOf("/"));
        if (!FileManager.fileExists(dir)) {
            FileManager.mkDir(dir);
        }
        return path;
    }

    public String pathHtml(String base) {
        if (base == null || base.isEmpty()) {
            base = TAG;
        }
        String path = base + "/html" + codice_path + articolo_endpoint + ".html";
        String dir = path.substring(0, path.lastIndexOf("/"));
        if (!FileManager.fileExists(dir)) {
            FileManager.mkDir(dir);
        }
        return path;
    }

    public static void showArticoli(TreeSet<Articolo> articoli) {
        if (articoli == null) {
            log(TAG, "Null Articoli!");
            return;
        }
        for (Articolo articolo : articoli) {
            log(TAG, articolo.toString());
        }
    }
}
