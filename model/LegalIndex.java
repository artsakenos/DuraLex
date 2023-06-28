/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tk.artsakenos.ultragraph.duralex.model;

import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import tk.artsakenos.iperunits.database.CSVConnector;
import tk.artsakenos.iperunits.file.FileManager;
import tk.artsakenos.iperunits.file.SuperLog;
import tk.artsakenos.iperunits.serial.Jsonable;
import tk.artsakenos.iperunits.string.SuperString;
import tk.artsakenos.iperunits.types.HashCounter;
import tk.artsakenos.iperunits.types.MapUtils;

import static tk.artsakenos.ultragraph.duralex.DuralexNetwork.TAG;

/**
 * LegalIndex consente di effettuare operazioni sugli articoli già archiviati.
 * Ad esempio, caricarli, estrapolare statistiche, salvarne formati compatti per
 * analisi ulteriori.
 * <p>
 * LegalIndex può ospitare tutte le "Collezioni", ovvero dei codici con il loro
 * nome, id, e i loro articoli. Ad esempio il Codice Civile, La Costituzione, O
 * i regi decreti del 1860.
 *
 * @author Andrea
 * @version Jul 22, 2020
 */
public class LegalIndex extends TreeSet<LegalIndex.Collezione> {

    public LegalIndex() {
        add(new Collezione("/codici/codice-civile", "Codice Civile"));
        add(new Collezione("/codici/codice-procedura-civile", "Codice di Procedura Civile"));
        add(new Collezione("/codici/disposizioni-attuazione-codice-procedura-civile", "Codice di Procedura Civile (Disposizioni)"));
        add(new Collezione("/codici/disposizioni-attuazione-codice-civile", "Codice Civile (Disposizioni)"));
        add(new Collezione("/codici/codice-penale", "Codice Penale"));
        add(new Collezione("/codici/codice-procedura-penale", "Codice di Procedura Penale"));
        add(new Collezione("/codici/disposizioni-attuazione-codice-penale", "Disposizioni di Attuazione del Codice Penale"));
        add(new Collezione("/codici/disp-att-cpp", "Codice di Procedura Penale (Disposizioni)"));
        add(new Collezione("/codici/codice-della-strada", "Codice della Strada"));
        add(new Collezione("/codici/costituzione", "Costituzione"));
    }

    /**
     * Carica tutti gli articoli da una cartella
     *
     * @param path Il path da cui caricare, (ad esempio TAG, TAG/codici,
     *             TAG/decreti/legge/1993
     * @return un TreeSet con tutti gli articoli
     */
    public TreeSet<Articolo> load_articoli(String path) {
        final String[] files = FileManager.getFilesRecursive(path);
        final TreeSet<Articolo> articoli = new TreeSet<>();
        for (String file : files) {
            if (file.endsWith(".json")) {
                Articolo articolo = Jsonable.fromFile(file, Articolo.class);
                articoli.add(articolo);
                // SuperLog.log(TAG, articolo.toString());
            }
        }
        SuperLog.log(TAG, "Caricati (" + path + ") " + articoli.size() + " articoli!");
        return articoli;
    }

    /**
     * Carica le informazioni sui decreti in collezione, non tutti gli articoli
     * perché sono troppi. Lo fa utilizzando solo i nomi dei file.
     *
     * @param tag  il tag root, e.g., TAG=DuraLex
     * @param path e.g., "/decreti/legge/"
     */
    public void load_decreti_information(String tag, String path) {
        String[] list = FileManager.getFilesRecursive(tag + path);
        int counter = 0;
        // output> 2000, 330 articoli, 4500 voci
        // Contiene anno -> Articoli
        TreeMap<String, TreeSet<String>> stats = new TreeMap<>();
        // Contiene anno -> Num totale items
        HashCounter<String> totalCounter = new HashCounter<>();
        for (String file : list) {
            file = file.replace("\\", "/");
            file = file.replace(tag + path, "");
            if (!file.contains(".json")) {
                // Solo l'anno.
                continue;
            }
            String anno = file.substring(0, file.indexOf("/"));
            String art = SuperString.getFrom(file, "/", "_");
            totalCounter.add(anno);

            TreeSet<String> annoStats = stats.get(anno);
            if (annoStats == null) {
                annoStats = new TreeSet<>();
                stats.put(anno, annoStats);
            }
            annoStats.add(art);

            // System.out.println(file + "> " + anno + "> " + art);
        }

        for (String anno : totalCounter.keySet()) {
            Collezione collezione = new Collezione(path + anno, anno);
            collezione.totalItems = (int) totalCounter.getItemCount(anno);
            TreeSet<String> articoli = stats.get(anno);
            String statistics = "";
            for (int i = 1; i <= articoli.size(); ++i) {
                if (!articoli.contains("n." + i)) {
                    statistics += " !N." + i;
                }
            }
            collezione.statistics = articoli.size() + " Articles, " + collezione.totalItems + " total items." + statistics;
            add(collezione);
        }

        MapUtils.printMap(stats, null);
        System.out.println("Total Counter: " + totalCounter.toString() + "\n");
    }

    public Collezione getById(String id) {
        for (Collezione codice : this) {
            if (codice.id.equals(id)) {
                return codice;
            }
        }
        return null;
    }

    /**
     * Utili per caricamento su siti o app mobile. Prevede anche la creazione di
     * un formato compatto. Ricordarsi di caricare le collezioni, e.g.,
     * <p>
     * index.load_decreti_information(TAG, "/decreti/legge");
     *
     * @param path Il path da cui caricare e su cui salvare i compact. Ad
     *             esempio TAG, TAG/codici, TAG/decreti/legge/1993.
     * @param from_all_json Carica dal file
     */
    public void saveJson(String path, boolean from_all_json) {
        TreeSet<Articolo> articoli = new TreeSet<>();

        if (from_all_json) {
            List<Articolo> articolo_list = Jsonable.fromFile(path, Articolo.class, "", "");
            articoli = new TreeSet<>(articolo_list);
        } else {
            articoli = load_articoli(path);
            path = path.replace("/", "_");
            Jsonable.toFile(path + "_All.json", articoli, true);
        }
        // Creazione del formato compatto.
        for (Articolo articolo : articoli) {
            Collezione codice = this.getById(articolo.codice_path);
            String libro_titolo_key = articolo.sezione_libro_id + "#" + articolo.sezione_titolo_id;
            String libro_titolo_val = articolo.sezione_libro_caption + "#" + articolo.sezione_titolo_caption;
            if (codice.libri_titoli != null && libro_titolo_key != null) {
                codice.libri_titoli.put(libro_titolo_key, libro_titolo_val);
            }
            articolo.articolo_testo = null;
            articolo.codice_nome = null;
            articolo.sezione_libro_caption = null;
            articolo.sezione_titolo_caption = null;
            if (articolo.articolo_argomenti.isEmpty()) {
                articolo.articolo_argomenti = null;
            }
            if (articolo.articolo_correlati.isEmpty()) {
                articolo.articolo_correlati = null;
            }
            if (articolo.articolo_sentenze.isEmpty()) {
                articolo.articolo_sentenze = null;
            }
            codice.articoli.add(articolo);
        }
        Jsonable.toFile(path + "_Compact.json", this, true);
    }

    public static LegalIndex loadJson(String path, boolean compact) {
        if (compact) {
            LegalIndex index = Jsonable.fromFile(path + "_Compact.json", LegalIndex.class);
            return index;
        } else {
            List<Articolo> articoli = Jsonable.fromFile(path + "_All.json", Articolo.class, "", "");
            LegalIndex index = new LegalIndex();
            for (Articolo articolo : articoli) {
                Collezione codice = index.getById(articolo.codice_path);
                String libro_titolo_key = articolo.sezione_libro_id + "#" + articolo.sezione_titolo_id;
                String libro_titolo_val = articolo.sezione_libro_caption + "#" + articolo.sezione_titolo_caption;
                codice.libri_titoli.put(libro_titolo_key, libro_titolo_val);
                codice.articoli.add(articolo);
            }
            return index;
        }
    }

    /**
     * Utile per un'elucubrazione di IA
     */
    public void saveCsv(String path) {
        final TreeSet<Articolo> articoli = load_articoli(path);
        final CSVConnector csv = new CSVConnector();
        for (Articolo articolo : articoli) {
            csv.add(
                    // articolo.link_self, // ID
                    articolo.codice_path + articolo.articolo_endpoint, // ID 
                    articolo.articolo_titolo,
                    articolo.articolo_testo,
                    articolo.codice_nome,
                    Arrays.toString(articolo.articolo_argomenti.toArray())
            );
        }
        path = path.replace("/", "_");
        csv.save(path + "_All.csv");
    }

    public static class Collezione implements Comparable<Collezione> {

        private int totalItems;
        private String statistics;

        public Collezione() {
        }

        public Collezione(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String id;
        public String name;
        public TreeSet<Articolo> articoli = new TreeSet<>();
        // Contiene libro#titolo, o solo libro
        public TreeMap<String, String> libri_titoli = new TreeMap<>();

        @Override
        public String toString() {
            return id + ": " + statistics;
        }

        @Override
        public int compareTo(Collezione collezione) {
            return id.compareTo(collezione.id);
        }

    }

    @Override
    public String toString() {
        String output = "";

        for (Collezione collezione : this) {
            output += collezione.toString() + "\n";
        }
        return output;
    }

    public static void main(String[] args) {
        LegalIndex index = new LegalIndex();
        // index.load_articoli(TAG + "/decreti/legge/1993");
        // index.load_decreti_information(TAG, "/decreti/legge/");
        // index.saveJson("./working_dir/DuraLex_All.json", true);

    }

}
