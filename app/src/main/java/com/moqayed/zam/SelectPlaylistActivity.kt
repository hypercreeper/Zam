package com.moqayed.zam

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import com.moqayed.zam.MainActivity.Companion.playTrack
import com.moqayed.zam.MainActivity.Companion.setPlayerLoadingUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SelectPlaylistActivity : AppCompatActivity() {
    companion object {
        var selectPlaylistUri: String? = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_playlist)
        val playlistview = findViewById<LinearLayout>(R.id.tracklistview)
        runOnUiThread {
            playlistview?.removeAllViews()
        }
        var playlists = getDir("Playlists", Context.MODE_PRIVATE)?.listFiles()
        if(playlists != null) {
            for(playlistFile in playlists) {
                runOnUiThread {
                    if (playlistview != null) {
                        var playlist = MainActivity.createTrack(
                            this,
                            playlistview,
                            playlistFile.name.replace(".m3u",""),
                            "",
                            MediaItem.Builder().build(),
                            null,
                            Playlist(),
                        )
                        playlist.setOnTouchListener (object: View.OnTouchListener {
                            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                                if (event?.action == MotionEvent.ACTION_UP) {
                                    // Handle normal click here
                                    println("Short Click Detected!")
                                    val anim = AnimationUtils.loadAnimation(this@SelectPlaylistActivity, R.anim.release)
                                    playlist.startAnimation(anim)

                                    selectPlaylistUri = playlistFile.path
                                    val resultIntent = Intent()
                                    resultIntent.putExtra("playlistPath", selectPlaylistUri)
                                    setResult(RESULT_OK, resultIntent)
                                    finish()

                                    return true // Return true to indicate the event was handled
                                } else if (event?.action == MotionEvent.ACTION_DOWN) {
                                    val anim = AnimationUtils.loadAnimation(this@SelectPlaylistActivity, R.anim.press)
                                    playlist.startAnimation(anim)
                                    return true
                                } else if (event?.action == MotionEvent.ACTION_CANCEL) {
                                    val anim = AnimationUtils.loadAnimation(this@SelectPlaylistActivity, R.anim.release)
                                    playlist.startAnimation(anim)
                                    return false
                                } else {
                                    return false // Return false to allow other touch events to be processed
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}