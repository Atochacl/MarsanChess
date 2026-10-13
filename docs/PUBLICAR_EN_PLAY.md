# Publicar Marsan Chess en Google Play

Pasos para la primera publicación. Lo que ya está resuelto en el código
(Fix59) va marcado con [hecho].

## 1. Requisitos técnicos

- [hecho] `targetSdk 36` (Android 16), exigido para apps nuevas.
- [hecho] Botón Atrás con `OnBackPressedDispatcher` (con targetSdk 36
  `onBackPressed()` ya no se llama).
- [hecho] Pantallas de borde a borde: Juego, Estadísticas, Preferencias y
  Partidas grabadas respetan la barra de estado y la de navegación.
- [hecho] Base de datos con migraciones: el historial y las grabaciones no
  se borran al actualizar la app. El esquema se exporta a `app/schemas/`:
  después del primer build, subir esa carpeta al repo.
- [hecho] Aviso de licencia de Stockfish (GPLv3) en Preferencias > Acerca de.

## 2. Llave de firma (una sola vez)

En Android Studio: Build > Generate Signed App Bundle or APK > Android App
Bundle > Create new... y guardar el `.jks` FUERA de OneDrive y del repo,
con copia de seguridad. O por consola:

```
keytool -genkeypair -v -keystore marsanchess-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Luego copiar `keystore.properties.example` como `keystore.properties` en la
raíz del proyecto y completar la ruta y las claves. Si se pierde esta llave
de subida, Google permite cambiarla (con Play App Signing activado), pero es
un trámite; mejor no perderla.

## 3. Generar el AAB

Build > Generate Signed App Bundle (o `gradlew bundleRelease`). El archivo
queda en `app/build/outputs/bundle/release/app-release.aab`.
Antes de cada versión nueva subir `versionCode` (y `versionName`) en
`app/build.gradle`.

## 4. Play Console

1. Crear la app (idioma por defecto español, app gratuita, juego).
2. Activar Play App Signing (viene activado por defecto).
3. Pruebas: las cuentas personales nuevas deben hacer una prueba cerrada
   con al menos 12 testers durante 14 días antes de poder publicar en
   producción. Conviene empezar esto cuanto antes.
4. Ficha de la tienda en ES y EN: nombre, descripción corta y larga, ícono
   512x512, gráfico de funciones 1024x500 y capturas de pantalla.
5. Clasificación de contenido: cuestionario IARC (juego de mesa, sin
   violencia ni compras).
6. Público objetivo: indicar edades. Si se incluye a menores de 13 años
   aplican las reglas de Familias; lo más simple es 13+.
7. Seguridad de los datos (Data safety): ver sección 5.
8. Política de privacidad: URL pública obligatoria (ver sección 6).

## 5. Formulario de Seguridad de los datos

La app no pide permiso de Internet ni usa SDKs de terceros (sin anuncios
ni analítica), así que:

- ¿Recopila o comparte datos de usuario? **No.**
- El nombre de jugador, el historial y las grabaciones se guardan solo en
  el teléfono y no salen de él (no cuenta como "recopilación" para Play).
- Cifrado en tránsito / eliminación de datos: no aplica (no hay envío).

Si en el futuro se agrega el multijugador online, anuncios o analítica,
este formulario y la política de privacidad deben actualizarse.

## 6. Política de privacidad

El texto está en `docs/politica-de-privacidad.md` (ES y EN). Play exige una
URL pública: se puede publicar con GitHub Pages, en un sitio propio o en
una página de Google Sites. Falta completar el correo de contacto.

## 7. Play Integrity

No hace falta por ahora: sirve para validar partidas o cuentas en un
servidor, así que se deja para el multijugador online (fase 2).
