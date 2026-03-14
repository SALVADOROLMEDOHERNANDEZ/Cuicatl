package com.example.cuicatl

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.cuicatl.databinding.ActivityEditorBinding
import com.example.cuicatl.utils.AudioUtils
import java.io.File
import java.io.FileOutputStream

class EditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private var currentAudioPath: String? = null
    private var secondAudioPath: String? = null // Para Mixing

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = copyFileToInternalStorage(uri, "input_audio.wav")
                currentAudioPath = path
                binding.tvLoadedFile.text = "ARCHIVO: ${File(path).name}"
                binding.tvLoadedFile.setTextColor(getColor(R.color.neonBlue))
            }
        }
    }

    private val secondFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = copyFileToInternalStorage(uri, "second_audio.wav")
                secondAudioPath = path
                performMixing()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent opcional desde el reproductor
        intent.getStringExtra("songPath")?.let {
            currentAudioPath = it
            binding.tvLoadedFile.text = "ARCHIVO: ${File(it).name}"
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnLoadFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "audio/*"
            }
            filePicker.launch(intent)
        }

        binding.btnTrim.setOnClickListener {
            val path = currentAudioPath ?: return@setOnClickListener toast("Carga un archivo primero")
            val startMs = binding.etStartTime.text.toString().toIntOrNull() ?: 0
            val endMs = binding.etEndTime.text.toString().toIntOrNull() ?: 10000
            
            val result = AudioUtils.trimAudio(path, startMs, endMs)
            handleResult(result, "Corte completado")
        }

        binding.btnAddEcho.setOnClickListener {
            val path = currentAudioPath ?: return@setOnClickListener toast("Carga un archivo primero")
            handleResult(AudioUtils.addEcho(path), "Eco aplicado")
        }

        binding.btnAddReverb.setOnClickListener {
            val path = currentAudioPath ?: return@setOnClickListener toast("Carga un archivo primero")
            handleResult(AudioUtils.addReverb(path), "Reverb aplicada")
        }

        binding.btnMix.setOnClickListener {
            if (currentAudioPath == null) return@setOnClickListener toast("Carga la pista principal")
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "audio/*" }
            secondFilePicker.launch(intent)
        }

        binding.btnSave.setOnClickListener {
            val path = currentAudioPath ?: return@setOnClickListener toast("Nada que exportar")
            val success = AudioUtils.saveAudio(path, "PROYECTO_MASTER_${System.currentTimeMillis()}")
            if (success) toast("EXPORTADO A /Music/CUICATL_PRO")
        }
    }

    private fun handleResult(newPath: String?, message: String) {
        if (newPath != null) {
            currentAudioPath = newPath
            binding.tvLoadedFile.text = "EDITADO: ${File(newPath).name}"
            toast(message)
        } else {
            toast("Error en el proceso")
        }
    }

    private fun performMixing() {
        val p1 = currentAudioPath ?: return
        val p2 = secondAudioPath ?: return
        val result = AudioUtils.mixAudio(p1, p2)
        handleResult(result, "Pistas fusionadas con éxito")
    }

    private fun copyFileToInternalStorage(uri: Uri, fileName: String): String {
        val inputStream = contentResolver.openInputStream(uri)
        val file = File(cacheDir, fileName)
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        return file.absolutePath
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
