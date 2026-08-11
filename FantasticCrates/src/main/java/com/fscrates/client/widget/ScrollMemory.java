package com.fscrates.client.widget;

/**
 * Recuerda por donde iba una lista y que fila estaba elegida.
 *
 * Hace falta porque las pantallas del editor se reconstruyen enteras cada vez
 * que cambia algo (rebuildWidgets): los widgets viejos se tiran y se crean
 * nuevos. Un ScrollSelector recien creado empieza arriba, asi que al elegir un
 * item la lista saltaba al principio y perdias de vista lo que acababas de
 * tocar. Con listas largas era imposible trabajar.
 *
 * La solucion es que la posicion no viva en el widget (que es de usar y tirar)
 * sino en la pantalla, que sobrevive a la reconstruccion. El widget lee de aqui
 * al recibir los items y escribe aqui cada vez que se mueve.
 *
 * La fila elegida se guarda por identidad (el objeto en si, no su posicion),
 * porque el orden de la lista puede cambiar entre reconstrucciones: en el pool
 * las recompensas se reordenan por rareza, y un indice guardado apuntaria a
 * otra cosa.
 */
public final class ScrollMemory {
    /** Primera fila visible. */
    int scroll;

    /** La fila elegida, guardada por identidad. */
    Object selected;

    /** Vuelve al principio y olvida la seleccion. */
    public void clear() {
        this.scroll = 0;
        this.selected = null;
    }
}
