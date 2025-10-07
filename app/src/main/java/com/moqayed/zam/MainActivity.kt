package com.moqayed.zam

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation
import android.graphics.drawable.LayerDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.palette.graphics.Palette
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.moqayed.zam.MusicService.Companion.targetMediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity() {

    companion object {
//        var Queue: Playlist = Playlist()

        @Entity(tableName = "lastPlayedPlaylistTracks")
        data class TrackEntity(
            @PrimaryKey val uri: String, // Unique identifier
            val title: String,
            val artist: String,
            val artwork: ByteArray? // Store the artwork as ByteArray
        )

        @Dao
        interface TrackDao {
            @Query("SELECT * FROM lastPlayedPlaylistTracks")
            suspend fun getAllTracks(): List<TrackEntity>

            @Insert(onConflict = OnConflictStrategy.REPLACE)
            suspend fun insertTracks(tracks: List<TrackEntity>)

            @Query("DELETE FROM lastPlayedPlaylistTracks")
            suspend fun clearTracks()
        }

        @Database(entities = [TrackEntity::class], version = 1, exportSchema = false)
        abstract class AppDatabase : RoomDatabase() {
            abstract fun trackDao(): TrackDao

            companion object {
                @Volatile private var INSTANCE: AppDatabase? = null

                fun getDatabase(context: Context): AppDatabase {
                    return INSTANCE ?: synchronized(this) {
                        val instance = Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "last_played_database"
                        ).build()
                        INSTANCE = instance
                        instance
                    }
                }
            }
        }
        lateinit var sharedPreferences: SharedPreferences
        var overrideBackButton: Boolean = false
        var overrideBackButtonAction = {}
        var playbackListener: Player.Listener = object : Player.Listener {}

        fun dpToPx(context: Context, dp: Int): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }
        data class AudioMetadata(
            var title: String?,
            var artist: String?,
            var coverUri: Uri?,
            var cover: Bitmap?,
            var coverBytes: ByteArray?,
            var uri: Uri
        )

        @OptIn(UnstableApi::class)
        fun getAudioMetadataFFmpeg(uri: Uri): MediaItem {
            val retriever = MediaMetadataRetriever()
            val filePath = uri.toString()
            try {
                retriever.setDataSource(filePath)
            }
            catch (e: Exception) {
                return MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(File(filePath).name)
                            .setArtist("Unknown Artist")
                            .setArtworkData(null)
                            .build()
                    )
                    .build()
            }
                var mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?:File(filePath).name)
                            .setArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?:"Unknown Artist")
                            .setArtworkData(retriever.embeddedPicture)
                            .build()
                    )
                    .build()
                //Log.d("DEBUG", "Title: $title, Artist: $artist, Cover Art: " + cover)
                return mediaItem
//            val filePath = uri.toString()
//                var file: AudioFile? = null
//
//                try {
//                    file = AudioFileIO.read(File(filePath))
//                }
//                catch (e: Exception) {
//                    return AudioMetadata(null, null, null, null, null, uri)
//                }
//                val tag = file.tag
//                val title = try {
//                    tag.getFirst(FieldKey.TITLE)
//                }
//                catch (e: Exception) {
//                    file.file.name
//                }
//                val artist = try {tag.getFirst(FieldKey.ARTIST)}catch (e: Exception) {"Unknown Artist"}
//                var cover: ByteArray? = null
//                var coverUri: Uri? = null
//                try {
//                    cover = tag.firstArtwork.binaryData
//                    coverUri = Uri.parse(tag.firstArtwork.imageUrl)
//                }
//                catch (e: Exception) {
////                    e.printStackTrace()
//                }
//                //Log.d("DEBUG", "Title: $title, Artist: $artist, Cover Art: " + cover)
//                return AudioMetadata(title, artist, coverUri, try {BitmapFactory.decodeByteArray(cover, 0, cover?.size?:0)} catch (e: Exception) {null}, cover, uri)
//            } catch (e: Exception) {
//                //Log.e("getAudioMetdata", e.message?:"")
//                //e.printStackTrace()
//                AudioMetadata(null, null, null, null, null, uri)
//            }

//            return try {
//                retriever.setDataSource(filePath)
//
//                val title =
//                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
//                val artist =
//                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
//
//                // Extract album art (cover)
//                val coverBytes = retriever.embeddedPicture
//                val coverBitmap = coverBytes?.let {
//                    android.graphics.BitmapFactory.decodeByteArray(
//                        it,
//                        0,
//                        it.size
//                    )
//                }
//
//                AudioMetadata(title, artist, coverBitmap, coverBytes, uri)
//            } catch (e: Exception) {
//                e.printStackTrace()
//                AudioMetadata(null, null, null, null, uri)
//            } finally {
//                retriever.release()
//            }
        }

        fun getRGBValues(packedInt: Int): Triple<Int, Int, Int> {
            val red = (packedInt shr 16) and 0xFF
            val green = (packedInt shr 8) and 0xFF
            val blue = packedInt and 0xFF
            //Log.i("getRGBValues", "Red: " + red + ", Green: " + green + ", Blue: " + blue)
            return Triple(red, green, blue)
        }
        fun darkenColor(color: Int, amount: Int): Int {
            val a = (color shr 24) and 0xFF // Extract alpha
            val r = ((color shr 16) and 0xFF) - amount
            val g = ((color shr 8) and 0xFF) - amount
            val b = (color and 0xFF) - amount

            return (a shl 24) or
                    (r.coerceIn(0, 255) shl 16) or
                    (g.coerceIn(0, 255) shl 8) or
                    b.coerceIn(0, 255)
        }


        fun getDominantColor(context: Context, bitmap: Bitmap?): Int {
            if (bitmap == null) return ContextCompat.getColor(context, R.color.primary) // Default fallback color
            return Palette.from(bitmap).generate().getDominantColor(ContextCompat.getColor(context, R.color.primary))
        }
        var currentTracksPlaylist: Playlist = Playlist()
        var musicService: MusicService? = null

        fun createSelectableTrack(context: Context, rootLayout: LinearLayout, songName: String, songArtist: String, mediaItem: MediaItem, image: Bitmap?, selectedItems: MutableList<MediaItem>, atIndex: Int = -1, touchMove: ((v: View?, event: MotionEvent?) -> Unit)? = null, touchDown: ((v: View?, event: MotionEvent?) -> Unit)? = null, touchUp: ((v: View?, event: MotionEvent?) -> Unit)? = null, touchCancel: ((v: View?, event: MotionEvent?) -> Unit)? = null, overrideTouchEvents: Boolean = false): LinearLayout {
            val dominantColor = getDominantColor(context, image)
            val parentLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(40, dpToPx(context, 14), 40, 0)
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
//            background = ContextCompat.getDrawable(context, R.drawable.song_card_bg)
                val gradientDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 20).toFloat()
                    colors = intArrayOf(dominantColor, darkenColor(dominantColor, 50))
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                    setSize(dpToPx(context, 360), dpToPx(context, 70))
                }

                background = gradientDrawable
                foreground = ContextCompat.getDrawable(context, R.drawable.button_not_selected)
            }
            parentLayout.setTag(mediaItem)

            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 50), dpToPx(context, 50)).apply {
                    setMargins(dpToPx(context,12), 0, dpToPx(context,10),0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 15).toFloat() // 15dp rounded corners
                }

                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
            }
            if(image == null) {
                imageView.setImageDrawable(ContextCompat.getDrawable(context,R.drawable.default_music_icon))
            }
            else {
                imageView.setImageBitmap(image)
            }

            val textContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }


            val songNameTextView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(context, 26)
                ).apply {
                    setMargins(0,0, dpToPx(context, 10), 0)
                }
                typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
                text = songName
                textSize = 17f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                val (red, green, blue) = getRGBValues(dominantColor)
                setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
            }

            val artistTextView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
                text = songArtist
                textSize = 14f
                val (red, green, blue) = getRGBValues(dominantColor)
                setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
            }

            textContainer.addView(songNameTextView)
            textContainer.addView(artistTextView)

            parentLayout.addView(imageView)
            parentLayout.addView(textContainer)

            parentLayout.setOnTouchListener (object: View.OnTouchListener {
                var isLongPress = false
                val handler = Handler(Looper.getMainLooper())
                val longPressRunnable = Runnable {
                    isLongPress = true
                    parentLayout.performLongClick()
                    println("Long Click Detected!")
                    val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                    parentLayout.startAnimation(anim)
                }
                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    if (event?.action == MotionEvent.ACTION_UP) {
                        if(touchUp != null)
                            touchUp(v,event)
                        // Call your function here when touch is released
                        if(!overrideTouchEvents) {
                            handler.removeCallbacks(longPressRunnable)
                            if (!isLongPress) {
                                // Handle normal click here
                                println("Short Click Detected!")
                                val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                                parentLayout.startAnimation(anim)
                                Log.d("Track Card", "Music Clicked")
                                parentLayout.isSelected = !parentLayout.isSelected
                                if (parentLayout.isSelected) {
                                    parentLayout.foreground = ContextCompat.getDrawable(
                                        context,
                                        R.drawable.button_selected
                                    )
                                    selectedItems.add(0, mediaItem)
                                } else {
                                    parentLayout.foreground = ContextCompat.getDrawable(
                                        context,
                                        R.drawable.button_not_selected
                                    )
                                    selectedItems.remove(mediaItem)
                                }
                            }

                        }
                        return true // Return true to indicate the event was handled
                    } else if (event?.action == MotionEvent.ACTION_MOVE) {
                        if(touchMove != null)
                            touchMove(v, event)
                        return true
                    } else if (event?.action == MotionEvent.ACTION_DOWN) {
                        if(touchDown != null)
                            touchDown(v,event)
                        if(!overrideTouchEvents) {
                            isLongPress = false
                            handler.postDelayed(longPressRunnable, 500) // 500ms for long press
                            val anim = AnimationUtils.loadAnimation(context, R.anim.press)
                            parentLayout.startAnimation(anim)
                        }
                        return true
                    } else if (event?.action == MotionEvent.ACTION_CANCEL) {
                        if(touchCancel != null)
                            touchCancel(v,event)
                        if(!overrideTouchEvents) {
                            handler.removeCallbacks(longPressRunnable)
                            if (!isLongPress) {
                                val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                                parentLayout.startAnimation(anim)
                            }
                        }
                        return false
                    } else {
                        return false // Return false to allow other touch events to be processed
                    }
                }
            })

            if(atIndex != -1) {
                if(rootLayout.getChildAt(atIndex) is View)
                    rootLayout.removeViewAt(atIndex)
                rootLayout.addView(parentLayout, atIndex) // Replace rootLayout with your actual root view
            }
            else {
                rootLayout.addView(parentLayout)
            }
            return parentLayout
        }
        fun createViewTrack(context: Context, rootLayout: LinearLayout, songName: String, songArtist: String, mediaItem: MediaItem, image: Bitmap?, atIndex: Int = -1): LinearLayout {
            val dominantColor = getDominantColor(context, image)
            val parentLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(40, dpToPx(context, 14), 40, 0)
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
//            background = ContextCompat.getDrawable(context, R.drawable.song_card_bg)
                val gradientDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 20).toFloat()
                    colors = intArrayOf(dominantColor, darkenColor(dominantColor, 50))
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                    setSize(dpToPx(context, 360), dpToPx(context, 70))
                }

                background = gradientDrawable
            }
            parentLayout.setTag(mediaItem)

            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 50), dpToPx(context, 50)).apply {
                    setMargins(dpToPx(context,12), 0, dpToPx(context,10),0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 15).toFloat() // 15dp rounded corners
                }

                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
            }
            if(image == null) {
                imageView.setImageDrawable(ContextCompat.getDrawable(context,R.drawable.default_music_icon))
            }
            else {
                imageView.setImageBitmap(image)
            }

            val textContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }


            val songNameTextView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(context, 26)
                ).apply {
                    setMargins(0,0, dpToPx(context, 10), 0)
                }
                typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
                text = songName
                textSize = 17f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                val (red, green, blue) = getRGBValues(dominantColor)
                setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
            }

            val artistTextView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
                text = songArtist
                textSize = 14f
                val (red, green, blue) = getRGBValues(dominantColor)
                setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
            }

            textContainer.addView(songNameTextView)
            textContainer.addView(artistTextView)

            parentLayout.addView(imageView)
            parentLayout.addView(textContainer)

            if(atIndex != -1) {
                if(rootLayout.getChildAt(atIndex) is View)
                    rootLayout.removeViewAt(atIndex)
                rootLayout.addView(parentLayout, atIndex) // Replace rootLayout with your actual root view
            }
            else {
                rootLayout.addView(parentLayout)
            }
            return parentLayout
        }
        fun storeLastPlayed(context: Context, ofPlaylist: Playlist, currentPlaying: MediaItem) {
            var database = AppDatabase.getDatabase(context)
            var dao = database.trackDao()
            CoroutineScope(Dispatchers.IO).launch {
                var mediaItems = ofPlaylist.mediaItems
                var temp: List<TrackEntity> = listOf()
                if(mediaItems != null) {
                    var i = 0
                    var set = false
                    for (item in mediaItems) {
                        temp += TrackEntity(item.localConfiguration?.uri.toString(), (item.mediaMetadata.title?:"Unknown Title").toString(), (item.mediaMetadata.artist?:"Unknown Artist").toString(), item.mediaMetadata.artworkData)
                        if(item.localConfiguration?.uri == currentPlaying.localConfiguration?.uri && i != mediaItems.size) {
                            set = true
                            CoroutineScope(Dispatchers.Main).launch {
                                sharedPreferences.edit()
                                    .putInt("lastPlayedIndex", i)
                                    .putLong("lastPlayedDuration", musicService?.player?.duration!!)
                                    .commit()
                            }
                        }
                        i++
                    }
                    if(set == false) {
                        CoroutineScope(Dispatchers.Main).launch {
                            sharedPreferences.edit()
                                .putInt("lastPlayedIndex", 0)
                                .putLong("lastPlayedDuration", 0)
                                .commit()
                        }
                    }
                    dao.clearTracks()
                    dao.insertTracks(temp)
                }
            }
        }
        fun createTrack(context: Context, rootLayout: LinearLayout, songName: String, songArtist: String, mediaItem: MediaItem, image: Bitmap?, ofPlaylist: Playlist, atIndex: Int = -1, touchMove: ((v: View?, event: MotionEvent?) -> Unit)? = null, touchDown: ((v: View?, event: MotionEvent?) -> Unit)? = null, touchUp: ((v: View?, event: MotionEvent?) -> Unit)? = null, touchCancel: ((v: View?, event: MotionEvent?) -> Unit)? = null): LinearLayout {
            val dominantColor = getDominantColor(context, image)
            val parentLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(40, dpToPx(context, 14), 40, 0)
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
//            background = ContextCompat.getDrawable(context, R.drawable.song_card_bg)
                val gradientDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 20).toFloat()
                    colors = intArrayOf(dominantColor, darkenColor(dominantColor, 50))
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                    setSize(dpToPx(context, 360), dpToPx(context, 70))
                }

                background = gradientDrawable
            }
            parentLayout.setTag(arrayOf(mediaItem, ofPlaylist))

            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 50), dpToPx(context, 50)).apply {
                    setMargins(dpToPx(context,12), 0, dpToPx(context,10),0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 15).toFloat() // 15dp rounded corners
                }

                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
            }
            if(image == null) {
                imageView.setImageDrawable(ContextCompat.getDrawable(context,R.drawable.default_music_icon))
            }
            else {
                imageView.setImageBitmap(image)
            }

            val textContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }


            val songNameTextView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(context, 26)
                ).apply {
                    setMargins(0,0, dpToPx(context, 10), 0)
                }
                typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
                text = songName
                textSize = 17f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                val (red, green, blue) = getRGBValues(dominantColor)
                setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
            }

            val artistTextView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
                text = songArtist
                textSize = 14f
                val (red, green, blue) = getRGBValues(dominantColor)
                setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
            }

            textContainer.addView(songNameTextView)
            textContainer.addView(artistTextView)

            parentLayout.addView(imageView)
            parentLayout.addView(textContainer)

            parentLayout.setOnTouchListener (object: View.OnTouchListener {
                var isLongPress = false
                val handler = Handler(Looper.getMainLooper())
                val longPressRunnable = Runnable {
                    isLongPress = true
                    parentLayout.performLongClick()
                    println("Long Click Detected!")
                    val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                    parentLayout.startAnimation(anim)
                }
                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    if (event?.action == MotionEvent.ACTION_UP) {
                        if(touchUp != null) {
                            touchUp(v, event)
                        }
                        // Call your function here when touch is released
                        handler.removeCallbacks(longPressRunnable)
                        if (!isLongPress) {
                            // Handle normal click here
                            println("Short Click Detected!")
                            val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                            parentLayout.startAnimation(anim)
                            val name = songName
                            val artist = songArtist
                            val mi = mediaItem
                            val cover = image
                            Log.d("Track Card", "Music Clicked")

                            setPlayerLoadingUI()
                            CoroutineScope(Dispatchers.IO).launch {
                                playTrack(context, mediaItem, ofPlaylist)
//                                storeLastPlayed(context, ofPlaylist, mediaItem)
                            }
                        }


                        return true // Return true to indicate the event was handled
                    } else if (event?.action == MotionEvent.ACTION_MOVE) {
                        if(touchMove != null) {
                            touchMove(v, event)
                        }
                        return true
                    } else if (event?.action == MotionEvent.ACTION_DOWN) {
                        if(touchDown != null) {
                            touchDown(v, event)
                        }
                        isLongPress = false
                        handler.postDelayed(longPressRunnable, 500) // 500ms for long press
                        val anim = AnimationUtils.loadAnimation(context, R.anim.press)
                        parentLayout.startAnimation(anim)
                        return true
                    } else if (event?.action == MotionEvent.ACTION_CANCEL) {
                        if(touchCancel != null) {
                            touchCancel(v, event)
                        }
                        handler.removeCallbacks(longPressRunnable)
                        if (!isLongPress) {
                            val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                            parentLayout.startAnimation(anim)
                        }
                        return false
                    } else {
                        return false // Return false to allow other touch events to be processed
                    }
                }
            })

            if(atIndex != -1) {
                if(rootLayout.getChildAt(atIndex) is View)
                    rootLayout.removeViewAt(atIndex)
                rootLayout.addView(parentLayout, atIndex) // Replace rootLayout with your actual root view
            }
            else {
                rootLayout.addView(parentLayout)
            }
            return parentLayout
        }
        fun createPlaylistView(context: Context, rootLayout: GridLayout, playlistName: String, playlistPath: String, cover: Bitmap?): LinearLayout {
            val dominantColor = getDominantColor(context, cover)
            val parentLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(40, dpToPx(context, 14), 40, 0)
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
//            background = ContextCompat.getDrawable(context, R.drawable.song_card_bg)
                val gradientDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 20).toFloat()
                    colors = intArrayOf(dominantColor, darkenColor(dominantColor, 50))
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                    setSize(dpToPx(context, 360), dpToPx(context, 70))
                }

                background = gradientDrawable
            }

            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 50), dpToPx(context, 50)).apply {
                    setMargins(dpToPx(context,12), 0, dpToPx(context,10),0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 15).toFloat() // 15dp rounded corners
                }

                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
            }
            if(cover == null) {
                imageView.setImageDrawable(ContextCompat.getDrawable(context,R.drawable.default_music_icon))
            }
            else {
                imageView.setImageBitmap(cover)
            }

            val textContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }


            val songNameTextView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(context, 26)
                ).apply {
                    setMargins(0,0, dpToPx(context, 10), 0)
                }
                typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
                text = playlistName
                textSize = 17f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                val (red, green, blue) = getRGBValues(dominantColor)
                setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
            }

            val artistTextView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
                var count =  File(playlistPath).readLines().size-1
                text = (count.toString() + " Song" + if(count != 1) {"s"} else {""})
                textSize = 14f
                val (red, green, blue) = getRGBValues(dominantColor)
                setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
            }

            textContainer.addView(songNameTextView)
            textContainer.addView(artistTextView)

            parentLayout.addView(imageView)
            parentLayout.addView(textContainer)

            parentLayout.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Call your function here when touch is released

                    val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                    parentLayout.startAnimation(anim)
                    Log.d("Track Card", "Playlist Clicked")

                    var viewPlaylistFragment = ViewPlaylistFragment.newInstance(playlistName, playlistPath)
                    val transaction = (context as MainActivity).supportFragmentManager.beginTransaction()
                    transaction.setCustomAnimations(
                        R.anim.slide_in_right, // Enter animation
                        com.google.android.material.R.anim.abc_fade_out, // Exit animation
                        com.google.android.material.R.anim.abc_fade_in, // Pop enter animation
                        R.anim.slide_out_right // Pop exit animation
                    )
                    transaction.replace(R.id.MainView, viewPlaylistFragment)
                    transaction.addToBackStack(null) // Allows back navigation
                    transaction.commit()

                    true // Return true to indicate the event was handled
                }
                else if(event.action == MotionEvent.ACTION_DOWN) {
                    val anim = AnimationUtils.loadAnimation(context, R.anim.press)
                    parentLayout.startAnimation(anim)
                    true
                }
                else if(event.action == MotionEvent.ACTION_CANCEL) {
                    val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                    parentLayout.startAnimation(anim)
                    false
                }
                else {
                    false // Return false to allow other touch events to be processed
                }
            }
            rootLayout.addView(parentLayout)
            return parentLayout
        }
//        fun createPlaylistView(context: Context, rootLayout: GridLayout, playlistName: String, playlistPath: String, cover: Bitmap?) {
//            val linearLayout = LinearLayout(context).apply {
//                layoutParams = LinearLayout.LayoutParams(
//                    dpToPx(context, 170),
//                    LinearLayout.LayoutParams.WRAP_CONTENT
//                ).apply {
//                    setPadding(dpToPx(context, 10))
//                }
//                gravity = Gravity.CENTER_HORIZONTAL
//                orientation = LinearLayout.VERTICAL
//                val gradientDrawable = GradientDrawable().apply {
//                    shape = GradientDrawable.RECTANGLE
//                    cornerRadius = dpToPx(context, 20).toFloat()
//                    colors = intArrayOf(getDominantColor(context, cover), getDominantColor(context, cover))
//                    gradientType = GradientDrawable.LINEAR_GRADIENT
//                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
//                    setSize(dpToPx(context, 170), dpToPx(context, 170))
//                }
//
//                background = gradientDrawable
//            }
//
//            val imageView = ImageView(context).apply {
//                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 135), dpToPx(context, 135)).apply {
//                    gravity = Gravity.CENTER_HORIZONTAL
//                }
//                background = GradientDrawable().apply {
//                    shape = GradientDrawable.RECTANGLE
//                    cornerRadius = dpToPx(context, 15).toFloat() // 15dp rounded corners
//                }
//
//                scaleType = ImageView.ScaleType.CENTER_CROP
//                clipToOutline = true
//            }
//            if(cover == null) {
//                imageView.setImageBitmap(
//                    layerDrawableToBitmap(
//                        ContextCompat.getDrawable(
//                            context,
//                            R.drawable.default_music_icon
//                        ) as LayerDrawable
//                    )
//                )
//            }
//            else {
//                imageView.setImageBitmap(cover)
//            }
//
//            val textView = TextView(context).apply {
//                layoutParams = LinearLayout.LayoutParams(
//                    LinearLayout.LayoutParams.WRAP_CONTENT,
//                    LinearLayout.LayoutParams.WRAP_CONTENT
//                ).apply {
//                    gravity = Gravity.CENTER_HORIZONTAL
//                }
//                maxLines = 1
//                gravity = Gravity.CENTER
//                ellipsize = TextUtils.TruncateAt.END
//                text = playlistName
//                textSize = 18f
//                typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
//                setTextColor(ContextCompat.getColor(context, R.color.white))
//            }
//
//            val songCountTextView = TextView(context).apply {
//                layoutParams = LinearLayout.LayoutParams(
//                    LinearLayout.LayoutParams.MATCH_PARENT,
//                    LinearLayout.LayoutParams.WRAP_CONTENT
//                ).apply {
//                    gravity = Gravity.CENTER_HORIZONTAL
//                }
//                maxLines = 1
//                gravity = Gravity.CENTER
//                ellipsize = TextUtils.TruncateAt.END
//                var count =  File(playlistPath).readLines().size-1
//                text = (count.toString() + " Song" + if(count != 1) {"s"} else {""})
//                textSize = 15f
//                typeface = ResourcesCompat.getFont(context, R.font.inter)
//                setTextColor(ContextCompat.getColor(context, R.color.white))
//            }
//
//
//
//            linearLayout.addView(imageView)
//            linearLayout.addView(textView)
//            linearLayout.addView(songCountTextView)
//
//            linearLayout.setOnTouchListener { v, event ->
//                if (event.action == MotionEvent.ACTION_UP) {
//                    // Call your function here when touch is released
//
//                    val anim = AnimationUtils.loadAnimation(context, R.anim.release)
//                    linearLayout.startAnimation(anim)
//                    Log.d("Track Card", "Playlist Clicked")
//
//                    var viewPlaylistFragment = ViewPlaylistFragment.newInstance(playlistName, playlistPath)
//                    val transaction = (context as MainActivity).supportFragmentManager.beginTransaction()
//                    transaction.setCustomAnimations(
//                        R.anim.slide_in_right, // Enter animation
//                        com.google.android.material.R.anim.abc_fade_out, // Exit animation
//                        com.google.android.material.R.anim.abc_fade_in, // Pop enter animation
//                        R.anim.slide_out_right // Pop exit animation
//                    )
//                    transaction.replace(R.id.MainView, viewPlaylistFragment)
//                    transaction.addToBackStack(null) // Allows back navigation
//                    transaction.commit()
//
//                    true // Return true to indicate the event was handled
//                }
//                else if(event.action == MotionEvent.ACTION_DOWN) {
//                    val anim = AnimationUtils.loadAnimation(context, R.anim.press)
//                    linearLayout.startAnimation(anim)
//                    true
//                }
//                else if(event.action == MotionEvent.ACTION_CANCEL) {
//                    val anim = AnimationUtils.loadAnimation(context, R.anim.release)
//                    linearLayout.startAnimation(anim)
//                    false
//                }
//                else {
//                    false // Return false to allow other touch events to be processed
//                }
//            }
//
//            rootLayout.addView(linearLayout)
//
//        }
        lateinit var mainActivityContext: Context
        fun saveLastPlayed(mediaItem: MediaItem, ofPlaylist: Playlist) {
            Log.i("SaveLastPlayed", "Saving process started")
            val newTracks = mutableListOf<TrackEntity>()
            val database = AppDatabase.getDatabase(mainActivityContext)
            val dao = database.trackDao()
            CoroutineScope(Dispatchers.IO).launch {
                var i = 0
                for (item in ofPlaylist.mediaItems!!) {
                    if(item.localConfiguration?.uri == mediaItem.localConfiguration?.uri) {
                        break
                    }
                    i++
                }
                for(item in MusicService.playlist?.mediaItems!!) {
                    val uri = item.localConfiguration?.uri
                    var title = item.mediaMetadata.title
                    var artist = item.mediaMetadata.artist
//                    if(item.mediaMetadata.artworkData == null)
//                        Log.e("SearchSongs", "No artwork")
                    if(uri == null)
                        Log.e("SearchSongs", "No uri")
                    if(title == null) {
                        title = (item.mediaMetadata.title?:"Unknown Title").toString()
                    }
                    if(artist == null) {
                        artist = (item.mediaMetadata.artist?:"Unknown Artist").toString()
                    }
                    if(newTracks == null)
                        Log.e("SearchSongs", "No newTracks")

                    newTracks.add(TrackEntity(uri.toString(), title.toString(), artist.toString(), item.mediaMetadata.artworkData))
                }
                dao.clearTracks()
                dao.insertTracks(newTracks)
                sharedPreferences.edit()
                    .putInt("lastPlayedIndex", i)
                    .putLong("lastPlayedDuration", 0)
                    .commit()
                Log.i("SaveLastPlayed", "Saving process finished")
            }
        }
        fun systemHidePlayerUI(context: Context) {
            val anim = AnimationUtils.loadAnimation(context, R.anim.slide_out_down)
            minimizedplayer?.startAnimation(anim)
            anim.setAnimationListener(object: Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
//                    TODO("Not yet implemented")
                }

                override fun onAnimationEnd(animation: Animation?) {
                    minimizedplayer?.visibility = LinearLayout.GONE
                    minimizedplayer?.elevation = -1f
                }

                override fun onAnimationRepeat(animation: Animation?) {
//                    TODO("Not yet implemented")
                }
            })

        }
        fun systemShowPlayerUI(context: Context) {
            minimizedplayer?.visibility = LinearLayout.VISIBLE
            minimizedplayer?.elevation = 3f
            val anim = AnimationUtils.loadAnimation(context, R.anim.slide_in_down)
            minimizedplayer?.startAnimation(anim)
        }

        fun setPlayerUI(context: Context, songName: String, songArtist: String, image: Bitmap?, skipAnimation: Boolean = false) {
            var dominantColor = getDominantColor(context, image)
// Get the current background gradient drawable from minimizedplayer
            // Get the current background gradient drawable from minimizedplayer
            val oldGradientDrawable = minimizedplayer?.background as? GradientDrawable ?: GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = if(!MPExpanded){ dpToPx(context, 20).toFloat()}else{0f}
                colors = intArrayOf(dominantColor, darkenColor(dominantColor, 50)) // Default colors in case the background is not a GradientDrawable
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = if(MPExpanded) {Orientation.TOP_BOTTOM} else { Orientation.LEFT_RIGHT}
                setSize(dpToPx(context, 360), dpToPx(context, 70))
            }

// Get the current colors from the existing gradient drawable (minimizedplayer.background)
            val initialColors = oldGradientDrawable.colors

// New colors you want to transition to
            val newColors = intArrayOf(dominantColor, darkenColor(dominantColor, 50))

// Create the new gradient drawable
            val gradientDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = if(!MPExpanded){ dpToPx(context, 20).toFloat()}else{0f}
                colors = newColors
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = if(MPExpanded) {Orientation.TOP_BOTTOM} else { Orientation.LEFT_RIGHT}
                setSize(dpToPx(context, 360), dpToPx(context, 70))
            }

// Set the initial background
            minimizedplayer?.background = oldGradientDrawable

// Animate the left color (first color)
            val leftColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), initialColors!![0], newColors[0])
            leftColorAnimator.duration = 500 // Set the duration of the transition

// Animate the right color (second color)
            val rightColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), initialColors!![1], newColors[1])
            rightColorAnimator.duration = 500 // Set the duration of the transition

// Add update listeners for both animators
            leftColorAnimator.addUpdateListener { animator ->
                val leftColor = animator.animatedValue as Int
                val rightColor = gradientDrawable.colors!![1] // Keep the right color fixed for now
                gradientDrawable.colors = intArrayOf(leftColor, rightColor)
                minimizedplayer?.background = gradientDrawable
                if(MPExpanded) {
                    (context as Activity).window.statusBarColor = animator.animatedValue as Int
                }
            }

            rightColorAnimator.addUpdateListener { animator ->
                val rightColor = animator.animatedValue as Int
                val leftColor = gradientDrawable.colors!![0] // Keep the left color fixed for now
                gradientDrawable.colors = intArrayOf(leftColor, rightColor)
                minimizedplayer?.background = gradientDrawable
                if(MPExpanded) {
                    (context as Activity).window.navigationBarColor = animator.animatedValue as Int
                }
            }

// Start both animators
            if(!skipAnimation) {
                leftColorAnimator.start()
                rightColorAnimator.start()
            }
            else {
                gradientDrawable.colors = newColors
                minimizedplayer?.background = gradientDrawable
            }



            minimizedplayercover?.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(context, 15).toFloat() // 15dp rounded corners
            }

            minimizedplayercover?.scaleType = ImageView.ScaleType.CENTER_CROP
            minimizedplayercover?.clipToOutline = true
            minimizedplayercover?.setImageBitmap(image)

            minimizedplayersongname?.text = songName
            val (red, green, blue) = getRGBValues(dominantColor)
            minimizedplayersongname?.setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
            minimizedplayerartists?.text = songArtist
            minimizedplayerartists?.setTextColor(if((red*0.299 + green*0.587 + blue*0.114) > 156) {ContextCompat.getColor(context, R.color.black)} else {ContextCompat.getColor(context, R.color.white)})
            if((red*0.299 + green*0.587 + blue*0.114) > 156) {
                DarkPlayer = false
                minimizedplayerpreviousbtn?.foreground = ContextCompat.getDrawable(context,R.drawable.baseline_skip_previous_24)
                minimizedplayerplaypausebtn?.foreground = ContextCompat.getDrawable(context,if(musicService?.player?.isPlaying == true) { R.drawable.baseline_pause_24 } else { R.drawable.baseline_play_arrow_24})
                minimizedplayerplaypausespinner?.indeterminateTintList = ContextCompat.getColorStateList(context,R.color.black)
                minimizedplayernextbtn?.foreground = ContextCompat.getDrawable(context,R.drawable.baseline_skip_next_24)
                minimizedplayerseekbar?.progressTintList = ContextCompat.getColorStateList(context, R.color.black)

            } else {
                DarkPlayer = true
                minimizedplayerpreviousbtn?.foreground = ContextCompat.getDrawable(context,R.drawable.baseline_skip_previous_24_white)
                minimizedplayerplaypausebtn?.foreground = ContextCompat.getDrawable(context,if(musicService?.player?.isPlaying == true) { R.drawable.baseline_pause_24_white } else { R.drawable.baseline_play_arrow_24_white})
                minimizedplayerplaypausespinner?.indeterminateTintList = ContextCompat.getColorStateList(context,R.color.white)
                minimizedplayernextbtn?.foreground = ContextCompat.getDrawable(context,R.drawable.baseline_skip_next_24_white)
                minimizedplayerseekbar?.progressTintList = ContextCompat.getColorStateList(context,R.color.white)
            }
            if(shuffleMode) {
                minimizedplayershufflebtn?.foreground =
                    AppCompatResources.getDrawable(
                        context, if (DarkPlayer) {
                            R.drawable.baseline_shuffle_on_24_white
                        } else {
                            R.drawable.baseline_shuffle_on_24
                        }
                    )
            }
            else {
                minimizedplayershufflebtn?.foreground =
                    AppCompatResources.getDrawable(
                        context, if (DarkPlayer) {
                            R.drawable.baseline_shuffle_24_white
                        } else {
                            R.drawable.baseline_shuffle_24
                        }
                    )
            }
            if(repeatMode == Player.REPEAT_MODE_OFF) {
                minimizedplayerrepeatbtn?.foreground =
                    AppCompatResources.getDrawable(
                        context, if (DarkPlayer) {
                            R.drawable.baseline_forwards_auto_24_white
                        } else {
                            R.drawable.baseline_forwards_auto_24
                        }
                    )
            }
            else if(repeatMode == Player.REPEAT_MODE_ALL) {
                minimizedplayerrepeatbtn?.foreground =
                    AppCompatResources.getDrawable(
                        context, if (DarkPlayer) {
                            R.drawable.baseline_repeat_24_white
                        } else {
                            R.drawable.baseline_repeat_24
                        }
                    )
            }
            else if(repeatMode == Player.REPEAT_MODE_ONE) {
                minimizedplayerrepeatbtn?.foreground =
                    AppCompatResources.getDrawable(
                        context, if (DarkPlayer) {
                            R.drawable.baseline_repeat_one_24_white
                        } else {
                            R.drawable.baseline_repeat_one_24
                        }
                    )
            }
        }
        fun layerDrawableToBitmap(layerDrawable: LayerDrawable): Bitmap {
            val width = layerDrawable.intrinsicWidth.takeIf { it > 0 } ?: 100
            val height = layerDrawable.intrinsicHeight.takeIf { it > 0 } ?: 100

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            layerDrawable.setBounds(0, 0, canvas.width, canvas.height)
            layerDrawable.draw(canvas)

            return bitmap
        }
        @OptIn(UnstableApi::class)
        suspend fun loadSongsConcurrently(context: Context, templist: List<Uri>, titleOverride: Array<String> = arrayOf(), artistOverride: Array<String> = arrayOf()) = withContext(
            Dispatchers.IO) {
            val deferredList = templist.mapIndexed { index, uri ->
                async {
                    val mediaItem = MainActivity.getAudioMetadataFFmpeg(uri)
                    // TODO: TITLE OVERRIDE
                    Pair(mediaItem, index) // Return the item and its index for progress tracking
                }
            }

            val songResults = deferredList.awaitAll() // Wait for all tasks to complete
            val sortedSongs = songResults.sortedBy { it.second }.map { it.first } // Preserve order
            return@withContext sortedSongs // Return the final list
        }
        @OptIn(UnstableApi::class)
        fun playTrack(context: Context, mediaItem: MediaItem, ofPlaylist: Playlist) {
            Log.i("playTrack", "Initiated Playtrack")
            setPlayerUI(context, mediaItem?.mediaMetadata?.title.toString(), mediaItem?.mediaMetadata?.artist.toString(),
                if(mediaItem?.mediaMetadata?.artworkData != null) {BitmapFactory.decodeByteArray(mediaItem?.mediaMetadata?.artworkData, 0, mediaItem?.mediaMetadata?.artworkData?.size!!) } else {layerDrawableToBitmap(ContextCompat.getDrawable(context, R.drawable.default_music_icon) as LayerDrawable)}
            )
            Log.i("playTrack", "UI Set")
            musicService?.player?.addListener(object : Player.Listener {
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    super.onShuffleModeEnabledChanged(shuffleModeEnabled)
                    shuffleMode = shuffleModeEnabled
                    if(shuffleModeEnabled) {
                        minimizedplayershufflebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_shuffle_on_24_white
                                } else {
                                    R.drawable.baseline_shuffle_on_24
                                }
                            )
                    }
                    else {
                        minimizedplayershufflebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_shuffle_24_white
                                } else {
                                    R.drawable.baseline_shuffle_24
                                }
                            )
                    }
                }
                override fun onRepeatModeChanged(repeatMode: Int) {
                    super.onRepeatModeChanged(repeatMode)
                    this@Companion.repeatMode = repeatMode
                    if(repeatMode == Player.REPEAT_MODE_OFF) {
                        minimizedplayerrepeatbtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_forwards_auto_24_white
                                } else {
                                    R.drawable.baseline_forwards_auto_24
                                }
                            )
                    }
                    else if(repeatMode == Player.REPEAT_MODE_ALL) {
                        minimizedplayerrepeatbtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_repeat_24_white
                                } else {
                                    R.drawable.baseline_repeat_24
                                }
                            )
                    }
                    else if(repeatMode == Player.REPEAT_MODE_ONE) {
                        minimizedplayerrepeatbtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_repeat_one_24_white
                                } else {
                                    R.drawable.baseline_repeat_one_24
                                }
                            )
                    }

                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    prepareSeekBar(context as Activity)
                    setPlayerUI(context, mediaItem?.mediaMetadata?.title.toString(), mediaItem?.mediaMetadata?.artist.toString(),
                        if(mediaItem?.mediaMetadata?.artworkData != null) {BitmapFactory.decodeByteArray(mediaItem?.mediaMetadata?.artworkData, 0, mediaItem?.mediaMetadata?.artworkData?.size!!) } else {layerDrawableToBitmap(ContextCompat.getDrawable(context, R.drawable.default_music_icon) as LayerDrawable)}
                    )
                    if (musicService?.player?.isPlaying == true) {
                        minimizedplayerplaypausebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_pause_24_white
                                } else {
                                    R.drawable.baseline_pause_24
                                }
                            )
                    } else {
                        minimizedplayerplaypausebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_play_arrow_24_white
                                } else {
                                    R.drawable.baseline_play_arrow_24
                                }
                            )
                    }
                    if(mediaItem != null) {
                        // saveLastPlayed(mediaItem, ofPlaylist)
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        prepareSeekBar(context as Activity)
                        minimizedplayerplaypausebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_pause_24_white
                                } else {
                                    R.drawable.baseline_pause_24
                                }
                            )
                    } else {
                        playing = false
                        looper?.join()
                        minimizedplayerplaypausebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_play_arrow_24_white
                                } else {
                                    R.drawable.baseline_play_arrow_24
                                }
                            )
                    }
                }
            })
            Log.i("playTrack", "Listener set")
            CoroutineScope(Dispatchers.Main).launch {
                musicService?.player?.repeatMode = repeatMode
            }
            MusicService.playlist = ofPlaylist
            MusicService.items = ofPlaylist.mediaItems
            MusicService.targetMediaItem = mediaItem
            val intent = Intent(context, MusicService::class.java).apply {
                putExtra("ACTION", "PLAY")
            }
            startForegroundService(context, intent)
            Log.i("playTrack", "Finished")
//            saveLastPlayed(mediaItem, ofPlaylist)
        }
        @OptIn(UnstableApi::class)
        fun playTrack(context: Context, index: Int, position: Long? = null) {
            setPlayerLoadingUI()
            Log.i("playTrack", "Initiated Playtrack")
            var Queue = QueueManager.getMemoryQueue()
            var mediaItem = Queue!!.mediaItems?.get(if(index < 0) { 0 } else {index})
            setPlayerUI(context, mediaItem?.mediaMetadata?.title.toString(), mediaItem?.mediaMetadata?.artist.toString(),
                if(mediaItem?.mediaMetadata?.artworkData != null) {BitmapFactory.decodeByteArray(mediaItem?.mediaMetadata?.artworkData, 0, mediaItem?.mediaMetadata?.artworkData?.size!!) } else {layerDrawableToBitmap(ContextCompat.getDrawable(context, R.drawable.default_music_icon) as LayerDrawable)},
                if(position != null) {true} else {false}
            )
            Log.i("playTrack", "UI Set")
            musicService?.player?.removeListener(playbackListener)
            playbackListener = object : Player.Listener {
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    super.onShuffleModeEnabledChanged(shuffleModeEnabled)
                    shuffleMode = shuffleModeEnabled
                    if(shuffleModeEnabled) {
                        minimizedplayershufflebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_shuffle_on_24_white
                                } else {
                                    R.drawable.baseline_shuffle_on_24
                                }
                            )
                    }
                    else {
                        minimizedplayershufflebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_shuffle_24_white
                                } else {
                                    R.drawable.baseline_shuffle_24
                                }
                            )
                    }
                }
                override fun onRepeatModeChanged(repeatMode: Int) {
                    super.onRepeatModeChanged(repeatMode)
                    this@Companion.repeatMode = repeatMode
                    if(repeatMode == Player.REPEAT_MODE_OFF) {
                        minimizedplayerrepeatbtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_forwards_auto_24_white
                                } else {
                                    R.drawable.baseline_forwards_auto_24
                                }
                            )
                    }
                    else if(repeatMode == Player.REPEAT_MODE_ALL) {
                        minimizedplayerrepeatbtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_repeat_24_white
                                } else {
                                    R.drawable.baseline_repeat_24
                                }
                            )
                    }
                    else if(repeatMode == Player.REPEAT_MODE_ONE) {
                        minimizedplayerrepeatbtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_repeat_one_24_white
                                } else {
                                    R.drawable.baseline_repeat_one_24
                                }
                            )
                    }

                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    var seekbarmillis = measureTimeMillis {
                        prepareSeekBar(context as Activity)
                    }
                    var playerUImillis = measureTimeMillis {
                        setPlayerUI(
                            context,
                            mediaItem?.mediaMetadata?.title.toString(),
                            mediaItem?.mediaMetadata?.artist.toString(),
                            if (mediaItem?.mediaMetadata?.artworkData != null) {
                                BitmapFactory.decodeByteArray(
                                    mediaItem?.mediaMetadata?.artworkData,
                                    0,
                                    mediaItem?.mediaMetadata?.artworkData?.size!!
                                )
                            } else {
                                layerDrawableToBitmap(
                                    ContextCompat.getDrawable(
                                        context,
                                        R.drawable.default_music_icon
                                    ) as LayerDrawable
                                )
                            }
                        )
                    }
                    Log.w("playTrackWatchdog", "Seekbar:  $seekbarmillis ms, PlayerUI: $playerUImillis ms")
                    if (musicService?.player?.isPlaying == true) {
                        minimizedplayerplaypausebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_pause_24_white
                                } else {
                                    R.drawable.baseline_pause_24
                                }
                            )
                    } else {
                        minimizedplayerplaypausebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_play_arrow_24_white
                                } else {
                                    R.drawable.baseline_play_arrow_24
                                }
                            )
                    }
                    if(mediaItem != null) {
                        // saveLastPlayed(mediaItem, ofPlaylist)
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        prepareSeekBar(context as Activity)
                        minimizedplayerplaypausebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_pause_24_white
                                } else {
                                    R.drawable.baseline_pause_24
                                }
                            )
                    } else {
                        playing = false
                        looper?.join()
                        minimizedplayerplaypausebtn?.foreground =
                            AppCompatResources.getDrawable(
                                context, if (DarkPlayer) {
                                    R.drawable.baseline_play_arrow_24_white
                                } else {
                                    R.drawable.baseline_play_arrow_24
                                }
                            )
                    }
                }
            }
            musicService?.player?.addListener(playbackListener)
            Log.i("playTrack", "Listener set")
            CoroutineScope(Dispatchers.Main).launch {
                musicService?.player?.repeatMode = repeatMode
            }
            Log.w("playTrackWatchdog", "intent launcher: " + measureTimeMillis {
                MusicService.playlist = Queue
                MusicService.items = Queue!!.mediaItems
                MusicService.targetMediaItem = mediaItem
                val intent = Intent(context, MusicService::class.java).apply {
                    putExtra("ACTION", "PLAY")
                    if(position != null) {
                        putExtra("POSITION", position)
                        putExtra("INDEX", index)
                        putExtra("RESTORE", true)
                    }
                }
                startForegroundService(context, intent)
            } + "ms")
            Log.i("playTrack", "Finished")
//            saveLastPlayed(mediaItem, ofPlaylist)
        }
        fun prepareSeekBar(activity: Activity) {
            minimizedplayerseekbar?.min = 0
            minimizedplayerseekbar?.max = musicService?.player?.duration?.toInt()!!
            playing = true
            looper = Thread {
//                var duration: Long = 0
                while(playing) {
                    activity.runOnUiThread {
                        minimizedplayerseekbar?.progress = musicService?.player?.currentPosition!!.toInt()
//                        duration = musicService?.player?.currentPosition!!
                        QueueManager.savePlaybackState(activity.baseContext, musicService?.player?.currentMediaItemIndex?:0, musicService?.player?.currentPosition?:0)
                    }

                    Thread.sleep(500)
                }
            }
            looper?.start()

            // Allow user to seek
            minimizedplayerseekbar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) { // Only seek if the user is dragging
//                    musicService?.player?.seekTo(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
//                    musicService?.player?.pause()
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    var progress = seekBar?.progress
//                if (fromUser) { // Only seek if the user is dragging
                    musicService?.player?.seekTo(progress!!.toLong())
                    musicService?.player?.play()
//                }
                }
            })
        }
        fun setPlayerLoadingUI() {
            minimizedplayerplaypausespinner?.visibility = Button.VISIBLE
            minimizedplayerplaypausebtn?.visibility = Button.GONE
        }
        fun setPlayerNotLoadingUI() {
            minimizedplayerplaypausespinner?.visibility = Button.GONE
            minimizedplayerplaypausebtn?.visibility = Button.VISIBLE
        }

        var minimizedplayer: LinearLayout? = null
        var minimizedplayercover: ImageView? = null
        var minimizedplayersongname: TextView? = null
        var minimizedplayerartists: TextView? = null
        var minimizedplayerpreviousbtn: Button? = null
        var minimizedplayerplaypausebtn: Button? = null
        var minimizedplayerplaypausespinner: ProgressBar? = null
        var minimizedplayernextbtn: Button? = null
        var minimizedplayershufflebtn: Button? = null
        var minimizedplayerrepeatbtn: Button? = null
        var minimizedplayerseekbar: SeekBar? = null
        var DarkPlayer = false
        var repeatMode = 2
        var shuffleMode = false
        var playing: Boolean = false
        var MPExpanded = false
        private var looper: Thread? = null
    }

    private var isBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            musicService = null
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }

//    private fun checkStoragePermission() {
//        var permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            android.Manifest.permission.READ_MEDIA_AUDIO
//        } else {
//            android.Manifest.permission.READ_EXTERNAL_STORAGE
//        }
//
//        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
//
//        } else {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 9)
//            } else {
//                requestPermissions(arrayOf( android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE),9)
//            }
//            checkStoragePermission()
//        }
//    }
    private fun checkStoragePermission() {
        var permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {

        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO), 9)
            } else {
                requestPermissions(arrayOf( android.Manifest.permission.READ_EXTERNAL_STORAGE),9)
            }
            checkStoragePermission()
        }
    }

    var MinimizedPlayerOldVals = mutableMapOf<String, Any?>()
    fun expandMinimizedPlayer() {
        var MP = findViewById<LinearLayout>(R.id.MinimizedPlayerBG)
        var MPContent = findViewById<LinearLayout>(R.id.MinimizedPlayerContentLayout)
        var MPImageView = findViewById<ImageView>(R.id.MinimizedPlayerImageView)
        var MPSongNameView = findViewById<TextView>(R.id.MinimizedPlayerSongNameView)
        var MPArtistView = findViewById<TextView>(R.id.MinimizedPlayerArtistView)
        var MPSeekBar = findViewById<SeekBar>(R.id.MinimizedPlayerSeekBar)
        var MPShuffleBtn = findViewById<Button>(R.id.MinimizedPlayerShuffleBtn)
        var MPRepeatBtn = findViewById<Button>(R.id.MinimizedPlayerRepeatBtn)
        var MPPlayPauseBtn = findViewById<Button>(R.id.MinimizedPlayerPlayPauseBtn)
        var MPNextBtn = findViewById<Button>(R.id.MinimizedPlayerNextBtn)
        var MPPreviousBtn = findViewById<Button>(R.id.MinimizedPlayerPreviousBtn)
        var MPControls = findViewById<LinearLayout>(R.id.MinimizedPlayerControls)
        MinimizedPlayerOldVals["MPImageView_foregroundGravity"] = MPImageView.foregroundGravity
        MPImageView.foregroundGravity =  Gravity.CENTER
        MinimizedPlayerOldVals["MPImageView_layoutParams"] = MPImageView.layoutParams
        MPImageView.layoutParams = LinearLayout.LayoutParams(dpToPx(this, 190), dpToPx(this, 190)).apply {
            setMargins(dpToPx(this@MainActivity,12), dpToPx(this@MainActivity, 30), dpToPx(this@MainActivity, 10), dpToPx(this@MainActivity, 30))
        }
        MinimizedPlayerOldVals["MPContent_orientation"] = MPContent.orientation
        MPContent.orientation = LinearLayout.VERTICAL
        MinimizedPlayerOldVals["MPContent_gravity"] = MPContent.gravity
        MPContent.gravity = Gravity.CENTER
        MinimizedPlayerOldVals["MPContent_layoutParams"] = MPContent.layoutParams
        MPContent.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 0f
            setMargins(dpToPx(this@MainActivity, 20), 0, dpToPx(this@MainActivity, 20),0)
        }
        MinimizedPlayerOldVals["MPSongNameView_textAlignment"] = MPSongNameView.textAlignment
        MPSongNameView.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        MinimizedPlayerOldVals["MPArtistView_textAlignment"] = MPArtistView.textAlignment
        MPArtistView.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        MinimizedPlayerOldVals["MPSongNameView_textSize"] = MPSongNameView.textSize
        MPSongNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP,24f)
        MinimizedPlayerOldVals["MPArtistView_textSize"] = MPArtistView.textSize
        MPArtistView.setTextSize(TypedValue.COMPLEX_UNIT_SP,20f)

        MinimizedPlayerOldVals["MPSeekBar_layoutParams"] = MPSeekBar.layoutParams
        MPSeekBar.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(this, 20)).apply {
            setMargins(dpToPx(this@MainActivity, 10), dpToPx(this@MainActivity, 30), dpToPx(this@MainActivity, 10),0)
        }
        MinimizedPlayerOldVals["MP_background_orientation"] = (MP.background as GradientDrawable).orientation
        MinimizedPlayerOldVals["MP_background_cornerRadius"] = (MP.background as GradientDrawable).cornerRadius
        (MP.background as GradientDrawable).orientation = GradientDrawable.Orientation.TOP_BOTTOM
        (MP.background as GradientDrawable).cornerRadius = 0f
        MinimizedPlayerOldVals["window_statusBarColor"] = window.statusBarColor
        MinimizedPlayerOldVals["window_navigationBarColor"] = window.navigationBarColor

        MinimizedPlayerOldVals["MPSeekBar_thumb"] = MPSeekBar.thumb
        MPSeekBar.thumb = getDrawable(com.google.android.material.R.drawable.abc_seekbar_thumb_material)
        MinimizedPlayerOldVals["MPSeekBar_thumbTintList"] = MPSeekBar.thumbTintList
        MPSeekBar.thumbTintList = if(DarkPlayer) {getColorStateList(R.color.white)} else {getColorStateList(R.color.black)}

        MinimizedPlayerOldVals["MPShuffleBtn_layoutParams"] = MPShuffleBtn.layoutParams
        MinimizedPlayerOldVals["MPRepeatBtn_layoutParams"] = MPRepeatBtn.layoutParams
        MinimizedPlayerOldVals["MPPlayPauseBtn_layoutParams"] = MPPlayPauseBtn.layoutParams
        MinimizedPlayerOldVals["MPPreviousBtn_layoutParams"] = MPPreviousBtn.layoutParams
        MinimizedPlayerOldVals["MPNextBtn_layoutParams"] = MPNextBtn.layoutParams


        MPShuffleBtn.visibility = Button.VISIBLE
        MPShuffleBtn.layoutParams = LinearLayout.LayoutParams(dpToPx(this, 40), dpToPx(this, 40)).apply {
            setMargins(dpToPx(this@MainActivity, 10), 0, dpToPx(this@MainActivity, 10),0)
        }
        MPRepeatBtn.visibility = Button.VISIBLE
        MPRepeatBtn.layoutParams = LinearLayout.LayoutParams(dpToPx(this, 40), dpToPx(this, 40)).apply {
            setMargins(dpToPx(this@MainActivity, 10), 0, dpToPx(this@MainActivity, 10),0)
        }

        MPPlayPauseBtn.layoutParams = LinearLayout.LayoutParams(dpToPx(this, 40), dpToPx(this, 40)).apply {
            setMargins(dpToPx(this@MainActivity, 10), 0, dpToPx(this@MainActivity, 10),0)
        }
        MPPreviousBtn.layoutParams = LinearLayout.LayoutParams(dpToPx(this, 40), dpToPx(this, 40)).apply {
            setMargins(dpToPx(this@MainActivity, 10), 0, dpToPx(this@MainActivity, 10),0)
        }
        MPNextBtn.layoutParams = LinearLayout.LayoutParams(dpToPx(this, 40), dpToPx(this, 40)).apply {
            setMargins(dpToPx(this@MainActivity, 10), 0, dpToPx(this@MainActivity, 10),0)
        }


        MinimizedPlayerOldVals["MPControls_gravity"] = MPControls.gravity
        MPControls.gravity = Gravity.CENTER_HORIZONTAL
        MinimizedPlayerOldVals["MPControls_layoutParams"] = MPControls.layoutParams
        MPControls.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dpToPx(this@MainActivity, 30),0,0)
        }

        MPSeekBar.bringToFront()

        MP.addView(Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                weight = 1f
            }
            focusable = View.FOCUSABLE
            isClickable = true

        })


    }
    fun collapseMinimizedPlayer() {
        var MP = findViewById<LinearLayout>(R.id.MinimizedPlayerBG)
        var MPContent = findViewById<LinearLayout>(R.id.MinimizedPlayerContentLayout)
        var MPImageView = findViewById<ImageView>(R.id.MinimizedPlayerImageView)
        var MPSongNameView = findViewById<TextView>(R.id.MinimizedPlayerSongNameView)
        var MPArtistView = findViewById<TextView>(R.id.MinimizedPlayerArtistView)
        var MPSeekBar = findViewById<SeekBar>(R.id.MinimizedPlayerSeekBar)
        var MPShuffleBtn = findViewById<Button>(R.id.MinimizedPlayerShuffleBtn)
        var MPRepeatBtn = findViewById<Button>(R.id.MinimizedPlayerRepeatBtn)
        var MPPlayPauseBtn = findViewById<Button>(R.id.MinimizedPlayerPlayPauseBtn)
        var MPNextBtn = findViewById<Button>(R.id.MinimizedPlayerNextBtn)
        var MPPreviousBtn = findViewById<Button>(R.id.MinimizedPlayerPreviousBtn)
        var MPControls = findViewById<LinearLayout>(R.id.MinimizedPlayerControls)
        MPImageView.foregroundGravity = MinimizedPlayerOldVals["MPImageView_foregroundGravity"] as Int
        MPImageView.layoutParams = MinimizedPlayerOldVals["MPImageView_layoutParams"] as LinearLayout.LayoutParams?
        MPContent.orientation = MinimizedPlayerOldVals["MPContent_orientation"] as Int
        MPContent.gravity = MinimizedPlayerOldVals["MPContent_gravity"] as Int
        MPContent.layoutParams = MinimizedPlayerOldVals["MPContent_layoutParams"] as LinearLayout.LayoutParams?
        MPSongNameView.textAlignment = MinimizedPlayerOldVals["MPSongNameView_textAlignment"] as Int
        MPArtistView.textAlignment = MinimizedPlayerOldVals["MPArtistView_textAlignment"] as Int
        MPSongNameView.setTextSize(TypedValue.COMPLEX_UNIT_PX, MinimizedPlayerOldVals["MPSongNameView_textSize"] as Float)
        MPArtistView.setTextSize(TypedValue.COMPLEX_UNIT_PX, MinimizedPlayerOldVals["MPArtistView_textSize"] as Float)
        (MP.background as GradientDrawable).orientation = MinimizedPlayerOldVals["MP_background_orientation"] as Orientation
        (MP.background as GradientDrawable).cornerRadius = MinimizedPlayerOldVals["MP_background_cornerRadius"] as Float
        window.statusBarColor = MinimizedPlayerOldVals["window_statusBarColor"] as Int
        window.navigationBarColor = MinimizedPlayerOldVals["window_navigationBarColor"] as Int
        MPSeekBar.layoutParams = MinimizedPlayerOldVals["MPSeekBar_layoutParams"] as LinearLayout.LayoutParams?
        MPSeekBar.thumb = MinimizedPlayerOldVals["MPSeekBar_thumb"] as Drawable?
        MPSeekBar.thumbTintList = MinimizedPlayerOldVals["MPSeekBar_thumbTintList"] as ColorStateList?
        MPShuffleBtn.visibility = Button.GONE
        MPRepeatBtn.visibility = Button.GONE

        MPRepeatBtn.layoutParams = MinimizedPlayerOldVals["MPRepeatBtn_layoutParams"] as LinearLayout.LayoutParams
        MPShuffleBtn.layoutParams = MinimizedPlayerOldVals["MPShuffleBtn_layoutParams"] as LinearLayout.LayoutParams
        MPPlayPauseBtn.layoutParams = MinimizedPlayerOldVals["MPPlayPauseBtn_layoutParams"] as LinearLayout.LayoutParams
        MPNextBtn.layoutParams = MinimizedPlayerOldVals["MPNextBtn_layoutParams"] as LinearLayout.LayoutParams
        MPPreviousBtn.layoutParams = MinimizedPlayerOldVals["MPPreviousBtn_layoutParams"] as LinearLayout.LayoutParams

        MPControls.gravity = MinimizedPlayerOldVals["MPControls_gravity"] as Int
        MPControls.layoutParams = MinimizedPlayerOldVals["MPControls_layoutParams"] as LinearLayout.LayoutParams?

        MP.removeViewAt(MP.childCount-1)
        MPContent.bringToFront()
    }

    fun importPlaylist(uri: Uri) {
        var file = File(uri.path)
        var playlistdir = getDir("Playlists", Context.MODE_PRIVATE)
        file.copyTo(File(playlistdir, file.name), true) // Copy with the original filename
        var mainFragment = MainFragment()
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.MainView, mainFragment)
        transaction.runOnCommit {
            MaterialAlertDialogBuilder(this)
                .setTitle("Playlist Imported")
                .setMessage("The playlist '${file.nameWithoutExtension}' has been successfully imported.")
                .setPositiveButton("OK", null)
                .show()
        }
        transaction.commit()

    }

    private fun getFileUriFromContentUri(contentUri: Uri): Uri? {
        val contentResolver = contentResolver
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        contentResolver.query(contentUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val filePath = cursor.getString(columnIndex)
                return Uri.fromFile(File(filePath))
            }
        }
        return null
    }


    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        mainActivityContext = this

//        QueueManager.
        /*when {
            intent?.action == Intent.ACTION_SEND -> {
                if ("audio/x-mpegurl" == intent.type) {

                }
            }
            intent?.action == Intent.ACTION_VIEW -> {
                if ("audio/x-mpegurl" == intent.type) {

                }
            }
        }*/
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if ("audio/x-mpegurl" == intent.type) {
                    (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { contentUri ->
                        getFileUriFromContentUri(contentUri)?.let { fileUri ->
                            importPlaylist(fileUri)
                        }
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { contentUri ->
                    getFileUriFromContentUri(contentUri)?.let { fileUri ->
                        importPlaylist(fileUri)
                    }
                }
            }
        }

        sharedPreferences = getPreferences(MODE_PRIVATE)
        if(!sharedPreferences.getBoolean("AppInitialized", false)) {
//            startActivity(Intent(baseContext, GetStartedActivity::class.java))
        }
        window.statusBarColor = getColor(R.color.background)
        window.navigationBarColor = getColor(R.color.background)
        minimizedplayer = findViewById<LinearLayout>(R.id.MinimizedPlayerBG)
        minimizedplayercover = findViewById<ImageView>(R.id.MinimizedPlayerImageView)
        minimizedplayersongname = findViewById<TextView>(R.id.MinimizedPlayerSongNameView)
        minimizedplayerartists = findViewById<TextView>(R.id.MinimizedPlayerArtistView)
        minimizedplayerpreviousbtn = findViewById<Button>(R.id.MinimizedPlayerPreviousBtn)
        minimizedplayerplaypausebtn = findViewById<Button>(R.id.MinimizedPlayerPlayPauseBtn)
        minimizedplayerplaypausespinner = findViewById<ProgressBar>(R.id.MinimizedPlayerPlayPauseSpinner)
        minimizedplayernextbtn = findViewById<Button>(R.id.MinimizedPlayerNextBtn)
        minimizedplayerrepeatbtn = findViewById<Button>(R.id.MinimizedPlayerRepeatBtn)
        minimizedplayershufflebtn = findViewById<Button>(R.id.MinimizedPlayerShuffleBtn)
        minimizedplayerseekbar = findViewById<SeekBar>(R.id.MinimizedPlayerSeekBar)
        var mainFragment = MainFragment()
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.MainView, mainFragment)
        transaction.commit()
        checkStoragePermission()
        findViewById<TextView>(R.id.MinimizedPlayerSongNameView).setSelected(true)
        findViewById<TextView>(R.id.MinimizedPlayerArtistView).setSelected(true)
        findViewById<Button>(R.id.MinimizedPlayerPreviousBtn).setOnClickListener {
            musicService?.player?.seekToPreviousMediaItem()
        }
        findViewById<Button>(R.id.MinimizedPlayerNextBtn).setOnClickListener {
            musicService?.player?.seekToNextMediaItem()
        }
        findViewById<Button>(R.id.MinimizedPlayerNextBtn).setOnTouchListener (object: View.OnTouchListener {
            var isLongPress = false
            val handler = Handler(Looper.getMainLooper())
            val longPressRunnable = Runnable {
                isLongPress = true
                findViewById<Button>(R.id.MinimizedPlayerNextBtn).performLongClick()
                println("Long Click Detected!")
                musicService?.player?.setPlaybackSpeed(2f)
                var t = Toast.makeText(baseContext, "Speed: 2x", Toast.LENGTH_SHORT)
                t.setGravity(Gravity.TOP, 0,0)
                t.show()
            }
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event?.action == MotionEvent.ACTION_UP) {
                    // Call your function here when touch is released
                    handler.removeCallbacks(longPressRunnable)
                    if (!isLongPress) {
                        // Handle normal click here
                        println("Short Click Detected!")
                        findViewById<Button>(R.id.MinimizedPlayerNextBtn).performClick()
                    }
                    musicService?.player?.setPlaybackSpeed(1f)

                    return true // Return true to indicate the event was handled
                } else if (event?.action == MotionEvent.ACTION_MOVE) {
                    return true
                } else if (event?.action == MotionEvent.ACTION_DOWN) {
                    isLongPress = false
                    handler.postDelayed(longPressRunnable, 500) // 500ms for long press
                    return true
                } else if (event?.action == MotionEvent.ACTION_CANCEL) {
                    handler.removeCallbacks(longPressRunnable)
                    return false
                } else {
                    return false // Return false to allow other touch events to be processed
                }
            }
        })
//        findViewById<Button>(R.id.MinimizedPlayerNextBtn).setOnTouchListener (object: View.OnTouchListener {
//            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
//                if (event?.action == MotionEvent.ACTION_DOWN) {
//                    musicService?.player?.setPlaybackSpeed(2f)
//                }
//                else if (event?.action == MotionEvent.ACTION_UP) {
//                    musicService?.player?.setPlaybackSpeed(1f)
//                }
//                return true
//            }
//        })
        findViewById<Button>(R.id.MinimizedPlayerShuffleBtn).setOnClickListener {
            musicService?.player?.shuffleModeEnabled = !(musicService?.player?.shuffleModeEnabled == true)
        }
        findViewById<Button>(R.id.MinimizedPlayerRepeatBtn).setOnClickListener {
            if(repeatMode == 0) {
                repeatMode = 1
            }
            else if(repeatMode == 1) {
                repeatMode = 2
            }
            else {
                repeatMode = 0
            }
            musicService?.player?.repeatMode = repeatMode
        }


        findViewById<Button>(R.id.MinimizedPlayerPlayPauseBtn).setOnClickListener {
            var volume: Int = 0
            volume = (musicService?.player?.volume?.times(100))?.toInt() ?: 50
            if(musicService?.player?.isPlaying == true) {
                it.foreground = AppCompatResources.getDrawable(baseContext,if(DarkPlayer) {R.drawable.baseline_play_arrow_24_white} else {R.drawable.baseline_play_arrow_24})
                Thread {
                    for (i in 0..volume) {
                        runOnUiThread {
                            musicService?.player?.volume = 1f-(i.toFloat() / 100)
                        }
                        Thread.sleep(2)
                    }
                    runOnUiThread {
                        musicService?.player?.pause()
                        musicService?.player?.volume = (volume.toFloat()/100)
                    }
                }.start()
//                musicService?.player?.pause()
            }
            else {
                it.foreground = AppCompatResources.getDrawable(baseContext, if(DarkPlayer) {R.drawable.baseline_pause_24_white} else {R.drawable.baseline_pause_24})
                Thread {
                    runOnUiThread {
                        musicService?.player?.play()
                    }
                    for (i in 0..volume) {
                        runOnUiThread {
                            musicService?.player?.volume = (i.toFloat() / 100)
                        }
                        Thread.sleep(2)
                    }
                }.start()
//                musicService?.player?.play()
            }
        }

        val motionLayout = findViewById<MotionLayout>(R.id.motionLayout)
        val miniPlayer = findViewById<LinearLayout>(R.id.MinimizedPlayerBG)
        miniPlayer.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            private val SWIPE_THRESHOLD = 50

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.y
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val endY = event.y
                        val deltaY = startY - endY

                        if (abs(deltaY) > SWIPE_THRESHOLD) {
                            if (deltaY > 0) {
                                Log.d("SwipeTest", "Swiping UP, triggering transition to expanded")
                                if(!MPExpanded) {
                                    MPExpanded = true
                                    motionLayout.transitionToEnd()
                                    expandMinimizedPlayer()
                                }
                            } else {
                                Log.d("SwipeTest", "Swiping DOWN, triggering transition to collapsed")
                                if(MPExpanded) {
                                    MPExpanded = false
                                    motionLayout.transitionToStart()
                                    collapseMinimizedPlayer()
                                }
                            }
                            return true
                        }
                    }
                }
                return false
            }
        })


        motionLayout.setTransitionListener(object : MotionLayout.TransitionListener {
            override fun onTransitionStarted(motionLayout: MotionLayout?, startId: Int, endId: Int) {
                Log.d("MotionLayout", "Swipe detected! Transition started.")
            }

            override fun onTransitionChange(motionLayout: MotionLayout?, startId: Int, endId: Int, progress: Float) {}

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                Log.d("MotionLayout", "Transition completed.")
                if(currentId == R.id.expanded) {
                    window.statusBarColor = (findViewById<LinearLayout>(R.id.MinimizedPlayerBG).background as GradientDrawable).colors?.get(0)!!
                    window.navigationBarColor = (findViewById<LinearLayout>(R.id.MinimizedPlayerBG).background as GradientDrawable).colors?.get(1)!!
                }
            }

            override fun onTransitionTrigger(motionLayout: MotionLayout?, triggerId: Int, positive: Boolean, progress: Float) {}
        })

//
////         Toggle between collapsed and expanded on click
//        miniPlayer.setOnClickListener {
//            val currentState = motionLayout.currentState
//            if (currentState == R.id.collapsed) {
//                motionLayout.transitionToState(R.id.expanded)
//            } else {
//                motionLayout.transitionToState(R.id.collapsed)
//            }
//        }
        /*val database = AppDatabase.getDatabase(this)
        val dao = database.trackDao()
        var temp = mutableListOf<MediaItem>()
        CoroutineScope(Dispatchers.IO).launch {
//            dao.clearTracks()
            for (item in dao.getAllTracks()) {
                temp += MediaItem.Builder()
                    .setUri(item.uri)
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setArtworkData(item.artwork)
                        .build())
                    .build()
            }
            if(temp.size > 0) {
                runOnUiThread {
                    musicService?.player?.setMediaItems(temp)
                    musicService?.player?.seekTo(
                        sharedPreferences.getInt("lastPlayedIndex", 0),
                        sharedPreferences.getLong("lastPlayedDuration", 0)
                    )
                    var mediaitem = temp[if(sharedPreferences.getInt("lastPlayedIndex", 0) >= temp.size) {temp.size-1} else {sharedPreferences.getInt("lastPlayedIndex", 0)}]
                    setPlayerUI(
                        this@MainActivity,
                        mediaitem.mediaMetadata.title.toString(),
                        mediaitem.mediaMetadata.artist.toString(),
                        if (mediaitem.mediaMetadata.artworkData != null) {
                            BitmapFactory.decodeByteArray(
                                mediaitem.mediaMetadata.artworkData,
                                0,
                                mediaitem.mediaMetadata.artworkData!!.size
                            )
                        } else {
                            null
                        }
                    )
//                    val intent = Intent(this@MainActivity, MusicService::class.java).apply {
//                        putExtra("ACTION", "PLAY")
//                    }
                    startForegroundService(this@MainActivity, intent)
//                    musicService?.player?.pause()
                }
            }
        }*/
        CoroutineScope(Dispatchers.IO).launch {
            var queue = QueueManager.getPersistentQueue(this@MainActivity)
            if(queue.first != null) {
                runOnUiThread {
                    var mediaItem = queue.first?.mediaItems!![queue.second]
//                    setPlayerUI(
//                        this@MainActivity,
//                        mediaItem.mediaMetadata.title.toString(),
//                        mediaItem.mediaMetadata.artist.toString(),
//                        if (mediaItem.mediaMetadata.artworkData != null) {
//                            BitmapFactory.decodeByteArray(
//                                mediaItem.mediaMetadata.artworkData,
//                                0,
//                                mediaItem.mediaMetadata.artworkData!!.size
//                            )
//                        } else {
//                            null
//                        }
//                    )
                    QueueManager.setQueue(this@MainActivity, queue.first!!)
                    playTrack(this@MainActivity, queue.second, queue.third)
//                    musicService?.player?.seekTo(queue.second, queue.third)
                    MusicService.targetMediaItem = mediaItem
//                    musicService?.player?.pause()
                }
            }
        }
        if(musicService?.player?.isPlaying == true) {
            setPlayerUI(this,
                musicService?.player?.currentMediaItem?.mediaMetadata?.title.toString(),
                musicService?.player?.currentMediaItem?.mediaMetadata?.artist.toString(),
                if(musicService?.player?.currentMediaItem?.mediaMetadata?.artworkData != null) {
                    BitmapFactory.decodeByteArray(musicService?.player?.currentMediaItem?.mediaMetadata?.artworkData, 0, musicService?.player?.currentMediaItem?.mediaMetadata?.artworkData?.size!!)
                } else {null}
            )
            prepareSeekBar(this)
        }
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Activity was already running and is brought to front again.
        // Handle the new intent here if needed (e.g., update UI based on notification click)
        setIntent(intent) // Important to update the activity's intent
        if (intent != null) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        if(musicService?.player?.isPlaying == true) {
            setPlayerUI(this,
                musicService?.player?.currentMediaItem?.mediaMetadata?.title.toString(),
                musicService?.player?.currentMediaItem?.mediaMetadata?.artist.toString(),
                if(musicService?.player?.currentMediaItem?.mediaMetadata?.artworkData != null) {
                    BitmapFactory.decodeByteArray(musicService?.player?.currentMediaItem?.mediaMetadata?.artworkData, 0, musicService?.player?.currentMediaItem?.mediaMetadata?.artworkData?.size!!)
                } else {null}
            )
            prepareSeekBar(this)
        }
    }
    override fun onBackPressed() {
        if(overrideBackButton) {
            overrideBackButtonAction()
        }
        else {
            if(MPExpanded) {
                MPExpanded = false
                findViewById<MotionLayout>(R.id.motionLayout).transitionToStart()
                collapseMinimizedPlayer()
            }
            else
                super.onBackPressed()
        }
    }
}