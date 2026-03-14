package com.example.cuicatl.utils

import android.content.Context
import android.provider.MediaStore
import com.example.cuicatl.models.Song

object FileUtils {
    fun getAllSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        return try {
            val projection = arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
            )

            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null
            )

            cursor?.use {
                val titleIndex = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistIndex = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val pathIndex = it.getColumnIndex(MediaStore.Audio.Media.DATA)

                if (titleIndex >= 0 && artistIndex >= 0 && pathIndex >= 0) {
                    while (it.moveToNext()) {
                        val title = it.getString(titleIndex)
                        val artist = it.getString(artistIndex)
                        val path = it.getString(pathIndex)
                        if (!path.isNullOrEmpty()) {
                            songs.add(Song(title, artist, path))
                        }
                    }
                }
            }
            songs
        } catch (e: Exception) {
            e.printStackTrace()
            songs
        }
    }
}
