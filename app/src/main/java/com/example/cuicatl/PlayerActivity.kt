package com.example.cuicatl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.cuicatl.databinding.ActivityPlayerBinding

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
        }

        binding.btnPrev.setOnClickListener {
            musicService?.playPrevious()
            updateUI()
        }

        binding.btnRepeat.setOnClickListener {
            musicService?.let {
                it.isRepeating = !it.isRepeating
                binding.btnRepeat.setColorFilter(if (it.isRepeating) getColor(R.color.neonBlue) else 0x88888888.toInt())
            }
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
            updatePlayPauseButtons()
        }
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
                    updatePlayPauseButtons()
                    
                    // Check if song changed (e.g. naturally finished)
                    val currentSong = it.getCurrentSong()
                    if (currentSong != null && binding.tvSongName.text != currentSong.title) {
                        updateUI()
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
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        handler.removeCallbacksAndMessages(null)
    }
}
