package com.fscrates.client;

import com.fscrates.client.media.DefaultMedia;
import com.fscrates.config.MediaGuide;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init() {
        // Se extrae la media por defecto ya, en un hilo de fondo, para que la
        // primera apertura de una caja no tenga que hacerlo en ese momento.
        DefaultMedia.warmUp();

        // Se deja la chuleta de formatos de video en la carpeta de config. Va
        // tambien en el cliente porque en un solo jugador no hay arranque de
        // servidor dedicado, y es justo ahi donde se prueban los videos.
        MediaGuide.write();
    }
}
