# Fiw AntiCheat - Documentacion del mod

Fiw AntiCheat es un mod de verificacion de mods para servidores de Minecraft.
El servidor envia un reto al cliente cuando entra, el cliente responde con su
lista de mods, versiones, huellas de jar, marcadores estables y resource packs
activos/inactivos, y el servidor decide si el jugador puede continuar o debe ser
expulsado.

Este mod sirve para administrar servidores y modpacks, no para prometer una
proteccion imposible. Un cliente modificado puede mentir, pero Fiw AntiCheat
hace que los clientes normales sean verificables, bloquea mods no permitidos y
da a los admins una vista clara de lo que usa cada jugador.

## Compatibilidad

| Minecraft | Loader | Java | Jar |
|---|---|---|---|
| 1.20.1 | Fabric | 17 | `fiw-anticheat-fabric-1.20.1-*.jar` |
| 1.20.1 | MinecraftForge 47.x | 17 | `fiw-anticheat-forge-1.20.1-*.jar` |
| 1.21.1 | Fabric | 21 | `fiw-anticheat-fabric-1.21.1-*.jar` |
| 1.21.1 | NeoForge | 21 | `fiw-anticheat-neoforge-1.21.1-*.jar` |
| 1.21.11 | Fabric | 21 | `fiw-anticheat-fabric-1.21.11-*.jar` |
| 1.21.11 | NeoForge | 21 | `fiw-anticheat-neoforge-1.21.11-*.jar` |

NeoForge no publica un artefacto real para Minecraft 1.20.1 en su Maven actual,
asi que la version 1.20.1 de la familia Forge usa MinecraftForge 47.x.

## Instalacion

1. Elige el jar que coincide con el loader y la version de Minecraft del
   servidor.
2. Copia ese jar en la carpeta `mods/` del servidor.
3. Copia el mismo jar compatible en la carpeta `mods/` de cada cliente que deba
   entrar al servidor.
4. Instala las dependencias normales del loader:
   - Fabric necesita Fabric Loader y Fabric API.
   - Forge 1.20.1 necesita MinecraftForge 47.x.
   - NeoForge necesita la version de NeoForge correspondiente al target.
5. Arranca el servidor una vez para generar la configuracion.

La configuracion se crea en:

```text
config/fiw-mods-api/config.json
```

El nombre interno `fiw-mods-api` se mantiene por compatibilidad aunque el nombre
publico del mod sea Fiw AntiCheat.

Al actualizar, una config existente no se resetea. Los campos nuevos usan
defaults en memoria y aparecen en configs nuevas o cuando un comando existente
guarda la config.

## Funcionamiento

Al entrar un jugador:

1. El servidor congela temporalmente al jugador y envia un nonce de verificacion.
2. El cliente con Fiw AntiCheat responde con su lista de mods y el nonce.
3. El servidor comprueba firmas, ids baneados, resource packs configurados, modo
   blacklist/whitelist y excepciones.
4. Si pasa la verificacion, el jugador queda liberado.
5. Si falla, se expulsa al jugador con `kick_message`.
6. Si no responde a tiempo, se expulsa con `timeout_message`.

Si el jugador entra desde Floodgate/Geyser y `exemptions.floodgate_auto` esta
activado, el mod intenta eximirlo automaticamente.

## Configuracion basica

Ejemplo de configuracion generada:

```json
{
  "mode": "blacklist",
  "timeout_seconds": 10,
  "kick_message": "You are using a mod not allowed on this server.",
  "timeout_message": "Mod verification timed out. Please rejoin.",
  "detection": {
    "preset": "balanced",
    "monitor_only": false,
    "alert_staff": true,
    "block": {
      "cheat_clients": true,
      "xray": true,
      "fullbright": true,
      "freecam": true,
      "replay": false,
      "minimap": false,
      "autoclicker": true,
      "schematic_printer": true,
      "tweakeroo_utility": false,
      "damage_indicators": false,
      "zoom": false
    },
    "allow_overrides": [],
    "banned_mods": []
  },
  "resource_packs": {
    "log": true,
    "kick_on_banned": false,
    "banned_packs": [],
    "banned_fingerprints": []
  },
  "exemptions": {
    "floodgate_auto": true,
    "bypass_players": []
  },
  "profiling": {
    "enabled": true,
    "max_history": 200
  },
  "whitelist": {
    "require_all": true,
    "official_mods": []
  }
}
```

Opciones principales:

| Opcion | Uso |
|---|---|
| `mode` | `blacklist` permite todo salvo lo bloqueado. `whitelist` solo permite el modpack oficial capturado. |
| `timeout_seconds` | Tiempo maximo para que el cliente responda al reto. |
| `kick_message` | Mensaje cuando un jugador falla la verificacion. |
| `timeout_message` | Mensaje cuando el cliente no responde a tiempo. |
| `detection.preset` | Preset de categorias bloqueadas: `strict`, `balanced`, `lenient` o `custom`. |
| `detection.monitor_only` | Registra detecciones sin expulsar. Util para probar reglas. |
| `detection.alert_staff` | Avisa a operadores conectados cuando hay detecciones. |
| `detection.block` | Mapa de categorias usado solo con `preset: "custom"`. |
| `detection.allow_overrides` | Permite ids o categorias concretas aunque un preset las bloquee. |
| `detection.banned_mods` | Lista de ids exactos que siempre se bloquean. |
| `resource_packs.log` | Guarda resource packs activos/inactivos en perfiles. |
| `resource_packs.kick_on_banned` | Si es `true`, packs baneados expulsan. Por defecto solo registra. |
| `resource_packs.banned_packs` | Ids o nombres exactos de packs a marcar, activos o inactivos. |
| `resource_packs.banned_fingerprints` | Huellas SHA-256 exactas de packs a marcar aunque sean renombrados. |
| `exemptions.floodgate_auto` | Exime automaticamente jugadores Bedrock detectados por Floodgate. |
| `exemptions.bypass_players` | Nombres o UUIDs que saltan la verificacion. |
| `profiling.enabled` | Guarda perfiles de mods por jugador. |
| `profiling.max_history` | Maximo de eventos historicos guardados por perfil. |
| `whitelist.require_all` | En whitelist, exige que todos los mods oficiales esten presentes. |
| `whitelist.official_mods` | Snapshot oficial del modpack. Lo rellena `/fiwmods snapshot`. |

Despues de editar `config.json`, ejecuta:

```text
/fiwmods reload
```

## Presets de deteccion

| Preset | Bloquea |
|---|---|
| `strict` | Todas las categorias conocidas. |
| `balanced` | Clientes cheat, x-ray, fullbright, freecam, autoclickers y schematic printer. |
| `lenient` | Clientes cheat, x-ray y autoclickers. |
| `custom` | Solo las categorias activadas en `detection.block`. |

Categorias disponibles:

```text
cheat_clients
xray
fullbright
freecam
replay
minimap
autoclicker
schematic_printer
tweakeroo_utility
damage_indicators
zoom
```

## Modo blacklist

`blacklist` es el modo recomendado para empezar. Permite mods normales y bloquea
solo lo que este detectado por el preset o por `banned_mods`.

Configuracion recomendada para un primer despliegue:

```json
{
  "mode": "blacklist",
  "detection": {
    "preset": "balanced",
    "monitor_only": true,
    "alert_staff": true,
    "allow_overrides": [],
    "banned_mods": []
  }
}
```

Cuando confirmes que no hay falsos positivos, cambia:

```json
"monitor_only": false
```

Para bloquear un mod concreto por id:

```json
"banned_mods": ["examplemod"]
```

Para auditar resource packs sospechosos sin expulsar:

```json
"resource_packs": {
  "log": true,
  "kick_on_banned": false,
  "banned_packs": ["file/xray.zip", "xray.zip"],
  "banned_fingerprints": []
}
```

Para expulsar por packs configurados, cambia `kick_on_banned` a `true`.

Para permitir un id de mod concreto aunque coincida con una firma:

```json
"allow_overrides": ["examplemod"]
```

## Modo whitelist

`whitelist` sirve para forzar que todos usen el modpack oficial. Este modo
compara los mods reportados por el cliente con `whitelist.official_mods`.

Flujo recomendado:

1. Instala en el servidor el modpack oficial.
2. Instala Fiw AntiCheat en servidor y cliente.
3. Arranca el servidor en modo `blacklist` o en `whitelist` vacio.
4. Entra con un cliente limpio que tenga el modpack oficial.
5. Ejecuta:

```text
/fiwmods snapshot player <nombre>
```

6. Cambia `mode` a `whitelist`.
7. Ejecuta:

```text
/fiwmods reload
```

Tambien puedes capturar los mods del servidor con:

```text
/fiwmods snapshot server
```

Advertencia: si `mode` es `whitelist` y `official_mods` esta vacio, el mod entra
en modo setup y deja entrar a todos. Esto evita cerrar el servidor por accidente
antes de capturar el snapshot.

## Comandos

Todos los comandos requieren nivel de permiso 4.

| Comando | Que hace |
|---|---|
| `/fiwmods reload` | Recarga `config.json` y la base de firmas incluida. |
| `/fiwmods snapshot server` | Guarda los mods cargados en el servidor como whitelist oficial. |
| `/fiwmods snapshot player <nombre>` | Pide al jugador online su lista de mods y la guarda como whitelist oficial. |
| `/fiwmods profile <nombre>` | Muestra mods, resource packs y cambios recientes. |

## Perfiles de jugadores

Si `profiling.enabled` esta activo, el mod guarda perfiles en:

```text
config/fiw-mods-api/profiles/<uuid>.json
```

El comando `/fiwmods profile <nombre>` separa:

- `Mods`: mods relevantes instalados por el jugador.
- `Platform`: entradas ruidosas como Minecraft, Fabric API, Forge, NeoForge,
  loaders, mixins y el propio Fiw AntiCheat.
- `Resource packs active`: packs activos en el cliente.
- `Resource packs inactive`: packs instalados pero no activos.
- `Recent changes`: mods agregados, eliminados o con version cambiada, y packs
  agregados, activados, desactivados, eliminados o actualizados.

Esto ayuda a investigar cambios sospechosos sin leer listas enormes de mods de
plataforma.

## Ejemplos rapidos

Bloquear minimapas ademas del preset balanced:

```json
{
  "detection": {
    "preset": "custom",
    "block": {
      "cheat_clients": true,
      "xray": true,
      "fullbright": true,
      "freecam": true,
      "replay": false,
      "minimap": true,
      "autoclicker": true,
      "schematic_printer": true,
      "tweakeroo_utility": false,
      "damage_indicators": false,
      "zoom": false
    }
  }
}
```

Eximir una cuenta de pruebas:

```json
{
  "exemptions": {
    "bypass_players": ["TesterName", "00000000-0000-0000-0000-000000000000"]
  }
}
```

Probar el sistema sin expulsar:

```json
{
  "detection": {
    "monitor_only": true,
    "alert_staff": true
  }
}
```

## Build para desarrollo

Usa Java 21 para el proyecto completo. Java 17 sirve para targets 1.20.1 con
`--configure-on-demand`, pero Java 25 rompe esta version de Gradle/Groovy.

Comando completo:

```bash
./gradlew --configure-on-demand :core:test \
  :fabric-1.20.1:build \
  :forge-1.20.1:build \
  :fabric-1.21.1:build \
  :neoforge-1.21.1:build \
  :fabric-1.21.11:build \
  :neoforge-1.21.11:build
```

Servidores de prueba:

```bash
./gradlew --configure-on-demand :fabric-1.20.1:runServer
./gradlew --configure-on-demand :forge-1.20.1:runServer
./gradlew --configure-on-demand :fabric-1.21.1:runServer
./gradlew --configure-on-demand :neoforge-1.21.1:runServer
./gradlew --configure-on-demand :fabric-1.21.11:runServer
./gradlew --configure-on-demand :neoforge-1.21.11:runServer
```

Los jars de produccion quedan en:

```text
<modulo>/build/libs/
```

## Pruebas manuales recomendadas

1. Cliente limpio con Fiw AntiCheat instalado: debe pasar.
2. Cliente sin el companion mod: debe fallar por timeout.
3. Mod incluido en `detection.banned_mods`: debe expulsar.
4. `monitor_only: true`: debe registrar y avisar, pero no expulsar.
5. Resource pack activo/inactivo: debe aparecer en `/fiwmods profile <nombre>`.
6. Agregar, activar, desactivar o eliminar un resource pack online: debe crear
   un evento de historial tras el siguiente escaneo del cliente.
7. Pack en `resource_packs.banned_packs`: debe registrar por defecto y expulsar
   solo si `kick_on_banned` es `true`.
8. `/fiwmods snapshot player <nombre>`: debe actualizar `whitelist.official_mods`.
9. `mode: "whitelist"` con snapshot valido: debe permitir solo el modpack
   capturado.
10. `/fiwmods profile <nombre>`: debe mostrar mods, plataforma, packs y cambios.

## Solucion de problemas

El jugador es expulsado por timeout:

- Comprueba que el jar de Fiw AntiCheat esta instalado tambien en el cliente.
- Comprueba que el cliente usa el mismo loader/version de Minecraft que el
  servidor.
- Aumenta temporalmente `timeout_seconds` si el modpack tarda mucho al entrar.

Un mod permitido aparece como bloqueado:

- Pon `detection.monitor_only` en `true`.
- Revisa el id reportado con `/fiwmods profile <nombre>`.
- Anade el id a `detection.allow_overrides`.
- Ejecuta `/fiwmods reload`.

Un resource pack sospechoso solo aparece en logs:

- Esto es el default. Pon `resource_packs.kick_on_banned` en `true` para expulsar.
- Usa `resource_packs.banned_fingerprints` si el pack puede ser renombrado.
- Ejecuta `/fiwmods reload`.

Whitelist deja entrar a todos:

- Revisa si `whitelist.official_mods` esta vacio.
- Captura un snapshot con `/fiwmods snapshot player <nombre>` o
  `/fiwmods snapshot server`.
- Ejecuta `/fiwmods reload`.

Los admins no ven alertas:

- Comprueba que `detection.alert_staff` esta en `true`.
- Comprueba que los admins tienen permiso de operador/nivel 4.

## Limitaciones

- No detecta clientes maliciosos que modifiquen el companion para mentir.
- Resource packs instalados y borrados antes de que el companion los reporte no
  se pueden probar; los perfiles solo muestran lo que el cliente reporto al
  menos una vez.
- No sustituye sistemas anticheat basados en movimiento, combate o paquetes.
- Las firmas incluidas ayudan contra mods comunes, pero pueden necesitar ajustes
  para reglas especificas de cada servidor.
- En whitelist, las huellas de jar ayudan contra builds cambiadas o
  suplantaciones cuando estan presentes en el snapshot.
