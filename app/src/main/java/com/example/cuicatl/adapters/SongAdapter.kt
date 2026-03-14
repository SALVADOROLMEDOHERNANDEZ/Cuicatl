package com.example.cuicatl.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cuicatl.databinding.ItemSongBinding
import com.example.cuicatl.models.Song as SongModel

class SongAdapter(
    private val songs: List<SongModel>,
    private val onClick: (SongModel) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    inner class SongViewHolder(val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.tvTitle.text = song.title
        holder.binding.tvArtist.text = song.artist
        holder.binding.root.setOnClickListener { onClick(song) }
    }

    override fun getItemCount(): Int = songs.size
}
