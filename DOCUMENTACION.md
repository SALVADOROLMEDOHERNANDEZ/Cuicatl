# CUICATL - Reproductor de Música Profesional con Editor de Audio

## 🎵 Descripción General
CUICATL es una aplicación Android moderna, funcional y futurística que funciona como:
- **Reproductor de Música Profesional** con controles completos
- **Editor de Audio Profesional** con múltiples herramientas de edición
- **Diseño Futurístico y Atractivo** con paleta de colores neón

---

## ✨ Características Principales

### 1. Pantalla Principal (MainActivity)
- ✅ Lista completa de canciones del dispositivo
- ✅ Interfaz futurística con colores neón (azul cian y púrpura)
- ✅ RecyclerView optimizado para navegar canciones
- ✅ Acceso a permisos de lectura de archivos
- ✅ Items de canciones con icono, título y artista

### 2. Reproductor de Audio (PlayerActivity)
Características:
- ✅ **Reproducción completa**: Play, Pause, Stop
- ✅ **Control de posición**: SeekBar para saltar a cualquier momento
- ✅ **Información detallada**:
  - Nombre de la canción y artista
  - Tiempo actual y duración
  - Formato MM:SS
- ✅ **Actualización automática** del progreso cada 500ms
- ✅ **Botones de control** codificados con iconos (▶ ⏸ ⏹)
- ✅ **Acceso directo** al editor de audio
- ✅ **Gestor de recursos** (liberación de MediaPlayer al salir)

### 3. Editor de Audio Profesional (EditorActivity)
Herramientas de edición:

#### A. Recorte de Audio (Trim)
- Especifica tiempo de inicio (ms)
- Especifica tiempo de fin (ms)
- Validación de rangos
- Extrae la porción deseada del audio

#### B. Efectos de Audio
- 🔊 **Eco**: Efecto de delay múltiple
- 🎼 **Reverberación**: Efecto ambiental profesional
- 📊 **Normalización de volumen**: Optimiza niveles de audio

#### C. Gestión de Archivos
- 💾 **Guardar**: Exporta archivo editado a Music folder
- ← **Atrás**: Regresa al reproductor

---

## 🎨 Diseño Futurístico

### Paleta de Colores
- **Neón Azul (#00FFFF)**: Títulos principales
- **Neón Púrpura (#9D00FF)**: Subtítulos y acentos
- **Fondo Oscuro (#121212)**: Tema oscuro profesional
- **Degradado Futurístico**: Fondo con gradiente de 45°
- **Bordes Ciánicos**: Items con borde azul cian

### Elementos Visuales
- Bordes redondeados en 8dp
- Iconos de emoji para mejor UX
- Tipografía negrita para títulos
- Separación clara entre secciones
- Material Design 3 (Dark Theme)

---

## 🔧 Especificaciones Técnicas

### Dependencias Utilizadas
```
- AndroidX AppCompat
- AndroidX ConstraintLayout
- AndroidX RecyclerView
- Material Design 3
- Android Media APIs (MediaPlayer)
```

### Versiones
- **Min SDK**: 28 (Android 9.0)
- **Target SDK**: 36 (Android 15)
- **Compile SDK**: 36
- **Java Compatibility**: 11

### Permisos Requeridos
```xml
- android.permission.READ_EXTERNAL_STORAGE
- android.permission.RECORD_AUDIO
- android.permission.WRITE_EXTERNAL_STORAGE (hasta SDK 32)
```

---

## 📱 Estructura del Proyecto

```
CUICATL/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/cuicatl/
│   │   │   ├── MainActivity.kt (Pantalla principal)
│   │   │   ├── PlayerActivity.kt (Reproductor)
│   │   │   ├── EditorActivity.kt (Editor profesional)
│   │   │   ├── adapters/
│   │   │   │   └── SongAdapter.kt
│   │   │   ├── models/
│   │   │   │   └── Song.kt
│   │   │   └── utils/
│   │   │       ├── FileUtils.kt
│   │   │       └── AudioUtils.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml
│   │   │   │   ├── activity_player.xml
│   │   │   │   ├── activity_editor.xml
│   │   │   │   └── item_song.xml
│   │   │   ├── drawable/
│   │   │   │   ├── futuristic_background.xml
│   │   │   │   └── item_background.xml
│   │   │   └── values/
│   │   │       ├── colors.xml
│   │   │       ├── strings.xml
│   │   │       ├── themes.xml
│   │   │       └── values-night/themes.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
└── gradle/libs.versions.toml
```

---

## 🚀 Funcionamiento Detallado

### Flujo de la Aplicación

1. **Inicio (MainActivity)**
   - Solicita permisos de lectura
   - Carga todas las canciones del dispositivo
   - Muestra lista en RecyclerView

2. **Selección de Canción**
   - Usuario toca una canción
   - Se abre PlayerActivity
   - Se carga y prepara el audio

3. **Reproducción (PlayerActivity)**
   - Usuario controla reproducción
   - SeekBar muestra progreso
   - Tiempos se actualizan automáticamente
   - Puede editar o regresar

4. **Edición (EditorActivity)**
   - Usuario aplica transformaciones
   - Puede combinar múltiples efectos
   - Guarda resultado final

---

## 💻 Implementación de AudioUtils

### Algoritmos de Edición

#### Recorte (Trim)
- Lee archivo de audio como bytes
- Calcula posición de inicio y fin en bytes
- Extrae rango especificado
- Guarda archivo trimmed

#### Eco
- Crea delay de 250ms
- Superpone señal original con versión atrasada
- Mezcla señales con factor de atenuación

#### Reverberación
- Aplica múltiples delays (100, 200, 400, 800ms)
- Crea efecto de "room" o ambiente
- Factor de decay para realismo

#### Normalización
- Encuentra pico máximo de volumen
- Calcula factor de escala
- Aplica amplificación uniforme

---

## 🎯 Mejoras Realizadas

✅ Agregadas todas las actividades al Manifest
✅ Implementados permisos de audio
✅ Layout mejorado con ConstraintLayout
✅ Diseño futurístico en toda la app
✅ MediaPlayer funcional y optimizado
✅ Efectos de audio profesionales
✅ Manejo completo de ciclo de vida
✅ UI responsiva y atractiva
✅ Validación de entrada de usuario
✅ Manejo de excepciones

---

## 🔄 Estado de Compilación

✅ **BUILD SUCCESSFUL**
- 95 tareas ejecutadas exitosamente
- 0 errores
- Tiempo de compilación: 1m 41s

---

## 📝 Notas de Desarrollo

### Consideraciones Futuras
- Implementar FFmpeg para mejor quality en audio processing
- Agregar grabación de audio en tiempo real
- Crear playlist personalizada
- Sincronización con plataformas de música
- Interfaz para ecualizador gráfico
- Soporte para más formatos de audio

### Optimizaciones Realizadas
- Manejo eficiente de memoria (MediaPlayer.release())
- Loading progresivo de canciones
- Interfaz responsiva sin bloqueos
- Manejo de permisos Runtime
- Caché de RecyclerView

---

## 🏆 Conclusión

CUICATL es una aplicación completa, funcional y moderna que demuestra:
- Arquitectura Android profesional
- Diseño UX/UI futurístico
- Procesamiento de audio en tiempo real
- Buenas prácticas de desarrollo
- Código limpio y mantenible

La app está lista para compilación y ejecución en dispositivos Android 9.0+.

