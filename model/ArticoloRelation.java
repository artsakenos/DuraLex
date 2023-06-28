package tk.artsakenos.ultragraph.duralex.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Questa classe serve per il network per istanziare una relazione tra due articoli.
 * Il peso è il numero di volte che un articolo linka un altro.
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class ArticoloRelation {
    private long id;
}
