package com.example.cuicatl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cuicatl.adapters.ImageOptionAdapter
import com.example.cuicatl.databinding.ActivityPlayerBinding
import com.example.cuicatl.utils.AIService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var musicService: MusicService? = null
    private var isBound = false
    private val handler = Handler(Looper.getMainLooper())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updateUI()
            startProgressUpdate()
            autoFetchLyrics()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnPlay.setOnClickListener {
            musicService?.resumeSong()
            updatePlayPauseButtons()
        }

        binding.btnPause.setOnClickListener {
            musicService?.pauseSong()
            updatePlayPauseButtons()
        }

        binding.btnNext.setOnClickListener {
            musicService?.playNext()
            updateUI()
            autoFetchLyrics()
        }

        binding.btnPrev.setOnClickListener {
            musicService?.playPrevious()
            updateUI()
            autoFetchLyrics()
        }

        binding.btnRepeat.setOnClickListener {
            musicService?.let {
                it.isRepeating = !it.isRepeating
                binding.btnRepeat.setColorFilter(if (it.isRepeating) getColor(R.color.neonBlue) else 0x88888888.toInt())
            }
        }

        binding.btnLyricsToggle.setOnClickListener {
            if (binding.clLyricsView.visibility == View.VISIBLE) {
                binding.clLyricsView.visibility = View.GONE
                binding.clDiscView.visibility = View.VISIBLE
            } else {
                binding.clLyricsView.visibility = View.VISIBLE
                binding.clDiscView.visibility = View.GONE
                updateLyricsUI()
            }
        }

        binding.btnAICover.setOnClickListener {
            if (AIService.isInternetAvailable(this)) {
                showImageSelectionDialog()
            } else {
                Toast.makeText(this, "Conéctate a internet para ver imágenes de IA", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSaveLyrics.setOnClickListener {
            val song = musicService?.getCurrentSong()
            song?.lyrics = binding.tvLyrics.text.toString()
            binding.llLyricsActions.visibility = View.GONE
            Toast.makeText(this, "Letra guardada en el dispositivo", Toast.LENGTH_SHORT).show()
        }

        binding.btnDiscardLyrics.setOnClickListener {
            binding.tvLyrics.text = "Letra descartada"
            binding.llLyricsActions.visibility = View.GONE
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress)
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateUI() {
        musicService?.getCurrentSong()?.let { song ->
            binding.tvSongName.text = song.title
            binding.tvArtistName.text = song.artist
            binding.seekBar.max = musicService?.getDuration() ?: 0
            binding.tvDuration.text = formatTime(musicService?.getDuration() ?: 0)
            
            // Cargar portada que ocupe todo el círculo
            if (song.coverUri != null) {
                Glide.with(this)
                    .load(song.coverUri)
                    .centerCrop()
                    .into(binding.ivSongCover)
            } else {
                binding.ivSongCover.setImageResource(R.drawable.disc_visual)
            }
            
            updateLyricsUI()
            updatePlayPauseButtons()
        }
    }

    private fun updateLyricsUI() {
        val song = musicService?.getCurrentSong() ?: return
        if (song.lyrics != null) {
            binding.tvLyrics.text = song.lyrics
            binding.llLyricsActions.visibility = View.GONE
        } else {
            binding.tvLyrics.text = "IA: Buscando letra correcta en la web..."
            binding.llLyricsActions.visibility = View.GONE
        }
    }

    private fun autoFetchLyrics() {
        val song = musicService?.getCurrentSong() ?: return
        if (song.lyrics == null && AIService.isInternetAvailable(this)) {
            lifecycleScope.launch {
                val fetched = AIService.fetchLyricsFromWeb(song.title, song.artist)
                if (fetched != null && musicService?.getCurrentSong()?.path == song.path) {
                    binding.tvLyrics.text = fetched
                    binding.llLyricsActions.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showImageSelectionDialog() {
        val song = musicService?.getCurrentSong() ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_selection, null)
        val rvImages = dialogView.findViewById<RecyclerView>(R.id.rvImageOptions)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelSelection)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        lifecycleScope.launch {
            val imageUrls = AIService.searchCoverImages(song.title, song.artist)
            rvImages.layoutManager = GridLayoutManager(this@PlayerActivity, 3)
            rvImages.adapter = ImageOptionAdapter(imageUrls) { selectedUrl ->
                song.coverUri = selectedUrl
                Glide.with(this@PlayerActivity).load(selectedUrl).centerCrop().into(binding.ivSongCover)
                dialog.dismiss()
                // Actualizar otras UI si es necesario
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun updatePlayPauseButtons() {
        val isPlaying = musicService?.isPlaying ?: false
        binding.btnPlay.visibility = if (isPlaying) View.GONE else View.VISIBLE
        binding.btnPause.visibility = if (isPlaying) View.VISIBLE else View.GONE
    }

    private fun startProgressUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                musicService?.let {
                    if (it.isPlaying) {
                        binding.seekBar.progress = it.getCurrentPosition()
                        binding.tvCurrentTime.text = formatTime(it.getCurrentPosition())
                    }
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(serviceConnection)
        handler.removeCallbacksAndMessages(null)
    }
}
