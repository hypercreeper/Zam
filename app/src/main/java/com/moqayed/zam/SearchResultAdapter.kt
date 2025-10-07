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

class SearchResultAdapter(
    private val items: MutableList<SearchSongOnlineActivity.Companion.Result>,
    private val onItemClick: (SearchSongOnlineActivity.Companion.Result) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.MyViewHolder>() {

    class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.trackRowTitle)
        val artistTextView: TextView = view.findViewById(R.id.trackRowArtist)
        val albumImageView: ImageView = view.findViewById(R.id.trackRowImage)
    }
    public var selectable: Boolean = false
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.searchobject, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        var context = holder.itemView.context
        val item = items[position]

        val dominantColor = getDominantColor(context, item.Image)
        val gradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 20).toFloat()
            colors = intArrayOf(dominantColor, darkenColor(dominantColor, 50))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            setSize(dpToPx(context, 360), dpToPx(context, 70))
        }

        holder.itemView.background = gradientDrawable
        holder.titleTextView.text = item.title
        val (red, green, blue) = getRGBValues(dominantColor)
        holder.titleTextView.setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
        holder.artistTextView.setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
        holder.artistTextView.text = item.author
        holder.albumImageView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 15).toFloat() // 15dp rounded corners
        }
        if(item.Image == null) {
            holder.albumImageView.setImageDrawable(context.getDrawable(R.drawable.default_music_icon))
        }
        else {
            holder.albumImageView.setImageBitmap(item.Image)
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }
    fun moveItem(from: Int, to: Int, onMoved: ((SearchSongOnlineActivity.Companion.Result) -> Unit)) {
        val item = items.removeAt(from)
        items.add(to, item)
        onMoved(item)
        notifyItemMoved(from, to)
    }
    fun disableSelection() {
        selectable = false
        notifyDataSetChanged()
    }
    fun enableSelection() {
        selectable = true
        notifyDataSetChanged()
    }
    fun updateItems(updatedItems: MutableList<SearchSongOnlineActivity.Companion.Result>) {
        items.clear()
        items.addAll(updatedItems)
        notifyDataSetChanged()
    }
    override fun getItemCount(): Int = items.size
}
