package tk.artsakenos.ultragraph.duralex;

import org.jgrapht.graph.DirectedWeightedPseudograph;
import tk.artsakenos.iperunits.form.SuperColor;
import tk.artsakenos.iperunits.serial.Jsonable;
import tk.artsakenos.ultragraph.duralex.model.Articolo;
import tk.artsakenos.ultragraph.duralex.model.ArticoloRelation;
import tk.artsakenos.ultragraph.vis.VisEdge;
import tk.artsakenos.ultragraph.vis.VisGraph;
import tk.artsakenos.ultragraph.vis.VisNode;

import java.awt.*;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public class DuralexNetwork extends DirectedWeightedPseudograph<Articolo, ArticoloRelation> implements Serializable {

    private static final Logger logger = Logger.getLogger(DuralexNetwork.class.getName());

    public static final String TAG = "DuraLexNetwork";

    public DuralexNetwork() {
        super(ArticoloRelation.class);
    }

    public void loadNetwork() {
        final String path = "./working_dir/DuraLex_All.json";
        List<Articolo> articolo_list = Jsonable.fromFile(path, Articolo.class, "", "");
        TreeMap<String, Articolo> articoli = new TreeMap<>();
        int articolo_index = 0;
        for (Articolo articolo : articolo_list) {
            articolo.id = ++articolo_index;
            // SuperFileText.append("ArticleID.csv", articolo.id + "\t" + articolo.link_self + "\n");
            articoli.put(articolo.link_self, articolo);
            addVertex(articolo);
        }
        logger.log(Level.INFO, "Caricati {0} Articoli: ", articoli.size());

        int edge_id = 0;
        for (Articolo articolo : articolo_list) {
            for (String link_self : articolo.articolo_correlati) {
                link_self = link_self.split("#")[1];
                Articolo related = articoli.get(link_self);
                ArticoloRelation relation = new ArticoloRelation(++edge_id);
                if (related == null) {
                    // logger.log(Level.WARNING, "Articolo {0} broken.", articolo.link_self + " -->" + link_self);
                    // SuperFileText.append("Missing.log", link_self + "\n");
                    continue;
                }
                addEdge(articolo, related, relation);
            }
        }
        logger.log(Level.INFO, "Network built: {0}.", this.getType().toString());
    }


    public VisGraph toVisGraph() {

        int min_incoming_edges = 10;
        int max_incoming_edges = 20;
        String description = "Rete che mostra le relazioni tra gli articoli dei codici." +
                "Solo gli articoli che sono referenziati più di " + min_incoming_edges + " volte vengono presentati.";

        VisGraph vis = new VisGraph();

        // Parto dagli edges, se non voglio aggiungere nodi che non hanno connessioni.
        // .filter(invoice -> invoice.getAmount() > minAmount)
        for (ArticoloRelation relation : edgeSet()) {
            Articolo source = getEdgeSource(relation);
            Articolo target = getEdgeTarget(relation);

            // if (!source.codice_path.equals("/codici/codice-civile")) continue;
            // if (!target.codice_path.equals("/codici/codice-civile")) continue;

            int w_source = incomingEdgesOf(source).size();
            int w_target = incomingEdgesOf(target).size();
            if (w_source < min_incoming_edges) continue;
            if (w_source > max_incoming_edges) continue;

            VisNode n_source = new VisNode(source.id, w_source, source.id + "");
            VisNode n_target = new VisNode(target.id, w_target, target.id + "");
            Color c_source = SuperColor.getColor(source.codice_path.length());
            n_source.color = "#" + Integer.toHexString(c_source.getRGB()).substring(2);
            Color c_target = SuperColor.getColor(target.codice_path.length());
            n_target.color = "#" + Integer.toHexString(c_target.getRGB()).substring(2);

            vis.edges.add(new VisEdge(source.id, target.id, 1));
            vis.nodes.add(n_source);
            vis.nodes.add(n_target);

        }
        vis.description = description;
        vis.toFile();
        return vis;
    }

    public static void main(String[] args) throws IOException {
        DuralexNetwork network = new DuralexNetwork();
        network.loadNetwork();
        network.toVisGraph();
    }
}
