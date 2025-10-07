package com.moqayed.zam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.recyclerview.widget.RecyclerView
import com.moqayed.zam.MainActivity.Companion.darkenColor
import com.moqayed.zam.MainActivity.Companion.dpToPx
import com.moqayed.zam.MainActivity.Companion.getDominantColor
import com.moqayed.zam.MainActivity.Companion.getRGBValues

class SelectableTrackRowAdapter(
    private val items: MutableList<MediaItem>,
    private val onItemClick: (MediaItem) -> Unit,
    private var selectedItems: MutableList<MediaItem>
) : RecyclerView.Adapter<SelectableTrackRowAdapter.MyViewHolder>() {

    class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.trackRowTitle)
        val artistTextView: TextView = view.findViewById(R.id.trackRowArtist)
        val albumImageView: ImageView = view.findViewById(R.id.trackRowImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.selectabletrackrow, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        var context = holder.itemView.context
        val item = items[position]
        var image: Bitmap? = null
        if(item.mediaMetadata.artworkData != null) {
            image = BitmapFactory.decodeByteArray(item.mediaMetadata.artworkData, 0, item.mediaMetadata.artworkData!!.size)
        }
        val dominantColor = getDominantColor(context, image)
        val gradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 20).toFloat()
            colors = intArrayOf(dominantColor, darkenColor(dominantColor, 50))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            setSize(dpToPx(context, 360), dpToPx(context, 70))
        }

        holder.itemView.background = gradientDrawable
        holder.titleTextView.text = item.mediaMetadata.title
        val (red, green, blue) = getRGBValues(dominantColor)
        holder.titleTextView.setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
        holder.artistTextView.setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
        holder.artistTextView.text = item.mediaMetadata.artist
        holder.albumImageView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 15).toFloat() // 15dp rounded corners
        }
        if(image == null) {
            holder.albumImageView.setImageDrawable(context.getDrawable(R.drawable.default_music_icon))
        }
        else {
            holder.albumImageView.setImageBitmap(image)
        }
        if(selectedItems.contains(item)) {
            holder.itemView.foreground = ContextCompat.getDrawable(
                context,
                R.drawable.button_selected
            )
        } else {
            holder.itemView.foreground = ContextCompat.getDrawable(
                context,
                R.drawable.button_not_selected
            )
        }
        holder.itemView.setOnClickListener {
            if(!selectedItems.contains(item)) {
                holder.itemView.foreground = ContextCompat.getDrawable(
                    context,
                    R.drawable.button_selected
                )
                selectedItems.add(0, item)
            } else {
                holder.itemView.foreground = ContextCompat.getDrawable(
                    context,
                    R.drawable.button_not_selected
                )
                selectedItems.remove(item)
            }

            onItemClick(item)
        }
    }
    fun moveItem(from: Int, to: Int, onMoved: ((MediaItem) -> Unit)) {
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
        onMoved(item)
    }
    fun updateItems(updatedItems: MutableList<MediaItem>) {
        items.clear()
        items.addAll(updatedItems)
        notifyDataSetChanged()
    }
    override fun getItemCount(): Int = items.size
}
