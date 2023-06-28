/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tk.artsakenos.ultragraph.duralex.model;

import tk.artsakenos.iperunits.string.SuperString;

/**
 * @version Jul 15, 2020
 * @author Andrea
 */
public class Sentenza {

    public String cassazione;
    public String numero;
    public String contenuto;

    @Override
    public String toString() {
        return "[" + cassazione + " #" + numero + "] " + SuperString.capLength(contenuto, 50);
    }

}
