# YAWP 0.6.3-beta1 — parche de compatibilidad con Mohist 1.20.1

`yawp-1.20.1-forge-0.6.3-beta1-mohistfix.jar` es el jar original de
[Yet Another World Protector](https://github.com/Z0rdak/Yet-Another-World-Protector)
con **un solo cambio binario**, necesario para que arranque en un servidor híbrido Mohist.

## El problema

Mohist (CraftBukkit) reescribe `FrostWalkerEnchantment.onEntityMoved`: **borra** la llamada
`level.setBlockAndUpdate(...)` y la sustituye por `CraftEventFactory.handleBlockFormEvent(...)`
para poder disparar el `EntityBlockFormEvent` de Bukkit.

`FrostWalkerEnchantmentMixin` de YAWP inyecta exactamente en esa llamada:

```java
@Inject(method = "onEntityMoved",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(...)Z"),
        cancellable = true)
```

En Mohist el punto de inyección ya no existe. Como `yawp.mixins.json` declara
`injectors.defaultRequire = 1` y `required: true`, Mixin lo trata como error fatal:

```
Mixin apply failed yawp.mixins.json:FrostWalkerEnchantmentMixin -> net.minecraft.world.item.enchantment.FrostWalkerEnchantment:
InvalidInjectionException: Injection validation failed: Callback method onEntityMoved(...)V
expected 1 invocation(s) but 0 succeeded. Scanned 1 target(s).
```

La clase queda sin cargar y el arranque muere en `Bootstrap`:

```
Caused by: java.lang.NoClassDefFoundError: net/minecraft/world/item/enchantment/FrostWalkerEnchantment
	at net.minecraft.core.registries.BuiltInRegistries.m_257853_(BuiltInRegistries.java:150)
	at net.minecraft.server.Bootstrap.m_135870_(Bootstrap.java:55)
	at net.minecraft.server.Main.main(Main.java:161)
```

Esto ocurre **antes** de que exista el manejador de crash reports de Forge, por eso el
servidor se cierra sin generar ningún archivo en `crash-reports/`.

## El parche

En `de/z0rdak/yawp/mixin/FrostWalkerEnchantmentMixin.class` se reescribió la anotación a:

```java
@Inject(method = "onEntityMoved", at = @At(value = "HEAD"), cancellable = true)
```

El handler solo lee el parámetro `pos` del método (la posición de la entidad) y llama a
`info.cancel()`, así que `HEAD` es equivalente en comportamiento: la flag
`no_walker_freeze` sigue funcionando.

Ningún otro archivo de código cambia. Los otros 15 mixins de YAWP se aplican sin
problemas en Mohist.

## Verificación

Probado en Mohist 1.20.1 build 471 (Forge 47.4.13, Java 17):

- Arranque limpio: `Done (6.8s)!`, apagado limpio, código de salida 0
- 0 errores `FATAL`, incluso con comprobación estricta (`-Dmixin.debug.countInjections=true`)
- `/yawp help` y `/yawp dim ...` responden correctamente

Sin el parche: código de salida 1, sin crash report.

## Notas

- Ninguna opción de arranque evita el fallo. En concreto,
  `-Dmixin.env.ignoreRequired=true` **no** funciona: el servidor sigue muriendo con el
  mismo `NoClassDefFoundError`.
- Al actualizar YAWP hay que volver a aplicar el parche, salvo que el fallo se corrija
  aguas arriba.
