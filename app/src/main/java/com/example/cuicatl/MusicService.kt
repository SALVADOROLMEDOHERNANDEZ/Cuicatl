package com.example.cuicatl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.cuicatl.models.Song

class MusicService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    
    var songs: List<Song> = mutableListOf()
    var currentIndex: Int = -1
    var isRepeating = false
    var isPlaying = false

    private val CHANNEL_ID = "cuicatl_channel"
    private val NOTIFICATION_ID = 1

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CUICATL Music Control",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun setPlaylist(newSongs: List<Song>, startIndex: Int) {
        songs = newSongs
        currentIndex = startIndex
        playCurrent()
    }

    fun playSong(path: String) {
        val index = songs.indexOfFirst { it.path == path }
        if (index != -1) {
            currentIndex = index
            playCurrent()
        } else {
            startNewPlayer(path)
        }
    }

    private fun playCurrent() {
        if (currentIndex < 0 || currentIndex >= songs.size) return
        startNewPlayer(songs[currentIndex].path)
        showNotification()
    }

    private fun startNewPlayer(path: String) {
        mediaPlayer?.stop()
        mediaPlayer?.release()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    if (isRepeating) {
                        seekTo(0)
                        start()
                    } else {
                        playNext()
                    }
                }
            }
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
        }
    }

    fun resumeSong() {
        mediaPlayer?.start()
        isPlaying = true
        showNotification()
    }

    fun pauseSong() {
        mediaPlayer?.pause()
        isPlaying = false
        showNotification()
    }

    fun playNext() {
        if (songs.isEmpty()) return
        currentIndex = (currentIndex + 1) % songs.size
        playCurrent()
    }

    fun playPrevious() {
        if (songs.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) songs.size - 1 else currentIndex - 1
        playCurrent()
    }

    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun seekTo(pos: Int) { mediaPlayer?.seekTo(pos) }
    
    fun getCurrentSong(): Song? {
        return if (currentIndex in songs.indices) songs[currentIndex] else null
    }

    private fun showNotification() {
        val song = getCurrentSong() ?: return
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_play)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(pendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle())
            .addAction(android.R.drawable.ic_media_previous, "Previous", null) // Needs actual intents
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", null) // Needs actual intents
            .addAction(android.R.drawable.ic_media_next, "Next", null) // Needs actual intents
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle actions from notification here if needed
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}
