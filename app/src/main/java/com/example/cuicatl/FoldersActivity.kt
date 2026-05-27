package com.example.cuicatl

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cuicatl.adapters.FolderAdapter
import com.example.cuicatl.databinding.ActivityFoldersBinding
import com.example.cuicatl.utils.FileUtils

class FoldersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFoldersBinding
    private lateinit var adapter: FolderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFoldersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnBackFolders.setOnClickListener { finish() }
        binding.btnSaveFoldersSelection.setOnClickListener {
            val selected = adapter.getSelectedFolders()
            saveSelectedFolders(selected)
            Toast.makeText(this, "Filtro aplicado correctamente", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupRecyclerView() {
        val allFolders = FileUtils.getMusicFolders(this)
        val previouslySelected = getSavedFolders()
        
        adapter = FolderAdapter(allFolders, previouslySelected)
        binding.rvFolders.layoutManager = LinearLayoutManager(this)
        binding.rvFolders.adapter = adapter
    }

    private fun saveSelectedFolders(folders: Set<String>) {
        val prefs = getSharedPreferences("CuicatlPrefs", MODE_PRIVATE)
        prefs.edit().putStringSet("selected_folders", folders).apply()
    }

    private fun getSavedFolders(): Set<String> {
        val prefs = getSharedPreferences("CuicatlPrefs", MODE_PRIVATE)
        return prefs.getStringSet("selected_folders", emptySet()) ?: emptySet()
    }
}
