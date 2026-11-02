# Launcher Outlander (v0.2)

Launcher Android (Kotlin) para la radio FYT del Outlander 2006. Sin root.
Incluye: foto de tu camioneta, mapa, velocímetro digital, modo día y noche, widgets
(música, servicios, documentos, gasolina, gastos, marcación rápida, dónde estacioné, clima y más)
y respaldo de datos.

## Cómo obtener el APK sin instalar nada (sin administrador)

Se compila gratis en los servidores de GitHub. Solo necesitas tu cuenta y el navegador.

1. Descomprime este zip en tu PC (el explorador de Windows lo hace sin permisos de administrador).
2. En github.com crea un repositorio nuevo, por ejemplo `launcher-outlander` (puede ser **privado**).
3. En el repositorio: **Add file > Upload files**. Arrastra **el contenido** de la carpeta
   `OutlanderLauncher` (no el zip). Espera a que suban todos los archivos y pulsa **Commit changes**.
4. Revisa que haya quedado la carpeta `.github/workflows/build.yml`. Si no apareció (a veces las
   carpetas que empiezan con punto no se arrastran): **Add file > Create new file**, escribe el nombre
   `.github/workflows/build.yml` y pega el contenido del archivo `build.yml` de este proyecto.
5. Abre la pestaña **Actions**. Verás "Compilar APK" ejecutándose (tarda de 5 a 10 minutos).
   Si no arranca sola, entra a "Compilar APK" y pulsa **Run workflow**.
6. Cuando termine (palomita verde), abre esa ejecución y baja hasta **Artifacts**.
   Descarga **LauncherOutlander-APK**, descomprímelo y ahí está `app-debug.apk`.
7. Cópialo a una memoria USB, conéctala a la radio e instálalo con un explorador de archivos.
   Si la radio pide permiso para "instalar apps desconocidas", acéptalo.

Si la ejecución sale con una X roja: ábrela, copia el mensaje de error y mándaselo a Claude.

### Alternativa local (también sin administrador)
Android Studio tiene una versión en **.zip** (sin instalador) en developer.android.com/studio.
Descomprímela en una carpeta tuya, abre `studio64.exe`, elige **Open** sobre esta carpeta, espera el
Sync y usa **Build > Build APK(s)**. Necesita internet para bajar el SDK.

## Primer uso en la radio
1. Pulsa **Inicio**: Android pregunta qué launcher usar. Elige **Launcher Outlander** y "Siempre".
2. Acepta el permiso de **ubicación**. Si te da la opción, elige **Permitir todo el tiempo**: así el
   kilometraje y el lugar de estacionamiento se siguen guardando mientras usas otra app.
3. **Personalizar > Activar acceso a la música** y enciende "Launcher Outlander".
4. Toca **Kilometraje** y escribe el del tablero. Después la app lo suma sola con el GPS.
5. Toca cada servicio y usa **Ajustar lo que falta** para poner los valores reales.
6. **Personalizar > Destinos Casa y Trabajo** para los botones de navegación.

## Qué se guarda y dónde
Todo se guarda en el almacenamiento de la radio, no depende del arnés: kilometraje, servicios,
documentos, gasolina, gastos, contactos, widgets elegidos, tema y último punto donde estuvo el carro.
Se mantiene aunque se apague la radio. Solo se pierde si desinstalas la app, borras sus datos o
restableces la radio de fábrica.

**Respaldo:** Personalizar > Respaldo de datos > Exportar. Crea `outlander-respaldo.json` en
Descargas y en la memoria USB si hay una conectada. Para restaurar, usa Importar.

## Cosas que conviene saber
- **Mapa:** usa OpenStreetMap y necesita internet (hotspot del teléfono). Sin internet se ve en blanco.
  Tocar el mapa abre tu app de navegación.
- **Dónde estacioné** se guarda solo: es el último punto donde el GPS detectó movimiento.
- **Widgets OBD** (batería, temperatura del motor, RPM, consumo, fallas) muestran datos de ejemplo.
  Necesitan un adaptador ELM327, que todavía no está conectado en esta versión.
- **Aviso de velocidad:** toca el mini para elegir el límite. Suena un pitido al pasarlo.
- **Modo demo:** mantén pulsado el velocímetro digital para simular velocidad (útil para probar en casa).
- **Tema automático:** cambia con el amanecer y atardecer de tu ubicación (necesita el clima); si no
  hay datos usa de 6:00 a 19:00. También puedes fijarlo en Día o Noche.
- El acceso **Radio** busca la app de radio FYT por nombre de paquete. Si no la encuentra, te deja
  elegirla la primera vez.
- Para usar otra foto: reemplaza `app/src/main/res/drawable-nodpi/outlander.png` por un PNG con fondo
  transparente, de la camioneta mirando a la izquierda.
