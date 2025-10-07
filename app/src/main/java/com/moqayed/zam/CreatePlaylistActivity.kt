package com.moqayed.zam

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class CreatePlaylistActivity : AppCompatActivity() {
    var playlist: Playlist = Playlist()
    var callback: (mediaitems: MutableList<MediaItem>) -> Unit = {}
    lateinit var selectSongsLauncher: ActivityResultLauncher<Intent>
    @OptIn(UnstableApi::class)
    fun selectSongs(onSelected: (items: MutableList<MediaItem>) -> Unit) {
        callback = {
            onSelected(it)
        }
        var intent = Intent(this, AddSongsActivity::class.java)
        selectSongsLauncher.launch(intent)
    }
    fun updateUI() {
        var count = playlist.mediaItems?.size?:0
        findViewById<TextView>(R.id.playlistSongCount)?.text = count.toString() + " Song" + if(count != 1) {"s"} else{ ""}
        var tracklistview = findViewById<LinearLayout>(R.id.tracklistview)
        tracklistview.removeAllViews()
        if(playlist.mediaItems != null) {
            for (item in playlist.mediaItems!!) {
                MainActivity.createViewTrack(
                    this, tracklistview,
                    (item.mediaMetadata.title?:"Unknown Title").toString(),
                    (item.mediaMetadata.artist?:"Unknown Artist").toString(),
                    item,
                    if(item.mediaMetadata.artworkData != null) { BitmapFactory.decodeByteArray(item.mediaMetadata.artworkData, 0, item.mediaMetadata.artworkData!!.size)} else {null}
                )
            }
        }
    }
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_playlist)
        playlist.mediaItems = mutableListOf()
        val playlistName = findViewById<EditText>(R.id.playlistName)
        var playlistCreateBtn = findViewById<Button>(R.id.playlistCreateBtn)
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

        findViewById<FloatingActionButton>(R.id.playlistAddSongBtn).setOnClickListener {
            selectSongs { items ->
                playlist.mediaItems = items
                updateUI()
            }
        }
        playlistName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int,
                                           count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence, start: Int,
                                       before: Int, count: Int) {
                if(playlistName.text.isEmpty()) {
                    playlistCreateBtn.isEnabled = false
                    playlistCreateBtn.setTextColor(getColor(R.color.textColorSecondary))
                }
                else {
                    playlistCreateBtn.isEnabled = true
                    playlistCreateBtn.setTextColor(getColor(R.color.primary))
                }
            }
        })
        playlistCreateBtn.setOnClickListener {
            if(playlistCreateBtn.isEnabled) {
                var playlistdir = this.getDir("Playlists", Context.MODE_PRIVATE)
                var m3uFile = File(playlistdir.path, playlistName.text.toString() + ".m3u")
                m3uFile.createNewFile()
                var contents = "#EXTM3U\n"
                for (item in playlist.mediaItems!!) {
                    val uri = item.localConfiguration?.uri ?: continue // Skip if no URI
                    contents += (uri.toString() + "\n")
                }
                m3uFile.writeText(contents)
                finish()
            }
        }
        findViewById<Button>(R.id.playlistBackBtn).setOnClickListener {
            finish()
        }
    }
}