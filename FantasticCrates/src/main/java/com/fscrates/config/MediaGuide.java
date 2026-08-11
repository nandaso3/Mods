package com.fscrates.config;

import com.fscrates.FSCrates;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Escribe config/fscrates/_LEEME_VIDEOS.txt con los formatos que se soportan y
 * como preparar un video para que se vea bien.
 *
 * Existe porque el reproductor va con JCodec (un decodificador escrito en Java,
 * sin librerias nativas) y eso limita los formatos que acepta. Los dos fallos
 * tipicos son exportar en HEVC o en 10 bits, que es justo lo que hacen por
 * defecto muchos editores y moviles: el archivo parece un MP4 normal y corriente,
 * asi que sin esta chuleta es imposible adivinar por que no se ve.
 *
 * Tambien lleva los ajustes exactos con los que se preparo el video que viene
 * incluido, para quien quiera el mismo acabado en los suyos. Ojo con esto: el mod
 * NO reconvierte nada, reproduce el archivo tal cual se le da. Estos ajustes hay
 * que aplicarlos uno mismo antes de subir el video.
 *
 * Se reescribe en cada arranque para que no se quede una version vieja si en
 * futuras versiones cambia lo que se soporta.
 */
public final class MediaGuide {
    private static final String CONTENT =
        "===============================================================================\n"
        + " FANTASTIC CRATES - VIDEOS, IMAGENES Y MUSICA\n"
        + "===============================================================================\n"
        + "\n"
        + "Archivo generado por el mod. No hace falta editarlo: se reescribe en cada\n"
        + "arranque. Es solo una chuleta.\n"
        + "\n"
        + "\n"
        + "-------------------------------------------------------------------------------\n"
        + " 1. QUE PUEDES PONER\n"
        + "-------------------------------------------------------------------------------\n"
        + "\n"
        + "En la pestana VIDEOS del editor puedes poner un enlace directo o un archivo\n"
        + "local. Sirven tres cosas:\n"
        + "\n"
        + "  VIDEO    .mp4 / .mov / .m4v con video H.264 de 8 bits\n"
        + "  IMAGEN   .png / .jpg / .jpeg / .gif (el GIF se muestra fijo, no animado)\n"
        + "  MUSICA   .mp3 / .ogg / .wav\n"
        + "\n"
        + "El tipo se detecta mirando el contenido del archivo, no la extension, asi que\n"
        + "los enlaces sin extension (Google Drive, Dropbox y similares) tambien valen.\n"
        + "\n"
        + "El video y la musica son independientes: lo que dejes vacio usa lo que trae el\n"
        + "mod. Puedes poner tu video y quedarte con la musica del mod, o al reves. Vacio\n"
        + "significa \"la del mod\", no \"que no suene\": para el silencio esta el boton de\n"
        + "volumen de la propia pantalla.\n"
        + "\n"
        + "El audio que lleve dentro el video NUNCA se oye. Solo se usa su imagen, y de\n"
        + "hecho la pista de sonido se aparta antes de reproducirlo (ver punto 2). La\n"
        + "unica cosa que suena es la musica.\n"
        + "\n"
        + "\n"
        + "-------------------------------------------------------------------------------\n"
        + " 2. FORMATOS DE VIDEO: LO QUE FUNCIONA Y LO QUE NO\n"
        + "-------------------------------------------------------------------------------\n"
        + "\n"
        + "El decodificador es JCodec, en Java puro, para no tener que meter librerias\n"
        + "nativas de 100 MB por sistema operativo. A cambio solo entiende H.264:\n"
        + "\n"
        + "  SI   H.264 de 8 bits, perfil Baseline, Main o High\n"
        + "  SI   H.264 con B-frames (el reproductor reordena los fotogramas)\n"
        + "  NO   H.264 de 10 bits  -> en el log: \"Unsupported h264 feature: High bit depth\"\n"
        + "  NO   HEVC / H.265      -> en el log: \"Not a video track\"\n"
        + "  NO   WebM (VP8 / VP9), AV1, VP6\n"
        + "\n"
        + "Los dos NO importantes son 10 bits y HEVC, porque son lo que sale por defecto\n"
        + "de muchos moviles y editores. Si un video no se ve, mira el log del cliente:\n"
        + "el mod dice exactamente cual de estos casos es.\n"
        + "\n"
        + "Sobre el audio del video: da igual si lo lleva o no. El decodificador se\n"
        + "atragantaba con ciertas pistas de audio y no abria el archivo, aun estando el\n"
        + "video perfecto (pantalla en negro sin ninguna pista de por que). Ahora la pista\n"
        + "de sonido se aparta antes de abrirlo, sin recomprimir ni recortar el archivo.\n"
        + "No hace falta que quites el audio tu, aunque si lo quitas al exportar el\n"
        + "archivo pesa menos: eso es para lo que sirve el -an de la receta.\n"
        + "\n"
        + "\n"
        + "-------------------------------------------------------------------------------\n"
        + " 3. COMO PREPARAR UN VIDEO (ffmpeg)\n"
        + "-------------------------------------------------------------------------------\n"
        + "\n"
        + "IMPORTANTE: el mod reproduce tu archivo TAL CUAL. No lo recomprime ni le\n"
        + "mejora la calidad. Si quieres el mismo acabado que el video incluido, tienes\n"
        + "que aplicar estos ajustes tu, antes de subirlo.\n"
        + "\n"
        + "Receta completa (es la misma con la que se preparo el video por defecto):\n"
        + "\n"
        + "  ffmpeg -i ENTRADA -an \\\n"
        + "    -vf \"scale=1920:1080:flags=lanczos+accurate_rnd+full_chroma_int:param0=2,\\\n"
        + "deband=1thr=0.012:2thr=0.012:3thr=0.012:range=16:blur=1,\\\n"
        + "unsharp=luma_msize_x=3:luma_msize_y=3:luma_amount=0.4:chroma_amount=0.1\" \\\n"
        + "    -c:v libx264 -profile:v main -level 4.2 -crf 17 -preset veryslow \\\n"
        + "    -bf 0 -coder 0 -refs 1 -sc_threshold 0 -g 10 -keyint_min 10 \\\n"
        + "    -aq-mode 3 -aq-strength 1.1 -pix_fmt yuv420p -movflags +faststart \\\n"
        + "    SALIDA.mp4\n"
        + "\n"
        + "OJO en Windows: las barras \\ del final de cada linea son para la terminal de\n"
        + "Linux y Mac. En el CMD de Windows hay que escribir todo el comando seguido en\n"
        + "UNA sola linea, quitando las barras.\n"
        + "\n"
        + "Version corta, si solo quieres que se vea sin complicarte (vale en todos los\n"
        + "sistemas porque ya va en una linea):\n"
        + "\n"
        + "  ffmpeg -i ENTRADA -an -c:v libx264 -crf 20 -pix_fmt yuv420p -g 10 SALIDA.mp4\n"
        + "\n"
        + "Que hace cada parte, por si quieres tocarla:\n"
        + "\n"
        + "  -pix_fmt yuv420p   8 bits. IMPRESCINDIBLE, sin esto no se ve.\n"
        + "  -an                Quita el audio: el video no lo usa, la musica va aparte.\n"
        + "  scale=1920:1080    Ponlo a la resolucion de TU pantalla, no mas (ver punto 4).\n"
        + "  flags=lanczos      Reescalado nitido. param0=2 lo suaviza un poco para que\n"
        + "                     no salgan halos en los bordes.\n"
        + "  deband             Quita las bandas de los degradados (cielos, sombras). Si\n"
        + "                     tu video no tiene degradados, quitalo y pesara menos.\n"
        + "  unsharp 0.4        Enfoque suave. Subirlo mucho da un aspecto artificial.\n"
        + "  -crf 17            Calidad. Mas bajo = mejor y mas pesado. 17-20 va bien.\n"
        + "  -g 10              Un fotograma clave cada 10. Permite repartir el trabajo\n"
        + "                     entre varios hilos, o sea que va mas fluido.\n"
        + "  -bf 0 -coder 0     Se decodifican mas rapido. Con B-frames tambien funciona,\n"
        + "  -refs 1            solo cuesta algo mas de CPU.\n"
        + "  -preset veryslow   Solo afecta a lo que tarda ffmpeg, no a la reproduccion.\n"
        + "\n"
        + "\n"
        + "-------------------------------------------------------------------------------\n"
        + " 4. RESOLUCION Y FPS: NO TE PASES\n"
        + "-------------------------------------------------------------------------------\n"
        + "\n"
        + "Poner mas resolucion de la que tiene tu pantalla NO se ve mejor, y cuesta el\n"
        + "doble o el cuadruple de CPU. Con una pantalla 1080p, un video 1080p es lo\n"
        + "optimo: se dibuja pixel a pixel, sin reescalar ni suavizar nada.\n"
        + "\n"
        + "Medido en la tuberia real del mod (decodificado en paralelo + conversion),\n"
        + "en un equipo de 8 nucleos, son los fotogramas por segundo que se sostienen:\n"
        + "\n"
        + "  1080p    50 fps    de sobra para 30 fps, 67% de margen  <- recomendado\n"
        + "  1440p    23 fps    NO llega a 30 fps: se veria a tirones\n"
        + "  4K        no da\n"
        + "\n"
        + "O sea que 1440p y 4K no son \"mas calidad\", son un video que se atasca. El\n"
        + "techo esta en 1080p y en equipos mas modestos que el de la medida el margen\n"
        + "sera menor.\n"
        + "\n"
        + "De los fps: pon los que tenga el original. Reencodear un video de 30 fps a 60\n"
        + "no lo hace mas fluido, solo duplica fotogramas y el peso.\n"
        + "\n"
        + "Y el limite de verdad es el archivo de origen. Si descargaste un video ya muy\n"
        + "comprimido, subirle el CRF no le devuelve el detalle que perdio: solo conserva\n"
        + "mejor sus propios defectos.\n"
        + "\n"
        + "\n"
        + "-------------------------------------------------------------------------------\n"
        + " 5. LO QUE EL MOD APLICA SOLO, A TODOS LOS VIDEOS\n"
        + "-------------------------------------------------------------------------------\n"
        + "\n"
        + "Esto no hay que configurarlo y vale igual para el video incluido y para los\n"
        + "tuyos, vengan de un enlace o de un archivo:\n"
        + "\n"
        + "  - Color segun los metadatos del archivo. Se lee del propio video que matriz\n"
        + "    (BT.601 / BT.709 / BT.2020) y que rango (limitado o completo) lleva, y se\n"
        + "    convierte con eso. Si el archivo no lo declara se deduce por la\n"
        + "    resolucion. Esto es lo que da el contraste y los colores correctos: con\n"
        + "    una conversion generica los negros salen grises y la imagen parece tener\n"
        + "    menos definicion de la que tiene.\n"
        + "  - Color interpolado. En 4:2:0 hay una muestra de color por cada cuatro de\n"
        + "    brillo; en vez de repetirla en bloques de 2x2 se interpola, asi los bordes\n"
        + "    de color no salen escalonados.\n"
        + "  - Dibujado 1:1 sin suavizar cuando el video mide justo el hueco de la\n"
        + "    pantalla, para que salga exactamente igual de nitido que el archivo.\n"
        + "  - Enfoque automatico solo si hay que ampliar (por ejemplo un 720p en una\n"
        + "    pantalla 1080p), con la fuerza justa y topada para que no salgan halos. Si\n"
        + "    no hay que ampliar, el fotograma no se toca.\n"
        + "  - Reordenado por marca de tiempo, que es lo que evita que la imagen pegue\n"
        + "    saltos hacia atras en videos con B-frames.\n"
        + "  - Decodificado repartido entre varios hilos, dejando siempre nucleos libres\n"
        + "    para el juego.\n"
        + "\n"
        + "Lo que el mod NO hace es recomprimir tu video ni inventarle detalle. Si el\n"
        + "archivo viene borroso o muy comprimido, se vera asi: la calidad de partida la\n"
        + "pones tu al exportarlo (punto 3).\n"
        + "\n"
        + "\n"
        + "-------------------------------------------------------------------------------\n"
        + " 6. CACHE\n"
        + "-------------------------------------------------------------------------------\n"
        + "\n"
        + "Todo lo que se descarga de un enlace se guarda en:\n"
        + "\n"
        + "  config/fscrates/cache/\n"
        + "\n"
        + "Cada jugador lo descarga UNA vez y de ahi en adelante lo reutiliza. Si cambias\n"
        + "el archivo que hay detras de un enlace sin cambiar el enlace, borra esa carpeta\n"
        + "para forzar que se baje otra vez.\n"
        + "\n"
        + "\n"
        + "-------------------------------------------------------------------------------\n"
        + " 7. SI ALGO NO SE VE\n"
        + "-------------------------------------------------------------------------------\n"
        + "\n"
        + "Mira el log del cliente (logs/latest.log) y busca [FSCrates]. El mod dice si\n"
        + "es 10 bits, si es HEVC, si es WebM o si no pudo descargar el archivo, con el\n"
        + "nombre del archivo que ha fallado.\n"
        + "\n"
        + "Comprobar como esta hecho un video:\n"
        + "\n"
        + "  ffprobe -v error -select_streams v:0 \\\n"
        + "    -show_entries stream=codec_name,profile,pix_fmt,width,height,avg_frame_rate \\\n"
        + "    -of default=nw=1 TUVIDEO.mp4\n"
        + "\n"
        + "Tiene que salir codec_name=h264 y pix_fmt=yuv420p. Si pone yuv420p10le es de\n"
        + "10 bits, y si pone hevc es H.265: en los dos casos hay que reconvertirlo.\n"
        + "\n"
        + "===============================================================================\n";

    private MediaGuide() {
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("fscrates").resolve("_LEEME_VIDEOS.txt");
    }

    /** Reescribe la guia. Si falla no pasa nada: es documentacion, no configuracion. */
    public static void write() {
        try {
            Path out = file();
            Files.createDirectories(out.getParent());
            Files.write(out, CONTENT.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            FSCrates.LOGGER.warn("[FSCrates] No se pudo escribir _LEEME_VIDEOS.txt: {}", e.toString());
        }
    }
}
