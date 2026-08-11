package com.fscrates.client;

import com.fscrates.client.media.DefaultMedia;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init() {
        // Se extrae la media por defecto ya, en un hilo de fondo, para que la
        // primera apertura de una caja no tenga que hacerlo en ese momento.
        DefaultMedia.warmUp();
    }
}
