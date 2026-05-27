package com.example.cuicatl.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cuicatl.R

class ImageOptionAdapter(
    private val images: List<String>,
    private val onImageSelected: (String) -> Unit
) : RecyclerView.Adapter<ImageOptionAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivOption: ImageView = view.findViewById(R.id.ivImageOption)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = images[position]
        Glide.with(holder.itemView.context)
            .load(url)
            .centerCrop()
            .into(holder.ivOption)
        
        holder.itemView.setOnClickListener { onImageSelected(url) }
    }

    override fun getItemCount(): Int = images.size
}
