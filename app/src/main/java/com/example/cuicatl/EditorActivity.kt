package com.example.cuicatl

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
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
import com.google.android.material.slider.Slider
import java.io.File
import java.io.FileOutputStream

class EditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private var musicService: MusicService? = null
    private var isBound = false
    private lateinit var trackAdapter: TrackTabAdapter

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = copyFileToInternalStorage(uri)
                val newSong = Song(File(path).name, "Importado", path)
                musicService?.addTrack(newSong)
                updateTrackListUI()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            setupTrackList()
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
        setupEqualizerBands()
    }

    private fun setupTrackList() {
        val tracks = musicService?.getEditorTracks() ?: listOf()
        trackAdapter = TrackTabAdapter(tracks, 0) { index ->
            musicService?.selectTrack(index)
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
            updateEditorUI()
        }
        binding.rvTrackSelector.adapter = trackAdapter
        musicService?.selectTrack(lastIndex)
        updateEditorUI()
    }

    private fun setupListeners() {
        binding.tabEqualizer.setOnClickListener { showEqualizer(true) }
        binding.tabEffects.setOnClickListener { showEqualizer(false) }

        binding.btnAddTrack.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "audio/*" }
            filePicker.launch(intent)
        }

        binding.btnReset.setOnClickListener {
            Toast.makeText(this, "Ajustes de pista reiniciados", Toast.LENGTH_SHORT).show()
        }

        binding.btnEditorPlay.setOnClickListener {
            musicService?.let {
                if (it.isPlaying) it.pauseSong() else it.resumeSong()
                binding.btnEditorPlay.setImageResource(
                    if (it.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            }
        }

        binding.effectsLayout.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.applyVolume(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.effectsLayout.sbPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val pitch = 0.5f + (progress / 50f)
                    val tempo = 0.5f + (binding.effectsLayout.sbTempo.progress / 50f)
                    musicService?.applyPlaybackParams(pitch, tempo)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.effectsLayout.sbTempo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val tempo = 0.5f + (progress / 50f)
                    val pitch = 0.5f + (binding.effectsLayout.sbPitch.progress / 50f)
                    musicService?.applyPlaybackParams(pitch, tempo)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        binding.effectsLayout.effectReverb.swEffect.setOnCheckedChangeListener { _, isChecked ->
            musicService?.toggleReverb(isChecked)
        }
    }

    private fun showEqualizer(show: Boolean) {
        binding.equalizerLayout.root.visibility = if (show) View.VISIBLE else View.GONE
        binding.effectsLayout.root.visibility = if (show) View.GONE else View.VISIBLE
        binding.tabEqualizer.setBackgroundResource(if (show) R.drawable.pro_badge_bg else 0)
        binding.tabEffects.setBackgroundResource(if (!show) R.drawable.pro_badge_bg else 0)
    }

    private fun setupEqualizerBands() {
        val frequencies = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
        val container = binding.equalizerLayout.llEqualizerBands
        container.removeAllViews()
        for (i in frequencies.indices) {
            val bandView = LayoutInflater.from(this).inflate(R.layout.item_equalizer_band, container, false)
            val tvValue = bandView.findViewById<TextView>(R.id.tvBandValue)
            bandView.findViewById<TextView>(R.id.tvBandFreq).text = frequencies[i]
            bandView.findViewById<Slider>(R.id.sliderBand).addOnChangeListener { _, value, _ ->
                tvValue.text = "${value.toInt()} dB"
                musicService?.setEqualizerBand(i, (value * 100).toInt().toShort())
            }
            container.addView(bandView)
        }
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
            }
        }
    }

    private fun copyFileToInternalStorage(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val file = File(cacheDir, "track_${System.currentTimeMillis()}.wav")
        FileOutputStream(file).use { inputStream?.copyTo(it) }
        return file.absolutePath
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(serviceConnection)
    }
}
