package com.example.cuicatl.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

object AudioUtils {

    /**
     * Recorte real (lossless) de audio usando MediaExtractor + MediaMuxer.
     * Soporta MP3, M4A/AAC, WAV (PCM), OGG. El contenedor de salida se elige según el MIME.
     * Devuelve la ruta absoluta del archivo recortado o null en caso de error.
     */
    fun trimAudio(srcPath: String, startMs: Long, endMs: Long): String? {
        if (endMs <= startMs) return null
        val srcFile = File(srcPath)
        if (!srcFile.exists()) return null

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(srcPath)

            // Encontrar la primera pista de audio
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null

            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            val outFormat = when {
                mime.contains("mp4a-latm") || mime.contains("aac") || mime.contains("mp4") -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                mime.contains("3gpp") -> MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP
                mime.contains("ogg") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 // default container
            }
            val ext = when (outFormat) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP -> "3gp"
                MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG -> "ogg"
                else -> "m4a"
            }

            val outFile = File(srcFile.parentFile ?: File(srcFile.parent ?: "."),
                "trim_${System.currentTimeMillis()}_${srcFile.nameWithoutExtension}.$ext")
            muxer = MediaMuxer(outFile.absolutePath, outFormat)
            val outTrack = muxer.addTrack(format)
            muxer.start()

            // Seek al startMs
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val bufferSize = format.getIntegerOrDefault(MediaFormat.KEY_MAX_INPUT_SIZE, 1 shl 18)
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = android.media.MediaCodec.BufferInfo()

            val endUs = endMs * 1000L
            val startUs = startMs * 1000L
            var firstSampleTimeUs = -1L

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0) break
                if (sampleTimeUs > endUs) break

                if (firstSampleTimeUs < 0) firstSampleTimeUs = sampleTimeUs

                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = sampleTimeUs - startUs.coerceAtMost(firstSampleTimeUs)
                if (info.presentationTimeUs < 0) info.presentationTimeUs = 0
                info.flags = extractor.sampleFlags

                muxer.writeSampleData(outTrack, buffer, info)
                if (!extractor.advance()) break
            }

            return outFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
        try { if (containsKey(key)) getInteger(key) else default } catch (_: Exception) { default }

    /**
     * Duración del audio en milisegundos.
     */
    fun getDurationMs(path: String): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
        finally { r.release() }
    }

    /**
     * Exporta a la carpeta pública Music/CUICATL usando MediaStore en Android 10+,
     * o copia directa en versiones anteriores.
     */
    fun exportToMusic(context: Context, srcPath: String, displayName: String): Uri? {
        val src = File(srcPath)
        if (!src.exists()) return null
        val mime = when (src.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "mp4", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "3gp" -> "audio/3gpp"
            else -> "audio/*"
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, mime)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/CUICATL")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(uri).use { out ->
                    src.inputStream().use { it.copyTo(out!!) }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "CUICATL")
                if (!musicDir.exists()) musicDir.mkdirs()
                val outFile = File(musicDir, displayName)
                src.inputStream().use { input ->
                    FileOutputStream(outFile).use { input.copyTo(it) }
                }
                Uri.fromFile(outFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
