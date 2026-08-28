# Fantastic Claims 7.9.1 — Proyecto Gradle (Forge 1.20.1 / Java 17)

Proyecto de desarrollo reconstruido a partir de `Fantastic Claims-7.9.1.jar`. Compila sin errores y
genera un JAR equivalente al original.

## Entorno

| Componente | Versión |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.2.0 |
| Java | 17 |
| Mappings | `official` 1.20.1 (nombres Mojang) |
| Gradle wrapper | 8.1.1 |
| ForgeGradle | 6.0.x |
| MixinGradle | 0.7.x |

## Compilar

```bash
./gradlew build
```

El JAR queda en `build/libs/claimblocks-7.9.1.jar`.

Otros comandos útiles:

```bash
./gradlew compileJava    # solo compilar
./gradlew runClient      # cliente de pruebas
./gradlew runServer      # servidor de pruebas
```

Si tu `java -version` por defecto no es 17, exporta el JDK antes de compilar:

```bash
export JAVA_HOME=/ruta/al/jdk-17
```

## Estructura

```
src/main/java/com/claimblocks/
├── ClaimBlocks.java          utilidades de ItemStack / lore
├── ClaimBlocksMod.java       punto de entrada @Mod, geometría de zonas
├── chat/                     enrutado de respuestas de chat de los menús
├── client/                   render del contorno y caché de bordes del cliente
├── command/                  /fsclaim, /fsclaimadmin
├── data/                     Claim, ClaimManager, flags, tiers, config, persistencia JSON
├── event/                    protección de bloques y entidades, efectos pasivos, tracker
├── gui/                      menús de cofre (jugador, admin, partículas, miembros)
├── item/                     items de piedra de protección
├── mixin/                     8 mixins de protección (tolvas, fluidos, explosiones, chat…)
├── net/                      canal y paquete de bordes
├── render/                   borde de partículas
└── util/                     PlayerLookup, guardas de borde/decoración/explosión

src/main/resources/
├── META-INF/mods.toml
├── claimblocks.mixins.json
├── pack.mcmeta
└── assets/claimblocks/{lang,models/item}
```

## Notas sobre la reconstrucción

- Descompilado con [Vineflower](https://github.com/Vineflower/vineflower) 1.10.1.
- Los nombres SRG de Minecraft (`m_5776_`, `f_13036_`, …) se convirtieron a nombres oficiales
  Mojang (`isClientSide`, `TRAPDOORS`, …) cruzando `mcp_config-1.20.1/joined.tsrg` con los
  mappings oficiales de Mojang de 1.20.1. Las 288 referencias SRG del JAR quedaron mapeadas
  al 100%, por eso el proyecto usa `mapping_channel=official`.
- Se restauraron los genéricos que el descompilador pierde por borrado de tipos
  (`List<Claim>`, `Set<UUID>`, `LiteralArgumentBuilder<CommandSourceStack>`, …).
- Los casts `this` de los mixins se escribieron en la forma válida en tiempo de compilación
  `(Tipo)(Object)this`.
- El `refmap` no se versiona: MixinGradle lo regenera en cada build (el generado coincide con el
  del JAR original).
