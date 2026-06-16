# Fiw AntiCheat - Documentacion completa (Espanol)

Una herramienta del lado servidor de **verificacion y control de mods** para
Minecraft. Cuando un jugador entra, el servidor le envia un reto; un cliente con
el mod companero responde con sus mods cargados (id, version, huella SHA-256 del
jar y marcadores de codigo estables); el servidor evalua ese reporte y deja
entrar al jugador o lo expulsa.

> **Modelo de amenaza honesto - leelo primero.**
> Esto es un *enforcer de modpacks/mods*, no un anti-cheat criptograficamente
> irrompible. El reporte lo manda el cliente y el mod companero es de confianza.
> Un atacante decidido que decompile y edite el mod companero puede mentir sobre
> su lista de mods, y **ningun sistema del lado cliente puede impedir eso del
> todo**, porque el cliente posee todos los secretos. Lo que este mod si frena de
> forma fiable es el nivel casual: renombrar el jar, cambiar el id de un mod,
> subir de version, suplantar un mod permitido, repetir paquetes (replay) y "no
> instalarlo". Para algo mas robusto, combinalo con un launcher controlado (packs
> firmados) y un anti-cheat **del lado servidor** para cheats de inyeccion/DLL.

---

## 1. Targets soportados

El mod es un motor compartido mas un adaptador pequeno por loader/version. Elige
el **unico jar** que corresponde al loader y la version de Minecraft del servidor.

| Minecraft | Loader | Java | Jar |
|---|---|---:|---|
| 1.20.1 | Fabric | 17 | `fiw-anticheat-fabric-1.20.1-<version>.jar` |
| 1.20.1 | MinecraftForge 47.x | 17 | `fiw-anticheat-forge-1.20.1-<version>.jar` |
| 1.21.1 | Fabric | 21 | `fiw-anticheat-fabric-1.21.1-<version>.jar` |
| 1.21.1 | NeoForge | 21 | `fiw-anticheat-neoforge-1.21.1-<version>.jar` |
| 1.21.11 | Fabric | 21 | `fiw-anticheat-fabric-1.21.11-<version>.jar` |
| 1.21.11 | NeoForge | 21 | `fiw-anticheat-neoforge-1.21.11-<version>.jar` |

NeoForge no publica una build de Minecraft **1.20.1** en su Maven actual (la mas
antigua es 1.20.2), asi que el target 1.20.1 de la familia Forge usa
**MinecraftForge 47.x**.

---

## 2. Requisitos

- Cada jugador de **Java** debe tener el jar companero correspondiente (incluyelo
  dentro de tu modpack/launcher para que no lo instalen a mano).
- Dependencias del loader:
  - Fabric -> Fabric Loader + Fabric API.
  - Forge 1.20.1 -> MinecraftForge 47.x.
  - NeoForge -> la version de NeoForge del target.
- Los jugadores de **Bedrock via Geyser/Floodgate quedan exentos
  automaticamente** (no pueden correr mods de Java), asi que nunca son expulsados.

---

## 3. Como funciona (protocolo)

Todo el trabajo ocurre **una vez por entrada**, nunca por tick.

1. **Entrada -> congelar + reto.** El servidor congela al jugador (se bloquea el
   movimiento y las interacciones, y se le regresa si intenta moverse) y envia un
   **nonce** aleatorio de 32 bytes.
2. **Reporte del cliente.** El mod companero enumera todos los mods cargados y
   responde con, por mod:
   - `id` - id del mod en el loader
   - `version` - cadena de version de los metadatos del mod
   - `fingerprint` - **SHA-256 del archivo jar del mod**
   - `markers` - senales de identidad estables: paquetes raiz + nombres de
     configs de mixin declarados (sobreviven a renombrar el jar *y* a renombrar el
     id interno del mod)
   - mas el **nonce devuelto (echo)**
3. **Evaluacion en el servidor** (ver seccion 6). El resultado es PASS o KICK.
   - PASS -> descongela.
   - KICK -> desconecta con un mensaje generico; el motivo/mod **real** se registra
     en consola y (opcionalmente) se avisa al staff conectado.
   - Sin respuesta dentro de `timeout_seconds` -> expulsado con el mensaje de
     timeout.
4. **Exenciones** (Bedrock por Floodgate + lista de bypass) saltan todo el proceso.

El nonce por entrada es **anti-replay / ligado a la sesion** (un tercero fuera de
la sesion no puede falsificar una respuesta valida y no se pueden reusar respuestas
viejas). **No** es una firma del contenido: el mod companero, que todos los
jugadores tienen, siempre puede devolver su propio nonce. Ese es el limite
irreducible de confianza en el cliente.

---

## 4. Instalacion

1. Pon el jar correspondiente en la carpeta `mods/` del servidor.
2. Entrega el mismo jar a los clientes (dentro de tu modpack/launcher).
3. Arranca el servidor una vez para generar la config en:
   ```
   config/fiw-mods-api/config.json
   ```
   (El id interno `fiw-mods-api` se mantiene por compatibilidad; el nombre publico
   es Fiw AntiCheat.)
4. Edita la config (seccion 5) y recarga en el juego con `/fiwmods reload`
   (operador nivel 4).

---

## 5. Referencia de configuracion

Config por defecto completa:

```jsonc
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
      "cheat_clients": true, "xray": true, "fullbright": true, "freecam": true,
      "replay": false, "minimap": false, "autoclicker": true,
      "schematic_printer": true, "tweakeroo_utility": false,
      "damage_indicators": false, "zoom": false
    },
    "allow_overrides": [],
    "banned_mods": []
  },
  "exemptions": { "floodgate_auto": true, "bypass_players": [] },
  "profiling": { "enabled": true, "max_history": 200 },
  "whitelist": { "require_all": true, "official_mods": [] }
}
```

### Opciones raiz

| Clave | Tipo | Default | Significado |
|---|---|---|---|
| `mode` | string | `blacklist` | `blacklist` (abierto, bloquea los mods listados) o `whitelist` (solo el set oficial). |
| `timeout_seconds` | int | `10` | Segundos que tiene el cliente para responder al reto (minimo efectivo 1). Subelo en conexiones lentas para no expulsar jugadores legitimos antes de que respondan. |
| `kick_message` | string | ... | Mostrado a un jugador expulsado por un mod bloqueado o por whitelist. Nunca nombra el mod. |
| `timeout_message` | string | ... | Mostrado cuando el cliente no responde a tiempo. |

### `detection`

| Clave | Tipo | Default | Significado |
|---|---|---|---|
| `preset` | string | `balanced` | `strict` \| `balanced` \| `lenient` \| `custom`. Elige que categorias se bloquean. |
| `monitor_only` | bool | `false` | Si es `true`, **no se expulsa a nadie**: solo se registran las detecciones. Para despliegue seguro / observacion. |
| `alert_staff` | bool | `true` | Avisa a los operadores conectados con el nombre real del mod al detectar. |
| `block` | map | (balanced) | Mapa categoria -> on/off. **Solo se usa cuando `preset` es `custom`.** |
| `allow_overrides` | string[] | `[]` | Ids de mods a desbloquear de una categoria bloqueada (ej. permitir un minimap). |
| `banned_mods` | string[] | `[]` | Ids extra a bloquear siempre (ademas de la base de firmas). |

### `exemptions`

| Clave | Tipo | Default | Significado |
|---|---|---|---|
| `floodgate_auto` | bool | `true` | Exime automaticamente a jugadores Bedrock/Floodgate (dependencia blanda por reflexion; no hace nada si Floodgate no esta). |
| `bypass_players` | string[] | `[]` | **Nombres o UUIDs** que saltan la verificacion por completo (staff). |

### `profiling`

| Clave | Tipo | Default | Significado |
|---|---|---|---|
| `enabled` | bool | `true` | Guardar perfiles de mods por jugador (ver seccion 9). |
| `max_history` | int | `200` | Maximo de eventos de cambio guardados por jugador antes de recortar los mas viejos. |

### `whitelist`

| Clave | Tipo | Default | Significado |
|---|---|---|---|
| `require_all` | bool | `true` | Si es `true`, un jugador al que le falte algun mod oficial es expulsado (set exacto). Si es `false`, pueden faltar mods oficiales. |
| `official_mods` | object[] | `[]` | El set oficial capturado; lo llena `/fiwmods snapshot`. Cada entrada: `{ "id", "version", "fingerprint" }`. |

`official_mods[].fingerprint` es el SHA-256 del jar del mod. **Cuando esta
presente, se exige**: un mod que reclame ese id con otro jar es rechazado
(anti-suplantacion). Vuelve a hacer snapshot tras actualizar el pack para
refrescar las huellas.

---

## 6. Orden de evaluacion

Por cada entrada, en orden:

1. **Exencion** (Bedrock por Floodgate o `bypass_players`) -> **PASS**.
2. **Blocklist** (corre en ambos modos). Por cada mod reportado, se compara con la
   base de firmas (seccion 8). Si una firma coincide, su categoria esta activada y
   el id no esta en `allow_overrides` -> se registra una deteccion. Los ids en
   `banned_mods` tambien detectan.
3. **Modo**:
   - `blacklist` -> expulsa si hay alguna deteccion (si no, PASS - servidor abierto).
   - `whitelist` -> ademas exige que cada mod reportado este en `official_mods`
     (mod desconocido -> kick), exige las huellas fijadas (no coincide -> kick), y
     si `require_all`, expulsa cuando falta un mod oficial.
4. **`monitor_only`** suprime *todos* los kicks: solo registra las detecciones.

`official_mods` vacio en modo `whitelist` = **modo setup**: todos pueden entrar
(con un aviso fuerte en el log) hasta que captures un snapshot.

---

## 7. Modos en la practica

**Blacklist (por defecto).** Los jugadores pueden usar cualquier cosa excepto
mods que coincidan con una categoria activada de la blocklist o con `banned_mods`.
Ideal para un servidor abierto que solo quiere mantener fuera cheats/xray/etc.

**Whitelist.** Solo se permite el modpack oficial capturado; lo demas se expulsa.
Ideal para un servidor de modpack cerrado o un launcher controlado. Flujo:

1. Pon `"mode": "whitelist"`.
2. Entra con el pack oficial instalado y ejecuta `/fiwmods snapshot player <tu>`
   (o `/fiwmods snapshot server` para capturar los mods del propio servidor).
3. El set oficial (id + version + fingerprint) se escribe y se recarga.
4. Vuelve a hacer snapshot cada vez que cambie el pack.

---

## 8. Base de firmas (mods conocidos no permitidos)

La blocklist es un archivo de datos incluido:
`core/src/main/resources/signatures.json`. Cada entrada es solo datos: agrega
mods o categorias sin tocar codigo:

```jsonc
{ "name": "Some Cheat", "category": "cheat_clients",
  "match": { "ids": ["somecheat"], "packages": ["com.example.cheat"],
             "mixins": ["somecheat.mixins.json"] } }
```

Una firma coincide si **cualquiera** de las reglas acierta:
- `ids` - id exacto del mod (sobrevive a renombrar el archivo jar; el id vive en
  los metadatos),
- `packages` - prefijo de paquete raiz (sobrevive a renombrar el id interno),
- `mixins` - nombre del archivo de config de mixin declarado.

La coincidencia **nunca** es por version ni hash, asi que sobrevive a
actualizaciones de mods. Categorias:

`cheat_clients`, `xray`, `fullbright`, `freecam`, `replay`, `minimap`,
`autoclicker`, `schematic_printer`, `tweakeroo_utility`, `damage_indicators`,
`zoom`.

### Presets

| Preset | Bloquea |
|---|---|
| `strict` | todas las categorias |
| `balanced` (default) | cheat_clients, xray, fullbright, freecam, autoclicker, schematic_printer |
| `lenient` | cheat_clients, xray, autoclicker |
| `custom` | exactamente el mapa `detection.block` |

`allow_overrides`, `banned_mods` y `bypass_players` se aplican siempre por encima.

> La deteccion es **por mod**, no por ajuste: el mod puede bloquear un mod de
> minimapa entero, pero no puede leer un toggle *dentro* de un mod (ej. permitir
> el mapa de Xaero pero bloquear solo su radar de cuevas/entidades).

---

## 9. Perfiles por jugador

Con `profiling.enabled`, el servidor guarda un perfil por jugador en:

```
config/fiw-mods-api/profiles/<uuid>.json
```

En cada entrada compara el set de mods reportado contra el guardado y agrega
eventos `added` / `removed` / `updated` con fecha (limitado a `max_history`). Se
registra en cada reporte verificado (incluidos los kicks, para capturar la entrada
infractora). La salida **separa los mods reales instalados por el jugador del
ruido de plataforma** (minecraft, loader, fabric-api, neoforge, el propio
anticheat, etc.) para que el staff vea la lista util.

Velo con `/fiwmods profile <nombre>` - muestra los mods actuales agrupados +
cambios recientes, util para detectar "este jugador acaba de agregar freecam".

---

## 10. Comandos

`/fiwmods` - requiere permiso de operador nivel 4.

| Comando | Descripcion |
|---|---|
| `/fiwmods reload` | Recarga config + firmas incluidas desde disco |
| `/fiwmods snapshot server` | Captura los mods del **propio servidor** como whitelist |
| `/fiwmods snapshot player <nombre>` | Captura los mods reportados por un jugador conectado como whitelist |
| `/fiwmods profile <nombre>` | Muestra los mods actuales agrupados + historial reciente |

---

## 11. Rendimiento

Toda la verificacion es **por entrada** y fuera del hilo principal para la E/S;
nada corre por tick salvo un barrido ligero de timeout sobre las entradas
pendientes. El hash del jar del cliente ocurre una vez en la entrada, en el
cliente. El impacto en TPS es practicamente nulo, incluso con muchos jugadores.

---

## 12. Compilar desde el codigo

```bash
# Usa Java 21 para correr el build multi-proyecto completo.
./gradlew build          # compila core + todos los targets
./gradlew :core:test     # corre los tests unitarios del motor
```

Los jars por target quedan en el `build/libs/` de cada modulo. Nota de tooling: el
proyecto fija Gradle 8.10.2 (compatibilidad con Loom/ForgeGradle/NeoForge) y usa
el foojay toolchain resolver para auto-provisionar Java 17/21; el daemon de Gradle
debe correr en Java 21 (Loom lo exige para los targets 1.21.x).

Estructura del proyecto:

```
core/             Motor en Java puro: config, firmas, evaluacion, perfiles (+ tests)
fabric-1.20.1/    Adaptador Fabric 1.20.1 (red por canales legacy)
fabric-1.21.1/    Adaptador Fabric 1.21.1 (red CustomPayload)
fabric-1.21.11/   Adaptador Fabric 1.21.11
forge-1.20.1/     Adaptador MinecraftForge 1.20.1 (congelado por eventos, SimpleChannel)
neoforge-1.21.1/  Adaptador NeoForge 1.21.1 (red por payloads, congelado por mixin)
neoforge-1.21.11/ Adaptador NeoForge 1.21.11
```

---

## 13. Solucion de problemas

- **Jugadores legitimos expulsados al entrar** -> sube `timeout_seconds`; las
  conexiones lentas pueden no responder a tiempo.
- **Un submod/dependencia anidado queda marcado** -> agrega su id a
  `allow_overrides`, o usa un preset menos estricto.
- **Mods que se auto-actualizan (ej. Essential) rompen el modo whitelist** ->
  fijalos con un launcher controlado, o mantenlos en modo `blacklist`.
- **Jugadores de Bedrock expulsados** -> asegurate de tener Floodgate instalado y
  `floodgate_auto` en `true`, o agregalos a `bypass_players`.
- **Despliegue en un servidor en vivo** -> pon `monitor_only: true` primero,
  revisa los logs, y luego activa el bloqueo.

---

## 14. Licencia

Fiw AntiCheat se publica bajo la **Fiw AntiCheat License (Attribution,
Non-Commercial)** - Copyright (c) 2026 Fi3w0. Puedes usar, modificar, forkear y
redistribuir el mod (incluidas builds modificadas) siempre que des credito claro y
visible a Fi3w0 como creador original, enlaces al original y mantengas intactos los
avisos existentes. **No puedes venderlo** ni vender ningun fork sin permiso escrito
(correrlo en un servidor, incluso uno monetizado, esta permitido). Fi3w0 conserva
la autoria de la obra original. Ver [LICENSE](LICENSE). Contacto: Discord `fi3w0`.