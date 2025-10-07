package com.moqayed.zam

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ItemTouchHelper
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import com.moqayed.zam.MainPagerAdapter.Companion.playlistFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ViewPlaylistFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ViewPlaylistFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var v: View? = null
    private var draggingView: View? = null
    private var lastY = 0f
    lateinit var adapter: TrackRowAdapter
    var itemTouchHelper: ItemTouchHelper? = null
    var editMode: Boolean = false
    @Entity(tableName = "tracks") // Table name is fixed per database
    data class TrackEntity(
        @PrimaryKey val uri: String, // Unique identifier
        val title: String,
        val artist: String,
        val artwork: ByteArray?, // Store the artwork as ByteArray
        val enabled: Boolean, // Store the artwork as ByteArray
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

    @Database(entities = [TrackEntity::class], version = 2, exportSchema = false)
    abstract class AppDatabase : RoomDatabase() {
        abstract fun trackDao(): TrackDao

        companion object {
            @Volatile private var instances: MutableMap<String, AppDatabase> = mutableMapOf()
            val MIGRATION_1_2 = object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Add the new column with a default value
                    database.execSQL(
                        "ALTER TABLE tracks ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1"
                    )
                }
            }
            fun getDatabase(context: Context, dbName: String): AppDatabase {
                return instances[dbName] ?: synchronized(this) {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        dbName // Each database gets a unique name
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                    instances[dbName] = instance
                    instance
                }
            }
            fun deleteDatabase(context: Context, dbName: String) {
                context.deleteDatabase(dbName)
                instances.remove(dbName)
            }
        }
    }

    fun parseM3U(filePath: String): Array<Pair<Uri, Boolean>> {
        var entries = arrayOf<Pair<Uri, Boolean>>()
        val lines = File(filePath).readLines()

        for (line in lines) {
            val trimmed = line.trim().replace("../", "/storage/emulated/0/")

            when {
                trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                    // This is a media file URL or path
                    if(!trimmed.startsWith("-")) {
                        entries += Pair(Uri.parse(trimmed), true)
                    }
                    else {
                        entries += Pair(Uri.parse(trimmed.substring(1)), false)
                    }
                }

            }
        }
        return entries
    }
    fun saveM3U(mediaItems: List<MediaItem>, filePath: String) {
        var m3uFile = File(filePath)
        var contents = "#EXTM3U\n"
        for (item in mediaItems) {
            val uri = item.localConfiguration?.uri ?: continue // Skip if no URI
            contents += (if(item.mediaMetadata.isPlayable == true) "" else "-") + (uri.toString() + "\n")
        }
        m3uFile.writeText(contents)
    }
    var callback: (mediaitems: List<MediaItem>) -> Unit = {}
    lateinit var selectSongsLauncher: ActivityResultLauncher<Intent>
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        selectSongsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                var mediaitems = AddSongsActivity.selectedItems
                if (mediaitems != null) {
                    callback(mediaitems)
                }
            }
        }
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }
    @OptIn(UnstableApi::class)
    private fun searchTracks(templist: Array<Pair<Uri, Boolean>>): Pair<List<TrackEntity>, MutableList<MediaItem>> {
        var songList: Array<MediaItem> = arrayOf()
        val newTracks = mutableListOf<TrackEntity>()
        for (uri in templist) {
            var mediaItem = MainActivity.getAudioMetadataFFmpeg(uri.first)
            if(!uri.second) {
                mediaItem = mediaItem.buildUpon().setMediaMetadata(mediaItem.mediaMetadata.buildUpon().setIsPlayable(false).build()).build()
            }
            else {
                mediaItem = mediaItem.buildUpon().setMediaMetadata(mediaItem.mediaMetadata.buildUpon().setIsPlayable(true).build()).build()
            }
//            Log.i("searchTracks", mediaItem.mediaMetadata.isPlayable.toString())
            newTracks += TrackEntity(uri.first.toString(), mediaItem.mediaMetadata.title.toString(), mediaItem.mediaMetadata.artist.toString(), mediaItem.mediaMetadata.artworkData, mediaItem.mediaMetadata.isPlayable?:true)
            songList += mediaItem
        }
        playlist.mediaItems = songList.toMutableList()
        return Pair<List<TrackEntity>, MutableList<MediaItem>>(newTracks, songList.toMutableList())
    }
    private fun updateUI() {
        var count = playlist.mediaItems?.size
        v?.findViewById<TextView>(R.id.playlistSongCount)?.text =
            count.toString() + " Song" + if (count != 1) {
                "s"
            } else {
                ""
            }
    }
    var selectedItems = mutableListOf<MediaItem>()
    fun updateToEditMode() {
        var editModeBar = v?.findViewById<LinearLayout>(R.id.editModeBar)
        if(editMode) {
            MainActivity.systemHidePlayerUI(requireContext())
            val anim = AnimationUtils.loadAnimation(context, R.anim.slide_in_down)
            editModeBar?.startAnimation(anim)
            itemTouchHelper?.attachToRecyclerView(v?.findViewById(R.id.tracklistview))
        }
        else {
            MainActivity.systemShowPlayerUI(requireContext())
            val anim = AnimationUtils.loadAnimation(context, R.anim.slide_out_down)
            editModeBar?.startAnimation(anim)
            itemTouchHelper?.attachToRecyclerView(null)
        }
    }
    @OptIn(UnstableApi::class)
    fun selectSongs(onSelected: (items: List<MediaItem>) -> Unit) {
        callback = {
            onSelected(it)
        }
        var intent = Intent(requireContext(), AddSongsActivity::class.java)
        selectSongsLauncher.launch(intent)
    }
    private fun getNewIndex(y: Float): Int {
        var container = v?.findViewById<LinearLayout>(R.id.tracklistview)
        for (i in 0 until container!!.childCount) {
            val child = container.getChildAt(i)
            if (y < child.y + child.height / 2f) {
                return i
            }
        }
        return container.childCount
    }

    @OptIn(UnstableApi::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        v = inflater.inflate(R.layout.fragment_view_playlist, container, false)

        v?.findViewById<Button>(R.id.playlistBackBtn)?.setOnClickListener {
            (context as MainActivity).supportFragmentManager.popBackStack()
        }
        v?.findViewById<Button>(R.id.editModeBarRemoveFromPlaylistBtn)?.setOnClickListener {
            var templist = playlist.mediaItems?.toMutableList()
            if(templist != null) {
                templist.removeAll(selectedItems)
                playlist.mediaItems = templist
                adapter.updateItems(playlist.mediaItems!!)
//                templist.removeAll(selectedItems)
                param2?.let { it1 -> saveM3U(playlist.mediaItems!!, it1) }
                CoroutineScope(Dispatchers.IO).launch {
                    val database = AppDatabase.getDatabase(requireContext(), param1!!)
                    val dao = database.trackDao()
                    dao.clearTracks()
                    dao.insertTracks(searchTracks(playlist.convertMediaItemsToSongUris()!!).first)
                }
                if(editMode == false) {
                    editMode = false
                    updateToEditMode()
                }
                MainActivity.overrideBackButton = false
                MainActivity.overrideBackButtonAction = {}
                adapter.disableSelection()
            }
            editMode = false
            updateToEditMode()
            updateUI()
        }
        v?.findViewById<Button>(R.id.editModeBarSkipFromPlaylistBtn)?.setOnClickListener {
            var templist = playlist.mediaItems?.toMutableList()
            if(templist != null) {
                var i = 0
                for(item in templist) {
                    if(item in selectedItems) {
                        if (item.mediaMetadata.isPlayable != null) {
                            templist[i] = item.buildUpon().setMediaMetadata(
                                item.mediaMetadata.buildUpon()
                                    .setIsPlayable(!item.mediaMetadata.isPlayable!!).build()
                            ).build()
                        } else {
                            templist[i] = item.buildUpon().setMediaMetadata(
                                item.mediaMetadata.buildUpon().setIsPlayable(false).build()
                            ).build()
                        }
                    }
                    i++
                }
                playlist.mediaItems = templist
                adapter.updateItems(playlist.mediaItems!!)
//                templist.removeAll(selectedItems)
                param2?.let { it1 -> saveM3U(playlist.mediaItems!!, it1) }
                CoroutineScope(Dispatchers.IO).launch {
                    val database = AppDatabase.getDatabase(requireContext(), param1!!)
                    val dao = database.trackDao()
                    dao.clearTracks()
                    dao.insertTracks(searchTracks(playlist.convertMediaItemsToSongUris()!!).first)
                }
//                if(editMode == false) {
//                    editMode = false
//                    updateToEditMode()
//                }
//                MainActivity.overrideBackButton = false
//                MainActivity.overrideBackButtonAction = {}
//                adapter.disableSelection()
            }
//            editMode = false
//            updateToEditMode()
//            updateUI()
        }
        v?.findViewById<Button>(R.id.playlistMoreOptions)?.setOnClickListener {
            val popup = PopupMenu(requireContext(), it)
            val inflater: MenuInflater = popup.menuInflater
            inflater.inflate(R.menu.playlist_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when(menuItem.itemId){
                    R.id.add-> {
                        selectSongs {items ->
                            var temp = playlist.mediaItems!!.toList()
                            var combined = items + temp
                            playlist.mediaItems = combined.toMutableList()
                            param2?.let { it1 -> saveM3U(playlist.mediaItems!!, it1) }
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = AppDatabase.getDatabase(requireContext(), param1!!)
                                val dao = database.trackDao()
                                dao.clearTracks()
                                dao.insertTracks(searchTracks(playlist.convertMediaItemsToSongUris()!!).first)
                            }
                            adapter.updateItems(playlist.mediaItems!!)
                            updateUI()
                        }
                    }
                    R.id.share -> {
                        val fileUri = Uri.parse(param2) // Assuming param2 is the file URI string
                        val file = File(fileUri.path!!) // Create a File object from the URI path

                        // Create a content URI for the file using MediaStore
                        // This makes the file accessible to other apps via the MediaStore
                        val contentUri = context?.contentResolver?.insert(
                            MediaStore.Files.getContentUri("external"),
                            android.content.ContentValues().apply {
                                put(MediaStore.Files.FileColumns.DISPLAY_NAME, file.name)
                                put(MediaStore.Files.FileColumns.MIME_TYPE, "audio/x-mpegurl") // M3U MIME type
                                put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/ZamPlaylists") // Optional: Save in a specific folder
                            }
                        )
                        val shareIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            type = "audio/x-mpegurl"
                        }
                        startActivity(Intent.createChooser(shareIntent, null))

                    }
                    R.id.delete-> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete Playlist?")
                            .setMessage("Are you sure you want to delete the playlist \"${param1}\"? This action cannot be undone.")
                            .setNegativeButton("Cancel") { dialog, which ->
                                // Respond to negative button press
                            }
                            .setPositiveButton("Delete") { dialog, which ->
                                // Respond to positive button press
                                AppDatabase.deleteDatabase(requireContext(), param1!!)
                                var playlistdir = context?.getDir("Playlists", Context.MODE_PRIVATE)
                                File(playlistdir?.path, param1 + ".m3u").delete()
                                (context as MainActivity).supportFragmentManager.popBackStack()
                                playlistFragment.searchPlaylists()
                            }
                            .show()

                    }
                }
                true
            }
            popup.show()

        }
        v?.findViewById<Button>(R.id.playlistAddSongsBtn)?.setOnClickListener {
            selectSongs {items ->
                var temp = playlist.mediaItems!!.toList()
                var combined = items + temp
                playlist.mediaItems = combined.toMutableList()
                param2?.let { it1 -> saveM3U(playlist.mediaItems!!, it1) }
                CoroutineScope(Dispatchers.IO).launch {
                    val database = AppDatabase.getDatabase(requireContext(), param1!!)
                    val dao = database.trackDao()
                    dao.clearTracks()
                    dao.insertTracks(searchTracks(playlist.convertMediaItemsToSongUris()!!).first)
                }
                adapter.updateItems(playlist.mediaItems!!)
                updateUI()
            }
        }


        var editModeBar = v?.findViewById<LinearLayout>(R.id.editModeBar)
        var templist: Array<Pair<Uri, Boolean>> = arrayOf()
        var songList: List<MediaItem> = listOf()
        var context = requireContext()
        val database = AppDatabase.getDatabase(requireContext(), param1!!)
        val dao = database.trackDao()
        val anim = AnimationUtils.loadAnimation(context, R.anim.slide_out_down)
        anim.duration = 0
        editModeBar?.startAnimation(anim)
        CoroutineScope(Dispatchers.IO).launch {
            param1?.let { playlist.setName(it) }
            param2?.let { templist = parseM3U(it) }
            var temparr: Array<Uri> = arrayOf()
            for(item in templist) {
                temparr += item.first
            }
            playlist.setSongUris(temparr)
            var oldTracks = dao.getAllTracks()

            (context as Activity).runOnUiThread {
                var coverimage = v?.findViewById<ImageView>(R.id.playlistCoverImageView)
                coverimage?.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius =
                        MainActivity.dpToPx(context, 15).toFloat() // 15dp rounded corners
                }

                coverimage?.scaleType = ImageView.ScaleType.CENTER_CROP
                coverimage?.clipToOutline = true

                coverimage = v?.findViewById<ImageView>(R.id.playlistCoverImageViewHeader)
                coverimage?.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius =
                        MainActivity.dpToPx(context, 15).toFloat() // 15dp rounded corners
                }

                coverimage?.scaleType = ImageView.ScaleType.CENTER_CROP
                coverimage?.clipToOutline = true

//        coverimage?.setImageBitmap()
                v?.findViewById<TextView>(R.id.playlistName)?.text = playlist.getName()
                var count = playlist.getSongUris()?.size
                v?.findViewById<TextView>(R.id.playlistSongCount)?.text =
                    count.toString() + " Song" + if (count != 1) {
                        "s"
                    } else {
                        ""
                    }
                v?.findViewById<TextView>(R.id.playlistNameHeader)?.text = playlist.getName()
                v?.findViewById<TextView>(R.id.playlistSongCountHeader)?.text =
                    count.toString() + " Song" + if (count != 1) {
                        "s"
                    } else {
                        ""
                    }
            }
            for (track in oldTracks) {
                val mediaItem = MediaItem.Builder()
                    .setUri(track.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setArtworkData(track.artwork)
                            .setIsPlayable(track.enabled)
                            .build()
                    )
                    .build()
                songList += mediaItem

            }
            withContext(Dispatchers.Main) {
                playlist.mediaItems = songList.toMutableList()
//                    var createdtrack = MainActivity.createTrack(
//                        try {
//                            requireContext()
//                        } catch (e: Exception) {
//                            return@withContext
//                        },
//                        tracklistview!!,
//                        (mediaItem.mediaMetadata.title ?: "Unknown Title").toString(),
//                        (mediaItem.mediaMetadata.artist ?: "Unknown Artist").toString(),
//                        mediaItem, // Use the original media item
//                        mediaItem.mediaMetadata.artworkData?.size?.let {
//                            BitmapFactory.decodeByteArray(
//                                mediaItem.mediaMetadata.artworkData, 0,
//                                it
//                            )
//                        },
//                        Queue
//                    )
//                    createdtrack.isLongClickable = true
//                    createdtrack.setOnLongClickListener {
//                        if (!editMode) {
//                            editMode = true
//                            MainActivity.overrideBackButtonAction = {
//                                editMode = false
//                                updateToEditMode()
//                                MainActivity.overrideBackButton = false
//                            }
//                            MainActivity.overrideBackButton = true
//                            Log.d("Track", "Long clicked!")
//                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
//                            updateToEditMode(createdtrack)
//                        } else {
//                            Log.d("Track", "Reorder")
//                            //TODO:  Reorder support
//                            it.performHapticFeedback(HapticFeedbackConstants.DRAG_START)
//                        }
//                        true
//                    }
                val recyclerView: RecyclerView = v!!.findViewById(R.id.tracklistview)
                recyclerView.layoutManager = LinearLayoutManager(requireContext())
                adapter = TrackRowAdapter(playlist.mediaItems!!, { item ->
                    QueueManager.setQueue(context, playlist)
                    MainActivity.playTrack(requireContext(), playlist.mediaItems!!.indexOf(item))
                }, { item ->
                    editMode = true
                    updateToEditMode()
                    MainActivity.overrideBackButton = true
                    MainActivity.overrideBackButtonAction = {
                        MainActivity.overrideBackButton = false
                        adapter.disableSelection()
                        editMode = false
                        updateToEditMode()
                    }
                    true
                }, selectedItems)
                recyclerView.adapter = adapter

            }

            var newtracks = searchTracks(templist)
            if (playlist.mediaItems != null || oldTracks != newtracks.first) {
                dao.clearTracks()
                dao.insertTracks(newtracks.first)
                dao.getAllTracks()
                withContext(Dispatchers.Main) {
                    playlist.mediaItems = newtracks.second
                    adapter.updateItems(playlist.mediaItems!!)
                }
            }
        }
        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if(editMode) {
                    val fromPos = viewHolder.adapterPosition
                    val toPos = target.adapterPosition
                    adapter.moveItem(fromPos, toPos, { item ->
                        var items = playlist.mediaItems!!.toMutableList()
                        items.removeAt(fromPos)
                        items.add(toPos, item)
                        playlist.mediaItems = items
                    })
                    return true
                }
                else {
                    return false
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                param2?.let { it1 -> saveM3U(playlist.mediaItems!!, it1) }
                var items = playlist.mediaItems!!.toMutableList()
                var newtracks = listOf<TrackEntity>()
                for(mediaItem in items) {
                    newtracks += TrackEntity(mediaItem.localConfiguration?.uri.toString(), mediaItem.mediaMetadata.title.toString(), mediaItem.mediaMetadata.artist.toString(), mediaItem.mediaMetadata.artworkData, mediaItem.mediaMetadata.isPlayable?:true)
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val database = AppDatabase.getDatabase(requireContext(), param1!!)
                    val dao = database.trackDao()
                    dao.clearTracks()
                    dao.insertTracks(newtracks)
                    dao.getAllTracks()
                }
                super.clearView(recyclerView, viewHolder)
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used
            }
        })
        itemTouchHelper?.attachToRecyclerView(null)
        v?.findViewById<FloatingActionButton>(R.id.playlistStartPlayingBtn)?.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Call your function here when touch is released
//                setNewQueue(playlist)
                // TODO: Change queue set method
                MainActivity.playTrack(requireContext(), 0)
//                MainActivity.storeLastPlayed(requireContext(), Queue, Queue.mediaItems!![0])
                val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                v.startAnimation(anim)

                true // Return true to indicate the event was handled
            }
            else if(event.action == MotionEvent.ACTION_DOWN) {
                val anim = AnimationUtils.loadAnimation(context, R.anim.press)
                v.startAnimation(anim)
                true
            }
            else if(event.action == MotionEvent.ACTION_CANCEL) {
                val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                v.startAnimation(anim)
                false
            }
            else {
                false // Return false to allow other touch events to be processed
            }
        }
        v?.findViewById<FloatingActionButton>(R.id.playlistStartPlayingShuffledBtn)?.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Call your function here when touch is released
//                QueueManager.setQueue(context, playlist)
                // TODO: SET QUEUE CHANGEG
//                val i = Random.nextInt( (Queue.mediaItems?.size?:2)-2)
//                MainActivity.playTrack(requireContext(), i)
//                MainActivity.storeLastPlayed(requireContext(), Queue, Queue.mediaItems!![i])
                val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                v.startAnimation(anim)

                true // Return true to indicate the event was handled
            }
            else if(event.action == MotionEvent.ACTION_DOWN) {
                val anim = AnimationUtils.loadAnimation(context, R.anim.press)
                v.startAnimation(anim)
                true
            }
            else if(event.action == MotionEvent.ACTION_CANCEL) {
                val anim = AnimationUtils.loadAnimation(context, R.anim.release)
                v.startAnimation(anim)
                false
            }
            else {
                false // Return false to allow other touch events to be processed
            }
        }
//        var scrollView = v?.findViewById<NestedScrollView>(R.id.playlistScrollView)
//        scrollView?.setOnScrollChangeListener { el, scrollX, scrollY, oldScrollX, oldScrollY ->
//            if(scrollView.scrollY > 750) {
//                v?.findViewById<LinearLayout>(R.id.playlistInfoHeader)?.visibility = LinearLayout.VISIBLE
//                v?.findViewById<ImageView>(R.id.playlistCoverImageViewHeader)?.visibility = LinearLayout.VISIBLE
//            }
//            else {
//                v?.findViewById<LinearLayout>(R.id.playlistInfoHeader)?.visibility = LinearLayout.GONE
//                v?.findViewById<ImageView>(R.id.playlistCoverImageViewHeader)?.visibility = LinearLayout.GONE
//            }
//            if(scrollView.scrollY > 749 && scrollView.scrollY < 951) {
//                v?.findViewById<LinearLayout>(R.id.playlistInfoHeader)?.alpha = (map(scrollView.scrollY,  750, 950, 0, 100).toFloat()/100)
//                v?.findViewById<LinearLayout>(R.id.playlistInfoHeader)?.translationY = map(scrollView.scrollY,  750, 950, 10, 0).toFloat()
//                v?.findViewById<ImageView>(R.id.playlistCoverImageViewHeader)?.alpha = (map(scrollView.scrollY,  750, 950, 0, 100).toFloat()/100)
//                v?.findViewById<ImageView>(R.id.playlistCoverImageViewHeader)?.translationY = map(scrollView.scrollY,  750, 950, 10, 0).toFloat()
//            }
//        }
        v?.findViewById<TextView>(R.id.playlistNameHeader)?.setSelected(true)
        return v
    }
    private fun map(x: Int, inMin: Int, inMax: Int, outMin: Int, outMax: Int): Int {
        return (x - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
    }

    override fun onDestroyView() {
        if(editMode)
            MainActivity.systemShowPlayerUI(requireContext())
        super.onDestroyView()
    }
    companion object {
        var playlist: Playlist = Playlist()
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ViewPlaylistFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ViewPlaylistFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}