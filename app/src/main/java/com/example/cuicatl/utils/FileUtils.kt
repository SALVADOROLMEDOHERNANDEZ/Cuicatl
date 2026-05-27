package com.example.cuicatl.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import com.example.cuicatl.models.Song
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    fun getAllSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        return try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val titleIndex = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistIndex = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val pathIndex = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                val nameIndex = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

                while (it.moveToNext()) {
                    val path = it.getString(pathIndex)
                    if (!path.isNullOrEmpty() && File(path).exists()) {
                        val title = it.getString(titleIndex) ?: it.getString(nameIndex) ?: "Sin título"
                        val artist = it.getString(artistIndex) ?: "Artista desconocido"
                        
                        // Intentar obtener carátula embebida primero
                        val embeddedCover = getEmbeddedPicturePath(context, path)
                        
                        songs.add(Song(title, artist, path, coverUri = embeddedCover))
                    }
                }
            }
            songs
        } catch (e: Exception) {
            e.printStackTrace()
            songs
        }
    }

    private fun getEmbeddedPicturePath(context: Context, filePath: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val art = retriever.embeddedPicture
            if (art != null) {
                // Guardar temporalmente para que Glide pueda usar una URI
                val cacheFile = File(context.cacheDir, "art_${filePath.hashCode()}.jpg")
                if (!cacheFile.exists()) {
                    FileOutputStream(cacheFile).use { it.write(art) }
                }
                cacheFile.absolutePath
            } else null
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    fun getMusicFolders(context: Context): List<String> {
        val folders = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )

            cursor?.use {
                val pathIndex = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                while (it.moveToNext()) {
                    val path = it.getString(pathIndex)
                    val file = File(path)
                    file.parent?.let { parent -> folders.add(parent) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return folders.toList().sorted()
    }
}
