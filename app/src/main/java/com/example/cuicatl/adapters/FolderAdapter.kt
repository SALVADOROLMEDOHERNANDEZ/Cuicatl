package com.example.cuicatl.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cuicatl.R
import java.io.File

class FolderAdapter(
    private val folders: List<String>,
    previouslySelected: Set<String>
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    private val selectedFolders = previouslySelected.toMutableSet()

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFolderName: TextView = view.findViewById(R.id.tvFolderName)
        val tvFolderPath: TextView = view.findViewById(R.id.tvFolderPath)
        val cbFolder: CheckBox = view.findViewById(R.id.cbFolder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val path = folders[position]
        val file = File(path)
        
        holder.tvFolderName.text = file.name
        holder.tvFolderPath.text = path
        
        // Remove listener temporarily to avoid trigger when setting state
        holder.cbFolder.setOnCheckedChangeListener(null)
        holder.cbFolder.isChecked = selectedFolders.contains(path)

        val toggleSelection = { isChecked: Boolean ->
            if (isChecked) {
                selectedFolders.add(path)
            } else {
                selectedFolders.remove(path)
            }
        }

        holder.cbFolder.setOnCheckedChangeListener { _, isChecked ->
            toggleSelection(isChecked)
        }

        holder.itemView.setOnClickListener {
            val newState = !selectedFolders.contains(path)
            toggleSelection(newState)
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = folders.size

    fun getSelectedFolders(): Set<String> = selectedFolders.toSet()
}
