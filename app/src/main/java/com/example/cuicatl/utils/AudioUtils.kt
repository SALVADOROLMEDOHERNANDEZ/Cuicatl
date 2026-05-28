package com.example.cuicatl.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

object AudioUtils {
    
    private const val SAMPLE_RATE = 44100
    private const val BYTES_PER_SAMPLE = 2

    fun getDurationMs(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (e: Exception) { 0L } finally { retriever.release() }
    }

    fun trimAudio(path: String, startMs: Long, endMs: Long): String? {
        return try {
            val file = File(path)
            val outputFile = File(file.parent, "trimmed_${System.currentTimeMillis()}.wav")

            val audioBytes = file.readBytes()
            val headerOffset = 44 
            val startByte = headerOffset + (startMs / 1000f * SAMPLE_RATE * BYTES_PER_SAMPLE).toInt()
            val endByte = min(headerOffset + (endMs / 1000f * SAMPLE_RATE * BYTES_PER_SAMPLE).toInt(), audioBytes.size)

            if (startByte < endByte) {
                val trimmedData = audioBytes.sliceArray(startByte until endByte)
                FileOutputStream(outputFile).use { it.write(trimmedData) }
                outputFile.absolutePath
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportToMusic(context: Context, sourcePath: String, displayName: String): Uri? {
        val resolver = context.contentResolver
        val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val details = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/CUICATL")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(audioCollection, details) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { output ->
                File(sourcePath).inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                details.clear()
                details.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, details, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    private fun getShort(ba: ByteArray, i: Int): Short {
        if (i < 0 || i + 1 >= ba.size) return 0
        return ((ba[i].toInt() and 0xFF) or (ba[i + 1].toInt() shl 8)).toShort()
    }

    private fun setShort(ba: ByteArray, i: Int, s: Short) {
        if (i < 0 || i + 1 >= ba.size) return
        ba[i] = (s.toInt() and 0xFF).toByte()
        ba[i + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
    }
}
