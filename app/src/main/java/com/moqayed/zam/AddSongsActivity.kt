package com.moqayed.zam

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.children
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.animation.AnimationUtils
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class AddSongsActivity : AppCompatActivity() {
    companion object {
        var selectedItems: MutableList<MediaItem>? = null

    }
    lateinit var adapter: SelectableTrackRowAdapter
    lateinit var downloadSongsLauncher: ActivityResultLauncher<Intent>
    var selected: MutableList<MediaItem> = mutableListOf()
    @Entity(tableName = "tracks")
    data class TrackEntity(
        @PrimaryKey val uri: String, // Unique identifier
        val title: String,
        val artist: String,
        val artwork: ByteArray? // Store the artwork as ByteArray
    )

    @Dao
    interface TrackDao {
        @Query("SELECT * FROM tracks")
        suspend fun getAllTracks(): List<TrackEntity>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertTracks(tracks: List<TrackEntity>)

        @Query("DELETE FROM tracks")
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
                        "track_database"
                    ).build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
    var UIUpdated = false
    @OptIn(UnstableApi::class)
    private fun loadSongs() {
        /*val database = MainFragment.AppDatabase.getDatabase(this)
        val dao = database.trackDao()
        CoroutineScope(Dispatchers.IO).launch {
            val tracks = dao.getAllTracks()

            for (track in tracks) {
                val mediaItem = MediaItem.Builder()
                    .setUri(track.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setArtworkData(track.artwork)
                            .build()
                    )
                    .build()
                items.add(mediaItem)

            }
            withContext(Dispatchers.Main) {
                val recyclerView: RecyclerView = findViewById(R.id.tracklistview)
                recyclerView.layoutManager = LinearLayoutManager(this@AddSongsActivity)
                adapter = SelectableTrackRowAdapter(items, { item ->
                    if(selected.contains(item)) {

                    }
                }, selected)
                recyclerView.adapter = adapter
            }
            Thread {
                while (true) {
                    if(items.size > 0) {
                        runOnUiThread {
                            findViewById<Button>(R.id.playlistAddSongsBtn).isEnabled = true
                            findViewById<Button>(R.id.playlistAddSongsBtn).setTextColor(getColor(R.color.primary))
                        }
                    }
                    else {
                        runOnUiThread {
                            findViewById<Button>(R.id.playlistAddSongsBtn).isEnabled = false
                            findViewById<Button>(R.id.playlistAddSongsBtn).setTextColor(getColor(R.color.textColorSecondary))
                        }
                    }
                }
            }.start()
        }*/
        val recyclerView: RecyclerView = findViewById(R.id.tracklistview)
        recyclerView.layoutManager = LinearLayoutManager(this@AddSongsActivity)
        adapter = SelectableTrackRowAdapter(MainFragment.playlist.mediaItems!!.toMutableList(), { item ->
            if (MainFragment.playlist.mediaItems!!.toMutableList().size > 0) {
                runOnUiThread {
                    findViewById<Button>(R.id.playlistAddSongsBtn).isEnabled = true
                    findViewById<Button>(R.id.playlistAddSongsBtn).setTextColor(getColor(R.color.primary))
                }

            } else {
                runOnUiThread {
                    findViewById<Button>(R.id.playlistAddSongsBtn).isEnabled = false
                    findViewById<Button>(R.id.playlistAddSongsBtn).setTextColor(getColor(R.color.textColorSecondary))
                }
            }
        }, selected)
        recyclerView.adapter = adapter
//        Thread {
//            var lastResult = false
//            while (true) {
//                if(lastResult != (MainFragment.playlist.mediaItems!!.toMutableList().size > 0)) {
//                    UIUpdated = false
//                }
//                lastResult = (MainFragment.playlist.mediaItems!!.toMutableList().size > 0)
//                if (MainFragment.playlist.mediaItems!!.toMutableList().size > 0) {
//                    if(!UIUpdated) {
//                        UIUpdated = true
//                        runOnUiThread {
//                            findViewById<Button>(R.id.playlistAddSongsBtn).isEnabled = true
//                            findViewById<Button>(R.id.playlistAddSongsBtn).setTextColor(getColor(R.color.primary))
//                        }
//                    }
//                } else {
//                    if(!UIUpdated) {
//                        UIUpdated = true
//                        runOnUiThread {
//                            findViewById<Button>(R.id.playlistAddSongsBtn).isEnabled = false
//                            findViewById<Button>(R.id.playlistAddSongsBtn).setTextColor(getColor(R.color.textColorSecondary))
//                        }
//                    }
//                }
//            }
//        }.start()
    }
    @OptIn(UnstableApi::class)
    private fun searchSongs() {
        val database = MainFragment.AppDatabase.getDatabase(this)
        val dao = database.trackDao()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.MIME_TYPE} IN (?, ?, ?, ?, ?, ?)"
        val selectionArgs = arrayOf("audio/mpeg", "audio/aac", "audio/flac", "audio/opus", "audio/mp4", "audio/x-m4a") // MIME type for MP3
        val audioCursor = this.contentResolver?.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            proj,
            selection,
            selectionArgs,
            null
        )

        CoroutineScope(Dispatchers.IO).launch {
            if (audioCursor != null) {
                val newTracks = mutableListOf<MainFragment.TrackEntity>()
                val mediaItems = mutableListOf<MediaItem>()

                while (audioCursor.moveToNext()) {
                    val uri =
                        audioCursor.getString(audioCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    val title =
                        audioCursor.getString(audioCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                    val artist =
                        audioCursor.getString(audioCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                    var mediaItem = MainActivity.getAudioMetadataFFmpeg(Uri.parse(uri))

                    mediaItems.add(mediaItem)
                    newTracks.add(
                        MainFragment.TrackEntity(
                            uri,
                            title,
                            artist,
                            mediaItem.mediaMetadata.artworkData
                        )
                    ) // No artwork initially
                }
                audioCursor.close()

                withContext(Dispatchers.Main) {
                    var i = 0
                    for (mediaItem in mediaItems) {
//                        if(items[i].localConfiguration?.uri != mediaItem.localConfiguration?.uri) {
//                            items.add(i, mediaItem)
//                            adapter.notifyItemInserted(i)
//                        }
                        i++
                    }
                    findViewById<ProgressBar>(R.id.loadingBar).visibility = ProgressBar.GONE
                }

            }
        }
    }
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_songs)

        downloadSongsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                selectedItems = mutableListOf()
                var resultItem = MainActivity.getAudioMetadataFFmpeg(Uri.parse(result?.data?.getStringExtra("result")))
                resultItem = resultItem.buildUpon().setMediaMetadata(resultItem.mediaMetadata.buildUpon().setIsPlayable(true).build()).build()
                selectedItems?.add(resultItem)
                MainFragment.playlist.mediaItems!!.add(resultItem)
                adapter.updateItems(MainFragment.playlist.mediaItems!!)
                val resultIntent = Intent()
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
        findViewById<ProgressBar>(R.id.loadingBar).visibility = ProgressBar.GONE // VISIBLE
        loadSongs()
//        Thread {
//            searchSongs()
//        }.start()
        findViewById<LinearLayout>(R.id.searchSongButton)?.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Call your function here when touch is released
//                activityLauncher.launch(Intent(requireContext(), CreatePlaylistActivity::class.java))
                var intent = Intent(this@AddSongsActivity, SearchSongOnlineActivity::class.java)
                downloadSongsLauncher.launch(intent)
                val anim = AnimationUtils.loadAnimation(this@AddSongsActivity, R.anim.release)
                v.startAnimation(anim)

                true // Return true to indicate the event was handled
            }
            else if(event.action == MotionEvent.ACTION_DOWN) {
                val anim = AnimationUtils.loadAnimation(this@AddSongsActivity, R.anim.press)
                v.startAnimation(anim)
                true
            }
            else if(event.action == MotionEvent.ACTION_CANCEL) {
                val anim = AnimationUtils.loadAnimation(this@AddSongsActivity, R.anim.release)
                v.startAnimation(anim)
                false
            }
            else {
                false // Return false to allow other touch events to be processed
            }
        }
        findViewById<Button>(R.id.playlistAddSongsBtn).setOnClickListener {
            if(it.isEnabled) {
                it.isEnabled = false
                (it as Button).setTextColor(getColor(R.color.textColorSecondary))
                selectedItems = selected
                val resultIntent = Intent()
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
        findViewById<Button>(R.id.playlistBackBtn).setOnClickListener {
            finish()
        }
        findViewById<Button>(R.id.filterButton).setOnClickListener {
            // TODO: Open filter dialog
        }

        var original = MainFragment.playlist.mediaItems!!.toMutableList()
        var items = mutableListOf<MediaItem>()
        findViewById<EditText>(R.id.playlistAddSongSearch).addTextChangedListener(object :
            TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // Filter the track list based on the search query
                val query = s.toString().lowercase()
                if(query.isEmpty()) {
                    adapter.updateItems(MainFragment.mediaItems)
                }
                else {
                    items.clear()
                    for (i in 0 until original.size) {
                        val mediaItem = original[i]
                        val title = mediaItem.mediaMetadata.title.toString().lowercase()
                        val artist = mediaItem.mediaMetadata.artist.toString().lowercase()
                        if (title.contains(query) || artist.contains(query)) {
                            items.add(mediaItem)
                        }
                    }
                    adapter.updateItems(items)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Not needed
            }
        })

//        setResult(RESULT_OK, resultIntent)
//        finish()
    }
}