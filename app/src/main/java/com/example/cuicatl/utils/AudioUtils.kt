package com.example.cuicatl.utils

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object AudioUtils {
    
    // Configuración estándar para procesamiento PCM (Simulado para WAV)
    private const val SAMPLE_RATE = 44100
    private const val BYTES_PER_SAMPLE = 2

    fun trimAudio(path: String, startMs: Int, endMs: Int): String? {
        return try {
            val file = File(path)
            val outputFile = File(file.parent, "trimmed_${System.currentTimeMillis()}_${file.name}")

            val audioBytes = file.readBytes()
            // Saltar cabecera WAV simple (44 bytes aprox) si existe, o procesar todo si es raw
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

    fun addEcho(path: String): String? {
        return try {
            val file = File(path)
            val outputFile = File(file.parent, "echo_${file.name}")
            val audioBytes = file.readBytes()
            val echoBytes = audioBytes.copyOf()

            val delayMs = 300
            val delaySizeBytes = (delayMs / 1000f * SAMPLE_RATE).toInt() * BYTES_PER_SAMPLE

            for (i in (delaySizeBytes + 44) until echoBytes.size step 2) {
                val sample = getShort(echoBytes, i)
                val prevSample = getShort(echoBytes, i - delaySizeBytes)
                val mixed = (sample + (prevSample * 0.5f)).toInt().coerceIn(-32768, 32767)
                setShort(echoBytes, i, mixed.toShort())
            }

            FileOutputStream(outputFile).use { it.write(echoBytes) }
            outputFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun addReverb(path: String): String? {
        return try {
            val file = File(path)
            val outputFile = File(file.parent, "reverb_${file.name}")
            val audioBytes = file.readBytes()
            val reverbBytes = audioBytes.copyOf()

            val delays = intArrayOf(80, 150, 250)
            val decay = 0.3f

            for (delayMs in delays) {
                val delaySize = (delayMs / 1000f * SAMPLE_RATE).toInt() * BYTES_PER_SAMPLE
                for (i in (delaySize + 44) until reverbBytes.size step 2) {
                    val sample = getShort(reverbBytes, i)
                    val delayedSample = getShort(audioBytes, i - delaySize)
                    val mixed = (sample + (delayedSample * decay)).toInt().coerceIn(-32768, 32767)
                    setShort(reverbBytes, i, mixed.toShort())
                }
            }

            FileOutputStream(outputFile).use { it.write(reverbBytes) }
            outputFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun normalizeAudio(path: String): String? {
        return try {
            val file = File(path)
            val outputFile = File(file.parent, "norm_${file.name}")
            val audioBytes = file.readBytes()
            
            var max = 0
            for (i in 44 until audioBytes.size step 2) {
                val s = Math.abs(getShort(audioBytes, i).toInt())
                if (s > max) max = s
            }

            if (max > 0) {
                val multiplier = 32767f / max
                for (i in 44 until audioBytes.size step 2) {
                    val s = (getShort(audioBytes, i) * multiplier).toInt().coerceIn(-32768, 32767)
                    setShort(audioBytes, i, s.toShort())
                }
            }

            FileOutputStream(outputFile).use { it.write(audioBytes) }
            outputFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // Nueva función de mezcla (Mixing)
    fun mixAudio(path1: String, path2: String): String? {
        return try {
            val file1 = File(path1)
            val file2 = File(path2)
            val data1 = file1.readBytes()
            val data2 = file2.readBytes()
            
            val outputSize = Math.max(data1.size, data2.size)
            val mixedData = ByteArray(outputSize)
            
            // Copiar cabecera del primero
            System.arraycopy(data1, 0, mixedData, 0, Math.min(44, data1.size))

            for (i in 44 until outputSize step 2) {
                val s1 = if (i < data1.size) getShort(data1, i) else 0
                val s2 = if (i < data2.size) getShort(data2, i) else 0
                val mixed = ((s1 + s2) / 2).toShort() // Mezcla simple 50/50
                setShort(mixedData, i, mixed)
            }

            val outputFile = File(file1.parent, "mix_${System.currentTimeMillis()}.wav")
            FileOutputStream(outputFile).use { it.write(mixedData) }
            outputFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun saveAudio(path: String, filename: String): Boolean {
        return try {
            val sourceFile = File(path)
            val musicDir = File("/sdcard/Music/CUICATL_PRO")
            if (!musicDir.exists()) musicDir.mkdirs()

            val outputFile = File(musicDir, "${filename}_final.wav")
            sourceFile.copyTo(outputFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
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
