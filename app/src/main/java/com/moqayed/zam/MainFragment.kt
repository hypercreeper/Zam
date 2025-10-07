package com.moqayed.zam

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Space
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.*
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.moqayed.zam.MainActivity.Companion.layerDrawableToBitmap
import com.moqayed.zam.MainActivity.Companion.setPlayerUI
import com.moqayed.zam.MainPagerAdapter.Companion.playlistFragment
import com.moqayed.zam.ViewPlaylistFragment.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [MainFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MainFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var v: View? = null
    var createImportLauncher: ActivityResultLauncher<Intent>? = null
    var createSelectPlaylistActivity: ActivityResultLauncher<Intent>? = null
    var createFileLauncher: ActivityResultLauncher<Intent>? = null
    lateinit var playlistfile: File
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                uri?.let {
                    // Handle the file URI (e.g., write data to the file)
                    requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(playlistfile.readBytes())
                    }
                    Toast.makeText(requireContext(), "Playlist Exported Successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        createSelectPlaylistActivity = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.i("SelectPlaylist", "Received")
                playlistfile = File(result.data?.getStringExtra("playlistPath"))
                Log.i("SelectPlaylist", "File Handle Obtained")
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "audio/x-mpegurl" // Specify the file type (e.g., text/plain, application/pdf, etc.)
                    putExtra(Intent.EXTRA_TITLE, playlistfile.name) // Default file name
                }
                createFileLauncher?.launch(intent)

            }
        }
        createImportLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uri: Uri? = result.data?.data
            uri?.let {
                val playlistDir = requireContext().getDir("Playlists", Context.MODE_PRIVATE)

                // Keep original file name if possible
                val fileName = getFileName(requireContext(), it) ?: "imported_playlist.m3u"
                val destFile = File(playlistDir, fileName)

                try {
                    requireContext().contentResolver.openInputStream(it)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    println("Copied to: ${destFile.absolutePath}")
                    Toast.makeText(requireContext(), "Playlist Imported Successfully!", Toast.LENGTH_SHORT).show()
                    playlistFragment.searchPlaylists()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }


        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    operator fun JSONArray.iterator(): Iterator<JSONObject>
            = (0 until length()).asSequence().map { get(it) as JSONObject }.iterator()

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

    @OptIn(UnstableApi::class)
    private fun searchSongs() {
        val database = AppDatabase.getDatabase(requireContext())
        val dao = database.trackDao()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.MIME_TYPE} IN (?, ?, ?, ?, ?, ?)"
        val selectionArgs = arrayOf("audio/mpeg", "audio/aac", "audio/flac", "audio/opus", "audio/mp4", "audio/x-m4a") // MIME type for MP3
        val audioCursor = context?.contentResolver?.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            proj,
            selection,
            selectionArgs,
            null
        )

        CoroutineScope(Dispatchers.IO).launch {
            if (audioCursor != null) {
                val newTracks = mutableListOf<TrackEntity>()
                updatedMediaItems = mutableListOf<MediaItem>()
                var i = 0
                while (audioCursor.moveToNext()) {
                    val uri = audioCursor.getString(audioCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    var title = audioCursor.getString(audioCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                    var artist = audioCursor.getString(audioCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                    var mediaItem = MainActivity.getAudioMetadataFFmpeg(Uri.parse(uri))
                    updatedMediaItems.add(mediaItem)
//                    if(mediaItem.mediaMetadata.artworkData == null)
//                        Log.e("SearchSongs", "No artwork")
                    if(uri == null)
                        Log.e("SearchSongs", "No uri")
                    if(title == null) {
                        title = (mediaItem.mediaMetadata.title?:"Unknown Title").toString()
                    }
                    if(artist == null) {
                        artist = (mediaItem.mediaMetadata.artist?:"Unknown Artist").toString()
                    }
                    if(newTracks == null)
                        Log.e("SearchSongs", "No newTracks")

                    newTracks.add(TrackEntity(uri, title, artist, mediaItem.mediaMetadata.artworkData)) // No artwork initially
                    i++
                }
                audioCursor.close()

                // Compare existing tracks in DB
                val storedTracks = dao.getAllTracks()
                if (storedTracks.size != newTracks.size) {
                    dao.clearTracks()
                    dao.insertTracks(newTracks)
                }
                mediaItems = updatedMediaItems
                Log.i("SearchTracks", "Search completed with $i tracks, mediaItems contains ${mediaItems.size} items")
                mediaItemsVarUpdated = true
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun loadSongs() {
        mediaItemsVarUpdated = false
//        val tracklistview = v?.findViewById<LinearLayout>(R.id.tracklistview) ?: return
        val database = AppDatabase.getDatabase(requireContext())
        val dao = database.trackDao()

        CoroutineScope(Dispatchers.IO).launch {
            val tracks = dao.getAllTracks()
            mediaItems = mutableListOf<MediaItem>()

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
                mediaItems.add(mediaItem)
            }
            playlist.mediaItems = mediaItems

//            try {
//                val space = Space(requireContext()).apply {
//                    layoutParams = LinearLayout.LayoutParams(
//                        LinearLayout.LayoutParams.MATCH_PARENT,
//                        MainActivity.dpToPx(context, 110)
//                    )
//                }
//                withContext(Dispatchers.Main) {
//                    tracklistview.addView(space)
//                }
//            }
//            catch (e:Exception){
//
//            }

        }
    }

    // Helper to get the original name
    fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }
    private var FILE_PICKER_REQUEST_CODE = 69
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment


        v = inflater.inflate(R.layout.fragment_main, container, false)
        if(v != null) {
            ViewCompat.setOnApplyWindowInsetsListener(v!!.findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
                insets
            }
        }


        v?.findViewById<Button>(R.id.appOptions)?.setOnClickListener {
            val popup = PopupMenu(requireContext(), it)
            val inflater: MenuInflater = popup.menuInflater
            inflater.inflate(R.menu.app_options_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when(menuItem.itemId){
                    R.id.importPlaylist-> {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "audio/x-mpegurl" // Set MIME type (e.g., "image/*" for images, "application/pdf" for PDFs)
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        createImportLauncher?.launch(Intent.createChooser(intent, "Select Playlist File"))
                    }
                    R.id.exportPlaylist -> {

                        var intent2 = Intent(requireContext(), SelectPlaylistActivity::class.java)
                        createSelectPlaylistActivity?.launch(intent2)

                    }
                    R.id.settings -> {

                    }
                }
                true
            }
            popup.show()

        }
//        loadSongsTemp()
        loadSongs()
        Thread {
            searchSongs()
        }.start()
        val tabLayout = v!!.findViewById<TabLayout>(R.id.mainTabView)
        val viewPager = v!!.findViewById<ViewPager2>(R.id.viewPager)

        val adapter = MainPagerAdapter(requireActivity())
        viewPager?.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Playlists"
                1 -> "Tracks"
                else -> "Tracks"
            }
        }.attach()



        return v
    }
    companion object {
        var playlist: Playlist = Playlist()
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MainFragment.
         */
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MainFragment.
         */

        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MainFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
        var mediaItems: MutableList<MediaItem> = mutableListOf()
        var updatedMediaItems: MutableList<MediaItem> = mutableListOf()
        var mediaItemsVarUpdated = false
    }
}