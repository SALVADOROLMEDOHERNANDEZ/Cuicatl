package com.example.cuicatl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.example.cuicatl.models.Song
import java.io.File

class MusicService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    
    private val editorTracks = mutableListOf<EditorTrack>()
    private var activeTrackIndex = -1

    data class EditorTrack(
        val song: Song,
        val player: MediaPlayer,
        var equalizer: Equalizer? = null,
        var reverb: PresetReverb? = null
    )

    var songs: List<Song> = mutableListOf()
    var currentIndex: Int = -1
    var isRepeating = false
    var isPlaying = false

    private val CHANNEL_ID = "cuicatl_channel"
    private val NOTIFICATION_ID = 1

    companion object {
        const val ACTION_PREVIOUS = "com.example.cuicatl.PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.example.cuicatl.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.cuicatl.NEXT"
    }

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

    fun addTrack(song: Song) {
        try {
            val player = MediaPlayer().apply {
                setDataSource(song.path)
                prepare()
            }
            val track = EditorTrack(song, player)
            editorTracks.add(track)
            if (activeTrackIndex == -1) activeTrackIndex = 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getEditorTracks(): List<Song> = editorTracks.map { it.song }

    fun selectTrack(index: Int) {
        if (index in editorTracks.indices) {
            activeTrackIndex = index
        }
    }

    fun getActiveTrack(): Song? = if (activeTrackIndex != -1) editorTracks[activeTrackIndex].song else null

    fun getActivePlayer(): MediaPlayer? {
        return if (activeTrackIndex != -1) editorTracks[activeTrackIndex].player else mediaPlayer
    }

    fun applyVolume(volume: Float) {
        getActivePlayer()?.setVolume(volume, volume)
    }

    fun applyPlaybackParams(pitch: Float, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getActivePlayer()?.let {
                try {
                    val params = PlaybackParams()
                    params.pitch = pitch.coerceIn(0.5f, 2.0f)
                    params.speed = speed.coerceIn(0.5f, 2.0f)
                    it.playbackParams = params
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun setEqualizerBand(band: Int, level: Short) {
        if (activeTrackIndex == -1) return
        val track = editorTracks[activeTrackIndex]
        if (track.equalizer == null) {
            track.equalizer = Equalizer(0, track.player.audioSessionId).apply { enabled = true }
        }
        try {
            track.equalizer?.setBandLevel(band.toShort(), level)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun toggleReverb(enabled: Boolean) {
        if (activeTrackIndex == -1) return
        val track = editorTracks[activeTrackIndex]
        if (track.reverb == null) {
            track.reverb = PresetReverb(0, track.player.audioSessionId)
        }
        track.reverb?.enabled = enabled
        if (enabled) {
            track.reverb?.preset = PresetReverb.PRESET_LARGEHALL
        }
    }

    fun setPlaylist(newSongs: List<Song>, startIndex: Int) {
        activeTrackIndex = -1
        songs = newSongs
        currentIndex = startIndex
        playCurrent()
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
                    if (isRepeating) { seekTo(0); start() } else { playNext() }
                }
            }
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
        }
    }

    fun resumeSong() {
        getActivePlayer()?.start()
        isPlaying = true
        if (activeTrackIndex == -1) showNotification()
    }

    fun pauseSong() {
        getActivePlayer()?.pause()
        isPlaying = false
        if (activeTrackIndex == -1) showNotification()
    }

    fun playNext() {
        if (activeTrackIndex != -1) return
        if (songs.isEmpty()) return
        currentIndex = (currentIndex + 1) % songs.size
        playCurrent()
    }

    fun playPrevious() {
        if (activeTrackIndex != -1) return
        if (songs.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) songs.size - 1 else currentIndex - 1
        playCurrent()
    }

    fun getDuration(): Int = getActivePlayer()?.duration ?: 0
    fun getCurrentPosition(): Int = getActivePlayer()?.currentPosition ?: 0
    fun seekTo(pos: Int) { getActivePlayer()?.seekTo(pos) }
    
    fun getCurrentSong(): Song? {
        return if (activeTrackIndex != -1) editorTracks[activeTrackIndex].song 
        else if (currentIndex in songs.indices) songs[currentIndex] 
        else null
    }

    private fun showNotification() {
        val song = getCurrentSong() ?: return
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val prevPending = PendingIntent.getService(this, 0, Intent(this, MusicService::class.java).apply { action = ACTION_PREVIOUS }, PendingIntent.FLAG_IMMUTABLE)
        val playPausePending = PendingIntent.getService(this, 1, Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }, PendingIntent.FLAG_IMMUTABLE)
        val nextPending = PendingIntent.getService(this, 2, Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val albumArt: Bitmap? = if (song.coverUri != null && File(song.coverUri!!).exists()) BitmapFactory.decodeFile(song.coverUri) else null

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_play)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(pendingIntent)
            .setStyle(MediaNotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (albumArt != null) notificationBuilder.setLargeIcon(albumArt)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIOUS -> playPrevious()
            ACTION_PLAY_PAUSE -> if (isPlaying) pauseSong() else resumeSong()
            ACTION_NEXT -> playNext()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        editorTracks.forEach { it.player.release() }
    }
}
