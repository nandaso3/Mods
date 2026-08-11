package com.fscrates.client.media;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte enlaces de "compartir" en enlaces de descarga directa.
 *
 * El motivo: cuando alguien sube un video a Google Drive y pulsa Compartir, lo
 * que copia es esto:
 *
 *   https://drive.google.com/file/d/1AbCdEf.../view?usp=sharing
 *
 * Ese enlace NO devuelve el archivo, devuelve una pagina web con el reproductor
 * de Drive. Pedirle al usuario que se construya a mano el enlace de descarga es
 * absurdo cuando la conversion es sacar el identificador y montar la otra
 * direccion, que es lo que se hace aqui.
 *
 * Antes, pegar el enlace de compartir daba una pantalla en negro sin mas
 * explicacion. Ahora se acepta el enlace que la gente tiene de verdad.
 *
 * Ojo con una cosa que esto NO puede resolver: el archivo tiene que estar
 * compartido como "cualquier persona con el enlace". Si esta restringido, Drive
 * responde con una pagina de permisos y no hay conversion que lo salve.
 */
public final class MediaUrls {
    /** .../file/d/<ID>/view  y  .../file/d/<ID>/preview */
    private static final Pattern DRIVE_FILE = Pattern.compile(
        "drive\\.google\\.com/file/d/([a-zA-Z0-9_-]{10,})"
    );

    /** .../open?id=<ID>, .../uc?id=<ID>, .../download?id=<ID> */
    private static final Pattern DRIVE_ID_PARAM = Pattern.compile(
        "drive(?:usercontent)?\\.google\\.com/[^?]*\\?(?:[^&]*&)*id=([a-zA-Z0-9_-]{10,})"
    );

    /** github.com/<usuario>/<repo>/blob/<rama>/<ruta> */
    private static final Pattern GITHUB_BLOB = Pattern.compile(
        "github\\.com/([^/]+)/([^/]+)/blob/(.+)"
    );

    private MediaUrls() {
    }

    /**
     * Devuelve el enlace que se puede descargar. Si no hay nada que convertir se
     * devuelve tal cual.
     */
    public static String normalize(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // ---- Google Drive
        Matcher file = DRIVE_FILE.matcher(trimmed);
        if (file.find()) {
            return driveDownload(file.group(1));
        }
        if (lower.contains("drive.google.com") || lower.contains("driveusercontent.google.com")) {
            Matcher param = DRIVE_ID_PARAM.matcher(trimmed);
            if (param.find()) {
                return driveDownload(param.group(1));
            }
        }
        // drive.usercontent.google.com/download?id=... ya es el bueno: se deja.

        // ---- Dropbox: el enlace de compartir acaba en dl=0, que abre la vista
        // previa. Con dl=1 devuelve el archivo.
        if (lower.contains("dropbox.com")) {
            if (lower.contains("dl=0")) {
                return trimmed.replace("dl=0", "dl=1").replace("DL=0", "dl=1");
            }
            if (!lower.contains("dl=1") && !lower.contains("raw=1")) {
                return trimmed + (trimmed.contains("?") ? "&" : "?") + "dl=1";
            }
            return trimmed;
        }

        // ---- GitHub: /blob/ es la pagina, raw.githubusercontent.com es el archivo.
        Matcher blob = GITHUB_BLOB.matcher(trimmed);
        if (blob.find()) {
            return "https://raw.githubusercontent.com/" + blob.group(1) + "/" + blob.group(2)
                + "/" + blob.group(3);
        }

        return trimmed;
    }

    private static String driveDownload(String id) {
        // Este host es el que sirve el archivo. El clasico uc?export=download
        // responde con la pagina de confirmacion en archivos grandes.
        return "https://drive.usercontent.google.com/download?id=" + id + "&export=download";
    }

    /** true si el enlace se ha reescrito, solo para poder avisarlo en el log. */
    public static boolean wasRewritten(String original, String normalized) {
        return original != null && normalized != null && !original.trim().equals(normalized);
    }
}
