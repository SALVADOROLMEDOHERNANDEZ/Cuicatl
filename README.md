# CUICATL - Reproductor y Editor de Audio Profesional

## Descripción
Aplicación Android moderna con reproductor de música integrado y editor de audio profesional. Diseño futurístico con tema oscuro.

## Requisitos
- Android 9.0 (SDK 28) o superior
- Mínimo 100MB de almacenamiento libre
- Permisos de acceso a archivos multimedia

## Instalación

Compilar desde código:
```
cd CUICATL
./gradlew build
```

## Uso

### Pantalla Principal
- La app carga automáticamente todas tus canciones
- Desliza para ver más canciones
- Toca cualquier canción para reproducir

### Reproductor
- Play: Inicia la reproducción
- Pause: Pausa la canción
- Stop: Detiene la reproducción
- SeekBar: Desliza para ir a un momento específico
- Editar Audio: Abre el editor profesional

### Editor de Audio

Recortar Audio:
- Ingresa tiempo de inicio (en milisegundos)
- Ingresa tiempo de fin
- Toca Recortar

Aplicar Efectos:
- Eco: Efecto de reverberación corta
- Reverberación: Efecto ambiental profesional
- Normalizar: Optimiza volumen automáticamente

Guardar Cambios:
- Toca Guardar para guardar en la carpeta de Música
- El archivo se guarda con prefijo CUICATL_

## Características
- Colores neón (azul cian y púrpura)
- Tema oscuro profesional
- Material Design 3
- Iconos intuitivos
- Bordes redondeados en elementos

## Permisos
La app solicita:
- READ_EXTERNAL_STORAGE: Para acceder a canciones
- RECORD_AUDIO: Para procesamiento de audio
- WRITE_EXTERNAL_STORAGE: Para guardar editaciones

## Notas
- Los cambios de audio se guardan como copias
- No se modifican archivos originales
- Los tiempos están en milisegundos (1000ms = 1 segundo)
- La app carga canciones dinámicamente del dispositivo

## Especificaciones
- Mínimo SDK: 28 (Android 9.0)
- Target SDK: 36 (Android 15)
- RAM Recomendado: 2GB+
- Almacenamiento: 100MB+ libre

## Lenguaje
- Kotlin
- Android APIs
- Material Design 3

CUICATL v1.0 - Reproductor de Audio Profesional

