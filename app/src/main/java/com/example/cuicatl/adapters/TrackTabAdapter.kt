package com.example.cuicatl.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cuicatl.R
import com.example.cuicatl.models.Song

class TrackTabAdapter(
    private val tracks: List<Song>,
    private var selectedIndex: Int,
    private val onTrackSelected: (Int) -> Unit
) : RecyclerView.Adapter<TrackTabAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvTrackName)
        val container: View = view.findViewById(R.id.llTrackTab)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track_tab, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = tracks[position]
        holder.tvName.text = "Pista ${position + 1}: ${song.title}"
        val isSelected = position == selectedIndex
        holder.container.isActivated = isSelected
        holder.container.alpha = if (isSelected) 1.0f else 0.7f

        holder.itemView.setOnClickListener {
            selectedIndex = position
            onTrackSelected(position)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = tracks.size

    fun updateSelection(index: Int) {
        selectedIndex = index
        notifyDataSetChanged()
    }
}
