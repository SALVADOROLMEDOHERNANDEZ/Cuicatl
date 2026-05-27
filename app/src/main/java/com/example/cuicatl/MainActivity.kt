package com.example.cuicatl

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.cuicatl.adapters.SongAdapter
import com.example.cuicatl.databinding.ActivityMainBinding
import com.example.cuicatl.models.Song
import com.example.cuicatl.utils.FileUtils
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var musicService: MusicService? = null
    private var isBound = false
    private val PERMISSION_CODE = 101
    private var displayedSongs: List<Song> = listOf()
    private val handler = Handler(Looper.getMainLooper())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            startUpdatingUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = Intent(this, MusicService::class.java)
        try {
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setupPermissions()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        if (checkStoragePermission()) {
            cargarBiblioteca()
        }
    }

    private fun checkStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val toRequest = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), PERMISSION_CODE)
        } else {
            cargarBiblioteca()
        }
    }

    private fun setupListeners() {
        binding.tabFolders.setOnClickListener {
            try {
                val intent = Intent(this, FoldersActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Error al abrir carpetas: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        binding.tabSongs.setOnClickListener {
            cargarBiblioteca()
        }

        binding.btnMiniPlayPause.setOnClickListener {
            musicService?.let {
                if (it.isPlaying) it.pauseSong() else it.resumeSong()
                updateMiniPlayerUI()
            }
        }
        binding.btnMiniNext.setOnClickListener {
            musicService?.playNext()
            updateMiniPlayerUI()
        }
        binding.btnMiniPrev.setOnClickListener {
            musicService?.playPrevious()
            updateMiniPlayerUI()
        }
        binding.cvMiniPlayer.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }
        
        binding.btnMusic.setOnClickListener {
            if (musicService?.getCurrentSong() != null) {
                startActivity(Intent(this, PlayerActivity::class.java))
            } else {
                Toast.makeText(this, "Selecciona una canción de la lista", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.btnEditAudio.setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
    }

    private fun startUpdatingUI() {
        handler.post(object : Runnable {
            override fun run() {
                updateMiniPlayerUI()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun updateMiniPlayerUI() {
        musicService?.let { service ->
            val currentSong = service.getCurrentSong()
            if (currentSong != null) {
                binding.cvMiniPlayer.visibility = View.VISIBLE
                binding.tvMiniTitle.text = currentSong.title
                binding.tvMiniArtist.text = currentSong.artist
                binding.btnMiniPlayPause.setImageResource(
                    if (service.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
                
                // Cargar imagen de fondo en el mini reproductor
                if (currentSong.coverUri != null) {
                    Glide.with(this)
                        .load(currentSong.coverUri)
                        .centerCrop()
                        .into(binding.ivMiniBackground)
                    
                    Glide.with(this)
                        .load(currentSong.coverUri)
                        .centerCrop()
                        .into(binding.ivMiniDisc)
                } else {
                    binding.ivMiniBackground.setImageDrawable(null)
                    binding.ivMiniDisc.setImageResource(R.drawable.disc_visual)
                }
            } else {
                binding.cvMiniPlayer.visibility = View.GONE
            }
        }
    }

    private fun cargarBiblioteca() {
        CoroutineScope(Dispatchers.Main).launch {
            val allSongs = withContext(Dispatchers.IO) {
                FileUtils.getAllSongs(this@MainActivity)
            }

            val selectedFolders = getSharedPreferences("CuicatlPrefs", MODE_PRIVATE)
                .getStringSet("selected_folders", emptySet()) ?: emptySet()

            displayedSongs = if (selectedFolders.isEmpty()) {
                allSongs
            } else {
                allSongs.filter { song ->
                    selectedFolders.any { folder -> song.path.startsWith(folder) }
                }
            }
            
            val adapter = SongAdapter(displayedSongs) { song ->
                musicService?.setPlaylist(displayedSongs, displayedSongs.indexOf(song))
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
            }
            
            binding.rvLibrary.layoutManager = LinearLayoutManager(this@MainActivity)
            binding.rvLibrary.adapter = adapter
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (checkStoragePermission()) {
                cargarBiblioteca()
            } else {
                Toast.makeText(this, "Se requiere permiso de acceso a archivos para mostrar tu música", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(serviceConnection)
        handler.removeCallbacksAndMessages(null)
    }
}
