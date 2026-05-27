package com.example.cuicatl

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.cuicatl.adapters.TrackTabAdapter
import com.example.cuicatl.databinding.ActivityEditorBinding
import com.example.cuicatl.models.Song
import com.example.cuicatl.utils.AudioUtils
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private var musicService: MusicService? = null
    private var isBound = false
    private lateinit var trackAdapter: TrackTabAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var fadeMs: Int = 0
    private var trackDurationMs: Long = 0L

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = copyFileToInternalStorage(uri)
                if (path != null) {
                    val displayName = File(path).nameWithoutExtension
                    val newSong = Song(displayName, "Importado", path)
                    musicService?.addTrack(newSong)
                    updateTrackListUI()
                    Toast.makeText(this, "Pista añadida: $displayName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No se pudo importar el archivo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            setupTrackList()
            setupEqualizerBands()
            attachVisualizer()
            updateEditorUI()
        }
        override fun onServiceDisconnected(name: ComponentName?) { isBound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindService(Intent(this, MusicService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

        setupListeners()
        setupTrimRange()
        animateEntry()
    }

    private fun animateEntry() {
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in).apply { duration = 450 }
        binding.llEditorHeader.startAnimation(fadeIn)
    }

    private fun attachVisualizer() {
        val sessionId = musicService?.getActiveAudioSessionId() ?: 0
        if (sessionId != 0) binding.waveform.attach(sessionId)
    }

    private fun setupTrackList() {
        val tracks = musicService?.getEditorTracks() ?: listOf()
        trackAdapter = TrackTabAdapter(tracks, 0) { index ->
            musicService?.selectTrack(index)
            attachVisualizer()
            updateEditorUI()
        }
        binding.rvTrackSelector.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvTrackSelector.adapter = trackAdapter
    }

    private fun updateTrackListUI() {
        val tracks = musicService?.getEditorTracks() ?: listOf()
        val lastIndex = if (tracks.isNotEmpty()) tracks.size - 1 else 0
        trackAdapter = TrackTabAdapter(tracks, lastIndex) { index ->
            musicService?.selectTrack(index)
            attachVisualizer()
            updateEditorUI()
        }
        binding.rvTrackSelector.adapter = trackAdapter
        musicService?.selectTrack(lastIndex)
        attachVisualizer()
        updateEditorUI()
    }

    private fun setupListeners() {
        binding.btnEditorBack.setOnClickListener { finish() }

        binding.tabEqualizer.setOnClickListener { showSection(0) }
        binding.tabEffects.setOnClickListener { showSection(1) }
        binding.tabTrim.setOnClickListener { showSection(2) }

        binding.btnAddTrack.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE) }
            filePicker.launch(intent)
        }

        binding.btnReset.setOnClickListener {
            musicService?.resetActiveEffects()
            resetUIControls()
            Toast.makeText(this, "Ajustes restablecidos", Toast.LENGTH_SHORT).show()
        }

        binding.btnEditorPlay.setOnClickListener {
            val svc = musicService ?: return@setOnClickListener
            if (svc.isEditorMode()) {
                if (svc.isPlaying) svc.pauseEditorTrack() else { svc.playEditorTrack(); applyFadeIn() }
            } else {
                if (svc.isPlaying) svc.pauseSong() else svc.resumeSong()
            }
            updatePlayPauseIcon()
        }
        binding.btnEditorPrev.setOnClickListener {
            musicService?.playPrevious()
            attachVisualizer()
            updateEditorUI()
        }
        binding.btnEditorNext.setOnClickListener {
            musicService?.playNext()
            attachVisualizer()
            updateEditorUI()
        }

        // MASTER CONTROLS
        binding.effectsLayout.sbVolume.setOnSeekBarChangeListener(simpleSeek { _, p, _ ->
            musicService?.applyVolume(p / 100f)
        })
        binding.effectsLayout.sbPitch.setOnSeekBarChangeListener(simpleSeek { _, p, _ ->
            val pitch = 0.5f + (p / 50f)
            val tempo = 0.5f + (binding.effectsLayout.sbTempo.progress / 50f)
            musicService?.applyPlaybackParams(pitch, tempo)
        })
        binding.effectsLayout.sbTempo.setOnSeekBarChangeListener(simpleSeek { _, p, _ ->
            val tempo = 0.5f + (p / 50f)
            val pitch = 0.5f + (binding.effectsLayout.sbPitch.progress / 50f)
            musicService?.applyPlaybackParams(pitch, tempo)
        })

        // REVERB switch (incluido genérico)
        binding.effectsLayout.effectReverb.swEffect.setOnCheckedChangeListener { _, isChecked ->
            musicService?.toggleReverb(isChecked)
        }
        binding.effectsLayout.effectReverb.tvEffectName.text = "Reverb (Sala grande)"
        binding.effectsLayout.effectReverb.tvEffectValue.text = "Preset Hall"

        // BASS BOOST
        binding.effectsLayout.swBassBoost.setOnCheckedChangeListener { _, isChecked ->
            musicService?.toggleBassBoost(isChecked, binding.effectsLayout.sbBassStrength.progress.toShort())
        }
        binding.effectsLayout.sbBassStrength.setOnSeekBarChangeListener(simpleSeek { _, p, _ ->
            musicService?.setBassBoostStrength(p.toShort())
        })

        // VIRTUALIZER
        binding.effectsLayout.swVirtualizer.setOnCheckedChangeListener { _, isChecked ->
            musicService?.toggleVirtualizer(isChecked, binding.effectsLayout.sbVirtualizerStrength.progress.toShort())
        }
        binding.effectsLayout.sbVirtualizerStrength.setOnSeekBarChangeListener(simpleSeek { _, p, _ ->
            musicService?.setVirtualizerStrength(p.toShort())
        })

        // LOUDNESS
        binding.effectsLayout.swLoudness.setOnCheckedChangeListener { _, isChecked ->
            musicService?.toggleLoudness(isChecked, binding.effectsLayout.sbLoudness.progress)
        }
        binding.effectsLayout.sbLoudness.setOnSeekBarChangeListener(simpleSeek { _, p, _ ->
            musicService?.setLoudnessGain(p)
        })

        // FADE
        binding.effectsLayout.sbFade.setOnSeekBarChangeListener(simpleSeek { _, p, _ ->
            fadeMs = p
            binding.effectsLayout.tvFadeValue.text = "Fade in/out: $p ms"
        })

        // EQ Presets
        binding.equalizerLayout.btnPresetFlat.setOnClickListener { applyPreset(MusicService.PRESET_FLAT, binding.equalizerLayout.btnPresetFlat) }
        binding.equalizerLayout.btnPresetBass.setOnClickListener { applyPreset(MusicService.PRESET_BASS, binding.equalizerLayout.btnPresetBass) }
        binding.equalizerLayout.btnPresetTreble.setOnClickListener { applyPreset(MusicService.PRESET_TREBLE, binding.equalizerLayout.btnPresetTreble) }
        binding.equalizerLayout.btnPresetVShape.setOnClickListener { applyPreset(MusicService.PRESET_VSHAPE, binding.equalizerLayout.btnPresetVShape) }

        // EXPORT
        binding.btnExportMaster.setOnClickListener { exportCurrent() }

        // TRIM
        binding.trimLayout.btnApplyTrim.setOnClickListener { applyTrim() }
    }

    private fun applyFadeIn() {
        if (fadeMs <= 0) return
        val svc = musicService ?: return
        val steps = 20
        val stepMs = fadeMs / steps
        for (i in 1..steps) {
            handler.postDelayed({ svc.applyVolume(i.toFloat() / steps) }, (i * stepMs).toLong())
        }
    }

    private fun applyPreset(preset: FloatArray, activeView: View) {
        musicService?.applyEqPreset(preset)
        // Reflejar en la UI de bandas
        refreshBandSliders()
        // Marcar tab activa
        val pills = listOf(
            binding.equalizerLayout.btnPresetFlat,
            binding.equalizerLayout.btnPresetBass,
            binding.equalizerLayout.btnPresetTreble,
            binding.equalizerLayout.btnPresetVShape
        )
        pills.forEach { it.isActivated = (it == activeView) }
    }

    private fun refreshBandSliders() {
        val levels = musicService?.getEqualizerLevels() ?: return
        val container = binding.equalizerLayout.llEqualizerBands
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val slider = child.findViewById<Slider>(R.id.sliderBand) ?: continue
            val tv = child.findViewById<TextView>(R.id.tvBandValue) ?: continue
            if (i < levels.size) {
                val db = (levels[i].toFloat() / 100f).coerceIn(slider.valueFrom, slider.valueTo)
                slider.value = db
                tv.text = "${db.toInt()} dB"
            }
        }
    }

    private fun showSection(idx: Int) {
        binding.equalizerLayout.root.visibility = if (idx == 0) View.VISIBLE else View.GONE
        binding.effectsLayout.root.visibility = if (idx == 1) View.VISIBLE else View.GONE
        binding.trimLayout.root.visibility = if (idx == 2) View.VISIBLE else View.GONE
        binding.tabEqualizer.isActivated = (idx == 0)
        binding.tabEffects.isActivated = (idx == 1)
        binding.tabTrim.isActivated = (idx == 2)
        binding.tabEqualizer.setTextColor(if (idx == 0) getColor(R.color.white) else getColor(R.color.text_muted))
        binding.tabEffects.setTextColor(if (idx == 1) getColor(R.color.white) else getColor(R.color.text_muted))
        binding.tabTrim.setTextColor(if (idx == 2) getColor(R.color.white) else getColor(R.color.text_muted))
    }

    private fun setupEqualizerBands() {
        val frequencies = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
        val container = binding.equalizerLayout.llEqualizerBands
        container.removeAllViews()
        for (i in frequencies.indices) {
            val bandView = LayoutInflater.from(this).inflate(R.layout.item_equalizer_band, container, false)
            val tvValue = bandView.findViewById<TextView>(R.id.tvBandValue)
            bandView.findViewById<TextView>(R.id.tvBandFreq).text = frequencies[i]
            val slider = bandView.findViewById<Slider>(R.id.sliderBand)
            slider.addOnChangeListener { _, value, fromUser ->
                tvValue.text = "${value.toInt()} dB"
                if (fromUser) musicService?.setEqualizerBand(i, (value * 100).toInt().toShort())
            }
            container.addView(bandView)
        }
    }

    private fun setupTrimRange() {
        binding.trimLayout.rangeTrim.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            if (values.size < 2 || trackDurationMs <= 0) return@addOnChangeListener
            val startMs = (values[0] / 100f * trackDurationMs).toLong()
            val endMs = (values[1] / 100f * trackDurationMs).toLong()
            binding.trimLayout.tvTrimStart.text = "Inicio: ${formatMs(startMs)}"
            binding.trimLayout.tvTrimEnd.text = "Fin: ${formatMs(endMs)}"
        }
    }

    private fun applyTrim() {
        val track = musicService?.getActiveTrack() ?: run {
            Toast.makeText(this, "No hay pista seleccionada", Toast.LENGTH_SHORT).show(); return
        }
        if (trackDurationMs <= 0) {
            Toast.makeText(this, "Duración no disponible", Toast.LENGTH_SHORT).show(); return
        }
        val values = binding.trimLayout.rangeTrim.values
        val startMs = (values[0] / 100f * trackDurationMs).toLong()
        val endMs = (values[1] / 100f * trackDurationMs).toLong()
        if (endMs - startMs < 200) {
            Toast.makeText(this, "Selecciona un rango mayor a 200ms", Toast.LENGTH_SHORT).show(); return
        }
        Toast.makeText(this, "Aplicando corte…", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            val newPath = withContext(Dispatchers.IO) {
                AudioUtils.trimAudio(track.path, startMs, endMs)
            }
            if (newPath != null) {
                musicService?.replaceActiveTrackSource(newPath)
                trackDurationMs = AudioUtils.getDurationMs(newPath)
                binding.trimLayout.rangeTrim.values = listOf(0f, 100f)
                attachVisualizer()
                Toast.makeText(this@EditorActivity, "Corte aplicado: ${File(newPath).name}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@EditorActivity, "No se pudo recortar este formato", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportCurrent() {
        val track = musicService?.getActiveTrack() ?: run {
            Toast.makeText(this, "No hay pista para exportar", Toast.LENGTH_SHORT).show(); return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val displayName = "CUICATL_${System.currentTimeMillis()}_${File(track.path).name}"
            val uri = withContext(Dispatchers.IO) {
                AudioUtils.exportToMusic(this@EditorActivity, track.path, displayName)
            }
            Toast.makeText(
                this@EditorActivity,
                if (uri != null) "Exportado a Música/CUICATL: $displayName"
                else "No se pudo exportar",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun resetUIControls() {
        binding.effectsLayout.sbVolume.progress = 100
        binding.effectsLayout.sbPitch.progress = 50
        binding.effectsLayout.sbTempo.progress = 50
        binding.effectsLayout.sbBassStrength.progress = 800
        binding.effectsLayout.sbVirtualizerStrength.progress = 800
        binding.effectsLayout.sbLoudness.progress = 700
        binding.effectsLayout.sbFade.progress = 0
        fadeMs = 0
        binding.effectsLayout.tvFadeValue.text = "Fade in/out: 0 ms"
        binding.effectsLayout.swBassBoost.isChecked = false
        binding.effectsLayout.swVirtualizer.isChecked = false
        binding.effectsLayout.swLoudness.isChecked = false
        binding.effectsLayout.effectReverb.swEffect.isChecked = false
        refreshBandSliders()
    }

    private fun updateEditorUI() {
        val song = musicService?.getActiveTrack()
        if (song != null) {
            binding.tvEditorSong.text = song.title
            binding.tvEditorArtist.text = song.artist
            binding.tvSmallTitle.text = song.title
            binding.tvSmallArtist.text = song.artist
            if (song.coverUri != null) {
                Glide.with(this).load(song.coverUri).centerCrop().into(binding.ivEditorArt)
                Glide.with(this).load(song.coverUri).centerCrop().into(binding.ivSmallArt)
            } else {
                binding.ivEditorArt.setImageResource(R.drawable.disc_visual)
                binding.ivSmallArt.setImageResource(R.drawable.disc_visual)
            }
            trackDurationMs = AudioUtils.getDurationMs(song.path)
            if (trackDurationMs > 0) {
                binding.trimLayout.tvTrimStart.text = "Inicio: 0:00"
                binding.trimLayout.tvTrimEnd.text = "Fin: ${formatMs(trackDurationMs)}"
            }
        } else {
            binding.tvEditorSong.text = getString(R.string.editor_no_track)
            binding.tvEditorArtist.text = getString(R.string.editor_select_track)
            binding.tvSmallTitle.text = "No Track"
            binding.tvSmallArtist.text = "Playback inactive"
        }
        updatePlayPauseIcon()
    }

    private fun updatePlayPauseIcon() {
        val playing = musicService?.isPlaying ?: false
        binding.btnEditorPlay.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun formatMs(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = ms / 60000
        return String.format("%d:%02d", m, s)
    }

    private fun copyFileToInternalStorage(uri: Uri): String? {
        return try {
            val (displayName, extension) = queryDisplayNameAndExt(uri)
            val safeName = displayName.ifBlank { "track_${System.currentTimeMillis()}" }
            val file = File(cacheDir, "${System.currentTimeMillis()}_$safeName.$extension")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { input.copyTo(it) }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace(); null
        }
    }

    private fun queryDisplayNameAndExt(uri: Uri): Pair<String, String> {
        var name = "track"
        var ext = "audio"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val full = c.getString(idx) ?: ""
                        if (full.isNotEmpty()) {
                            name = full.substringBeforeLast(".", full)
                            ext = full.substringAfterLast(".", "").ifBlank { mimeToExt(uri) }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        if (ext.isBlank() || ext == "audio") ext = mimeToExt(uri)
        return name to ext
    }

    private fun mimeToExt(uri: Uri): String {
        val mime = contentResolver.getType(uri) ?: return "m4a"
        return when {
            mime.contains("mpeg") -> "mp3"
            mime.contains("mp4") || mime.contains("aac") -> "m4a"
            mime.contains("wav") -> "wav"
            mime.contains("ogg") -> "ogg"
            mime.contains("3gpp") -> "3gp"
            else -> "m4a"
        }
    }

    private inline fun simpleSeek(crossinline onProgress: (SeekBar?, Int, Boolean) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onProgress(seekBar, progress, fromUser)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

    override fun onDestroy() {
        super.onDestroy()
        binding.waveform.release()
        if (isBound) unbindService(serviceConnection)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        super.onPause()
        binding.waveform.release()
    }

    override fun onResume() {
        super.onResume()
        attachVisualizer()
        updatePlayPauseIcon()
    }
}
