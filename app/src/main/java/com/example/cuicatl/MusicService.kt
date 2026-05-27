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
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
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
        var reverb: PresetReverb? = null,
        var bassBoost: BassBoost? = null,
        var virtualizer: Virtualizer? = null,
        var loudness: LoudnessEnhancer? = null,
        var volume: Float = 1f,
        var pitch: Float = 1f,
        var tempo: Float = 1f,
        var reverbPreset: Short = PresetReverb.PRESET_LARGEHALL
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

        // EQ presets como ganancias por banda en dB (10 bandas)
        val PRESET_FLAT = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val PRESET_BASS = floatArrayOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f)
        val PRESET_TREBLE = floatArrayOf(0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f, 7f)
        val PRESET_VSHAPE = floatArrayOf(6f, 5f, 3f, 0f, -2f, -2f, 0f, 3f, 5f, 6f)
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

    fun removeTrack(index: Int) {
        if (index !in editorTracks.indices) return
        val t = editorTracks[index]
        try {
            t.equalizer?.release()
            t.reverb?.release()
            t.bassBoost?.release()
            t.virtualizer?.release()
            t.loudness?.release()
            t.player.release()
        } catch (_: Exception) {}
        editorTracks.removeAt(index)
        if (editorTracks.isEmpty()) activeTrackIndex = -1
        else if (activeTrackIndex >= editorTracks.size) activeTrackIndex = editorTracks.size - 1
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

    fun getActiveEditorTrack(): EditorTrack? =
        if (activeTrackIndex != -1) editorTracks[activeTrackIndex] else null

    fun getActiveAudioSessionId(): Int =
        getActiveEditorTrack()?.player?.audioSessionId ?: mediaPlayer?.audioSessionId ?: 0

    fun isEditorMode(): Boolean = activeTrackIndex != -1

    fun playEditorTrack() {
        getActiveEditorTrack()?.let {
            try {
                it.player.start(); isPlaying = true
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun pauseEditorTrack() {
        getActiveEditorTrack()?.let {
            try { it.player.pause(); isPlaying = false } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun seekEditor(ms: Int) {
        try { getActiveEditorTrack()?.player?.seekTo(ms) } catch (_: Exception) {}
    }

    fun applyVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        getActiveEditorTrack()?.let {
            it.volume = v
            it.player.setVolume(v, v)
        } ?: run {
            mediaPlayer?.setVolume(v, v)
        }
    }

    fun applyPlaybackParams(pitch: Float, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getActivePlayer()?.let {
                try {
                    val wasPlaying = it.isPlaying
                    val params = PlaybackParams()
                    params.pitch = pitch.coerceIn(0.5f, 2.0f)
                    params.speed = speed.coerceIn(0.5f, 2.0f)
                    it.playbackParams = params
                    if (!wasPlaying) it.pause()
                    getActiveEditorTrack()?.apply {
                        this.pitch = params.pitch
                        this.tempo = params.speed
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun ensureEqualizer(t: EditorTrack): Equalizer? {
        if (t.equalizer == null) {
            try {
                t.equalizer = Equalizer(0, t.player.audioSessionId).apply { enabled = true }
            } catch (e: Exception) { e.printStackTrace() }
        }
        return t.equalizer
    }

    fun setEqualizerBand(band: Int, levelMillibel: Short) {
        val t = getActiveEditorTrack() ?: return
        val eq = ensureEqualizer(t) ?: return
        try {
            val range = eq.bandLevelRange
            val safe = levelMillibel.toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
            eq.setBandLevel(band.toShort(), safe)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun applyEqPreset(gainsDb: FloatArray) {
        val t = getActiveEditorTrack() ?: return
        val eq = ensureEqualizer(t) ?: return
        try {
            val range = eq.bandLevelRange
            val bands = eq.numberOfBands.toInt()
            for (i in 0 until bands) {
                val gain = if (i < gainsDb.size) gainsDb[i] else 0f
                val mb = (gain * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                eq.setBandLevel(i.toShort(), mb)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getEqualizerLevels(): ShortArray? {
        val t = getActiveEditorTrack() ?: return null
        val eq = ensureEqualizer(t) ?: return null
        return try {
            val n = eq.numberOfBands.toInt()
            ShortArray(n) { eq.getBandLevel(it.toShort()) }
        } catch (e: Exception) { null }
    }

    fun toggleReverb(enabled: Boolean) {
        val t = getActiveEditorTrack() ?: return
        if (t.reverb == null) {
            try {
                t.reverb = PresetReverb(0, t.player.audioSessionId)
            } catch (e: Exception) { e.printStackTrace(); return }
        }
        try {
            t.reverb?.preset = t.reverbPreset
            t.reverb?.enabled = enabled
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun setReverbPreset(preset: Short) {
        val t = getActiveEditorTrack() ?: return
        t.reverbPreset = preset
        try { t.reverb?.preset = preset } catch (_: Exception) {}
    }

    fun toggleBassBoost(enabled: Boolean, strength: Short = 800) {
        val t = getActiveEditorTrack() ?: return
        if (t.bassBoost == null) {
            try {
                t.bassBoost = BassBoost(0, t.player.audioSessionId)
            } catch (e: Exception) { e.printStackTrace(); return }
        }
        try {
            t.bassBoost?.setStrength(strength.toInt().coerceIn(0, 1000).toShort())
            t.bassBoost?.enabled = enabled
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun setBassBoostStrength(strength: Short) {
        val t = getActiveEditorTrack() ?: return
        try {
            t.bassBoost?.setStrength(strength.toInt().coerceIn(0, 1000).toShort())
        } catch (_: Exception) {}
    }

    fun toggleVirtualizer(enabled: Boolean, strength: Short = 800) {
        val t = getActiveEditorTrack() ?: return
        if (t.virtualizer == null) {
            try {
                t.virtualizer = Virtualizer(0, t.player.audioSessionId)
            } catch (e: Exception) { e.printStackTrace(); return }
        }
        try {
            t.virtualizer?.setStrength(strength.toInt().coerceIn(0, 1000).toShort())
            t.virtualizer?.enabled = enabled
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun setVirtualizerStrength(strength: Short) {
        val t = getActiveEditorTrack() ?: return
        try { t.virtualizer?.setStrength(strength.toInt().coerceIn(0, 1000).toShort()) } catch (_: Exception) {}
    }

    fun toggleLoudness(enabled: Boolean, gainMb: Int = 700) {
        val t = getActiveEditorTrack() ?: return
        if (t.loudness == null) {
            try {
                t.loudness = LoudnessEnhancer(t.player.audioSessionId)
            } catch (e: Exception) { e.printStackTrace(); return }
        }
        try {
            t.loudness?.setTargetGain(gainMb.coerceIn(0, 2000))
            t.loudness?.enabled = enabled
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun setLoudnessGain(gainMb: Int) {
        val t = getActiveEditorTrack() ?: return
        try { t.loudness?.setTargetGain(gainMb.coerceIn(0, 2000)) } catch (_: Exception) {}
    }

    fun resetActiveEffects() {
        val t = getActiveEditorTrack() ?: return
        try {
            t.equalizer?.let { eq ->
                val n = eq.numberOfBands.toInt()
                for (i in 0 until n) eq.setBandLevel(i.toShort(), 0)
            }
            t.reverb?.enabled = false
            t.bassBoost?.enabled = false
            t.virtualizer?.enabled = false
            t.loudness?.enabled = false
            t.volume = 1f
            t.pitch = 1f
            t.tempo = 1f
            t.player.setVolume(1f, 1f)
            applyPlaybackParams(1f, 1f)
        } catch (e: Exception) { e.printStackTrace() }
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
        if (activeTrackIndex != -1) {
            // Modo editor: avanzar entre pistas del editor
            if (editorTracks.isEmpty()) return
            activeTrackIndex = (activeTrackIndex + 1) % editorTracks.size
            return
        }
        if (songs.isEmpty()) return
        currentIndex = (currentIndex + 1) % songs.size
        playCurrent()
    }

    fun playPrevious() {
        if (activeTrackIndex != -1) {
            if (editorTracks.isEmpty()) return
            activeTrackIndex = if (activeTrackIndex - 1 < 0) editorTracks.size - 1 else activeTrackIndex - 1
            return
        }
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

    fun replaceActiveTrackSource(newPath: String) {
        val t = getActiveEditorTrack() ?: return
        try {
            val wasPlaying = t.player.isPlaying
            t.player.reset()
            t.player.setDataSource(newPath)
            t.player.prepare()
            // efectos atados al sessionId siguen funcionando porque el sessionId no cambia con reset()
            if (wasPlaying) t.player.start()
        } catch (e: Exception) { e.printStackTrace() }
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
        editorTracks.forEach { t ->
            try {
                t.equalizer?.release()
                t.reverb?.release()
                t.bassBoost?.release()
                t.virtualizer?.release()
                t.loudness?.release()
                t.player.release()
            } catch (_: Exception) {}
        }
    }
}
