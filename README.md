# YTCustom — Plugin para PaperMC 1.21.1 (Java 21)

Jefe **THE HOLLOW KING** (3 fases), monedas, **Pluma de Impulso**, arma, herramienta, mineral con bloque
personalizado, esbirros propios y resource pack con modelos 3D y texturas originales.

> **Estado real de las pruebas (léelo):** el código se escribió y revisó de forma estática. En el entorno donde se
> generó **no había acceso a internet**, así que **no se pudo descargar la API de Paper, ni compilar, ni ejecutar el plugin
> en un servidor**. Por eso **no se incluye ningún JAR**. El JAR se compila con GitHub Actions (abajo). Hasta que lo
> pruebes en tu servidor, trátalo como "sin probar en juego".

## Contenido

```
YTCustom/
├── build.gradle / settings.gradle
├── .github/workflows/build.yml      ← compila JAR + empaqueta resource pack
├── src/main/java/com/ytcustom/      ← código
├── src/main/resources/plugin.yml, config.yml
├── resourcepack/                    ← resource pack (carpeta)
├── tools/generate_pack.py           ← regenera texturas/modelos y el ZIP del pack
└── dist/YTCustom-ResourcePack.zip   ← ZIP del resource pack ya generado
```

## 1. Compilar el JAR (GitHub Actions)

1. Crea un repositorio en GitHub y sube **todo el contenido** de esta carpeta (el archivo debe quedar en `.github/workflows/build.yml`).
2. Pestaña **Actions** → workflow **Build YTCustom** → se ejecuta solo con cada push (o "Run workflow").
3. Al terminar, descarga los artefactos: `YTCustom-plugin-jar` (el JAR) y `YTCustom-resource-pack` (ZIP + SHA-1).

Compilación local (necesitas Java 21 y Gradle 8.x): `gradle build` → `build/libs/YTCustom-1.0.0.jar`.

## 2. Instalar en PaperMC 1.21.1

1. Copia el JAR a `plugins/` y arranca el servidor una vez (se crea `plugins/YTCustom/config.yml` y `secret.key`).
2. **No compartas ni borres `secret.key`**: firma los objetos personalizados (anti-falsificación). Si lo borras, los objetos antiguos dejarán de reconocerse.
3. Da permisos si hace falta (ver Comandos).

## 3. Resource pack: alojarlo y activarlo

El pack **no se aplica solo**: el servidor solo lo pide si has configurado una URL.

1. Usa `dist/YTCustom-ResourcePack.zip` (o el de GitHub Actions).
2. Súbelo a un sitio que dé un **enlace HTTPS directo de descarga** (p. ej. un *Release* de GitHub:
   `https://github.com/USUARIO/REPO/releases/download/v1.0.0/YTCustom-ResourcePack.zip`).
   Enlaces de "vista previa" (Drive, Dropbox con `?dl=0`) **no** sirven.
3. Calcula el hash del MISMO archivo: `sha1sum YTCustom-ResourcePack.zip` (Windows: `certutil -hashfile archivo SHA1`).
4. En `plugins/YTCustom/config.yml`:
   ```yaml
   resource-pack:
     enabled: true
     url: "https://.../YTCustom-ResourcePack.zip"
     sha1: "40 caracteres hexadecimales"
     required: false
   ```
5. `/ytcustom reload` y entra de nuevo (o `/ytcustom pack` para reenviártelo).
   Si cambias el ZIP, **recalcula el SHA-1**.

SHA-1 del ZIP incluido (ZIP determinista): `6d12ddfc7ca4dd69ba5f35ae22aba88a380ba4f4`
(si regeneras el pack con `tools/generate_pack.py` debería coincidir; compruébalo igualmente con `sha1sum`).

## Comandos y permisos

| Comando | Permiso (por defecto) | Función |
|---|---|---|
| `/ytboss` · `/ytboss remove` | `ytcustom.boss` (op) | Invoca al jefe en tu posición / elimina los jefes activos |
| `/ytitems <objeto\|all> [n]` | `ytcustom.items` (op) | Objetos: `coin pluma heart blade pickaxe ingot ore` |
| `/ytcoins [jugador]` | `ytcustom.coins` (todos); otros: `ytcustom.coins.others` (op) | Cuenta Monedas Huecas (inventario + cofre de Ender) |
| `/ytcustom reload` · `pack` | `ytcustom.admin` (op) | Recarga config / reenvía el resource pack |

## Qué hace cada cosa

- **THE HOLLOW KING**: entidad base (Wither Skeleton invisible, escalada) + modelos 3D (ItemDisplay) distintos por fase + anillo giratorio.
  - *Fase I Despertar*: rayo de sombras (línea telegrafiada) y golpe al suelo (círculo telegrafiado).
  - *Fase II (≈66 %)*: +velocidad y daño, onda de energía (se esquiva saltando), pulso de empuje, invoca Espectros Huecos.
  - *Fase III (≈33 %)*: +meteoros de energía oscura marcados 1.5 s antes, combo golpe→onda→rayo, aura de oscuridad cercana.
  - Transiciones de 3 s con invulnerabilidad, título, sonido y cambio de modelo.
- **Recompensas** (una sola vez, protegido contra doble ejecución): 8–16 Monedas Huecas (configurable), Pluma de Impulso garantizada, Corazón Hueco (15 % configurable), XP; sin drops vanilla; ranking de daño en el anuncio.
- **Pluma de Impulso**: clic derecho, impulso horizontal + pequeño vertical, recarga 18 s (config), 6 cargas (config) y se desvanece al agotarse. Solo se procesa la mano principal y la recarga se fija antes de gastar carga (sin dobles consumos).
- **Anti-falsificación**: cada objeto lleva en su PersistentDataContainer un id + firma HMAC-SHA256 con tu `secret.key`. Un objeto vanilla con el mismo nombre/lore no cuenta como moneda ni como Pluma.
- **Mena de Hollowite**: bloque personalizado sobre NOTE_BLOCK (instrumento *bit*, nota 7). Genera vetas en chunks **nuevos** de pizarra; da Lingotes (Fortuna/Toque de seda soportados). Con lingotes se craftean el **Filo Hueco** y el **Pico Hueco**.

## Limitaciones conocidas (honestas)

- **Sin compilar ni probar en servidor** (ver arriba). Puede haber fallos de API que solo aparecerán al compilar con Paper 1.21.1.
- **Orientación del modelo**: si el Rey mira hacia atrás, pon `boss.model-yaw-offset: 180` en config.
- **Tamaño visual vs hitbox**: se ajustan con `boss.hitbox-scale` y `boss.model-scale`.
- **Sin sonidos propios**: se usan sonidos vanilla. **Sin texturas vanilla sustituidas** (solo los objetos/bloque/modelos nuevos); no se repintan mobs vanilla.
- **Sin animación por huesos**: las animaciones (inclinación, pulso, giro, encogimiento, anillo giratorio) son transformaciones del modelo completo, no de miembros articulados.
- Las entidades personalizadas (Rey, Espectros) son **entidades vanilla invisibles + modelos ItemDisplay**: sin resource pack se verán como un esqueleto/vex invisibles con solo partículas.
- Los jugadores sin el resource pack verán los objetos como sus bases vanilla (pepita de oro, pluma, etc.).
- La mena solo se genera en chunks nuevos y el bloque no se puede mover con pistones ni destruir con explosiones (decisión de diseño para que no se convierta en un bloque de notas normal).
- El jefe se elimina al reiniciar/parar el servidor (no es persistente) para evitar entidades huérfanas.

## Regenerar el resource pack

`python3 tools/generate_pack.py` (solo biblioteca estándar) reescribe `resourcepack/` y `dist/YTCustom-ResourcePack.zip`.
Formato de pack: 34 (Minecraft 1.21 / 1.21.1).
