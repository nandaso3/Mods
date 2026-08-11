package com.fsrecipes;

/**
 * Los dos tipos de baneo que soporta Fantastic Recipes.
 *
 * <p>Un item solo puede estar en UNO de los dos modos a la vez (o en ninguno).
 * {@code null} siempre representa "sin baneo".
 */
public enum BanMode {
   /**
    * Solo la receta. El item deja de poder craftearse/cocinarse/forjarse, pero se
    * sigue pudiendo tener, usar y obtener por otras vias (loot, comandos, creativo...).
    */
   RECIPE(1, "receta", "§e[ REC]", "§eSolo receta", "§ereceta baneada"),

   /**
    * Blacklist total. Quita la receta Y ademas prohibe el item: no se puede tener en
    * el inventario, usar, recoger del suelo ni sacar del creativo.
    */
   ITEM(2, "item", "§c[ITEM]", "§cItem completo", "§citem baneado");

   /** Etiqueta que se muestra en la GUI para un item sin baneo. */
   public static final String TAG_NONE = "§7[ -- ]";

   private final int id;
   private final String key;
   private final String tag;
   private final String display;
   private final String verb;

   BanMode(int id, String key, String tag, String display, String verb) {
      this.id = id;
      this.key = key;
      this.tag = tag;
      this.display = display;
      this.verb = verb;
   }

   /** Id numerico usado en la red y en persistencia. Nunca 0 (0 = sin baneo). */
   public int id() {
      return this.id;
    }

   /** Clave usada en comandos y en el JSON de config. */
   public String key() {
      return this.key;
   }

   /** Etiqueta corta para las listas de la GUI (ancho fijo). */
   public String tag() {
      return this.tag;
   }

   /** Nombre legible para botones y mensajes. */
   public String display() {
      return this.display;
   }

   /** Texto para mensajes tipo "diamond ahora tiene la receta baneada". */
   public String verb() {
      return this.verb;
   }

   public static BanMode byId(int id) {
      for (BanMode m : values()) {
         if (m.id == id) {
            return m;
         }
      }
      return null;
   }

   public static BanMode byKey(String key) {
      if (key != null) {
         for (BanMode m : values()) {
            if (m.key.equalsIgnoreCase(key)) {
               return m;
            }
         }
      }
      return null;
   }

   /** Convierte un modo (posiblemente {@code null}) a su id de red. */
   public static int idOf(BanMode mode) {
      return mode == null ? 0 : mode.id;
   }

   public static String tagOf(BanMode mode) {
      return mode == null ? TAG_NONE : mode.tag;
   }
}
