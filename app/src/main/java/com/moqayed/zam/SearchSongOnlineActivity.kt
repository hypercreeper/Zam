package com.moqayed.zam

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream


class SearchSongOnlineActivity : AppCompatActivity() {
    companion object {
        data class Result(
            var title: String,
            var author: String,
            var ID: String,
            var Image: Bitmap?
        )
    }
    lateinit var adapter: SearchResultAdapter
    data class YouTubeSearchResponse(
        val items: List<YouTubeVideoItem>
    )

    data class YouTubeVideoItem(
        val id: VideoId,
        val snippet: Snippet
    )

    data class VideoId(
        val videoId: String
    )

    data class Snippet(
        val title: String,
        val description: String,
        val channelTitle: String,
        val thumbnails: Thumbnails
    )

    data class Thumbnails(
        @SerializedName("default") val default: Thumbnail
    )

    data class Thumbnail(
        val url: String
    )
    var searchResults = mutableListOf<Result>()
    fun downloadThumbnail(url: String): Bitmap? {
        return try {
            val connection = java.net.URL(url).openConnection()
            connection.connect()
            val input: InputStream = connection.getInputStream()
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun searchYouTube(query: String, apiKey: String, onResults: (List<Result>) -> Unit) {
        val url = "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet" +
                "&q=${query.replace(" ", "%20")}" +
                "&type=video" +
                "&maxResults=20" +
                "&key=$apiKey"

        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    val apiResponse = Gson().fromJson(json, YouTubeSearchResponse::class.java)

                    val results = apiResponse.items.map { item ->
                        val bitmap = downloadThumbnail(item.snippet.thumbnails.default.url)
                        Result(
                            title = item.snippet.title,
                            author = item.snippet.channelTitle,
                            ID = item.id.videoId,
                            Image = bitmap
                        )
                    }
                    onResults(results)

                } else {
                    Log.e("API ERROR","API Error: ${response.code}")
                    Log.e("API ERROR","API Error: ${response.body}")
                    Log.e("API ERROR","API Error: ${response.message}")
                    runOnUiThread {
                        findViewById<ProgressBar>(R.id.loadingBar).visibility = ProgressBar.GONE
                        MaterialAlertDialogBuilder(this@SearchSongOnlineActivity)
                            .setTitle("Error")
                            .setMessage("API Error: ${response.code}\n${response.message}")
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }

                }
            } catch (e: Exception) {
                Log.e("YT Search", "Request failed: ${e.message}")
                runOnUiThread {
                    findViewById<ProgressBar>(R.id.loadingBar).visibility = ProgressBar.GONE
                    MaterialAlertDialogBuilder(this@SearchSongOnlineActivity)
                        .setTitle("Error")
                        .setMessage("Request failed: ${e.message}")
                        .setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }.start()
    }

    fun downloadMusic(uri: String, saveDir: String): String? {
        try {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this);
                YoutubeDL.updateYoutubeDL(this)
            } catch (e: YoutubeDLException) {
                Log.e("YTDLP", "failed to initialize youtubedl-android", e)
            }

            val request = YoutubeDLRequest(uri)
            request.addOption("-o", saveDir + "/%(title)s.%(ext)s")
            request.addOption("-x")
            request.addOption("--audio-format", "mp3")
            request.addOption("--embed-thumbnail")
            request.addOption("--add-metadata")
            request.addOption("--metadata-from-title", "\"%(artist)s - %(title)s\"")
//            request.addOption("--print", "after_move:filepath")
            var resp = YoutubeDL.getInstance().execute(request, null, { progress, etaInSeconds, line ->
                runOnUiThread {
                    findViewById<ProgressBar>(R.id.loadingBar).visibility = View.VISIBLE
                    findViewById<ProgressBar>(R.id.loadingBar).isIndeterminate = false
                    findViewById<ProgressBar>(R.id.loadingBar).progress = progress.toInt()
                    findViewById<TextView>(R.id.ytdlplog).text = (findViewById<TextView>(R.id.ytdlplog).text.toString() + line + "\n")
                }
            })
            runOnUiThread {
                findViewById<ProgressBar>(R.id.loadingBar).visibility = View.GONE
                findViewById<ProgressBar>(R.id.loadingBar).isIndeterminate = true
            }
            var res = resp.out.substring(resp.out.indexOf("[ExtractAudio] Destination: ")).replace("[ExtractAudio] Destination: ", "")
            res = res.substring(0, res.indexOf("\n"))
            return res
        } catch (e: Exception) {
            Log.e("downloadMusic", "ERR", e)
            runOnUiThread {
                MaterialAlertDialogBuilder(this@SearchSongOnlineActivity)
                    .setTitle("Error")
                    .setMessage("Download error: ${e.message}")
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                findViewById<ProgressBar>(R.id.loadingBar).visibility = ProgressBar.GONE

                findViewById<ProgressBar>(R.id.loadingBar).visibility = View.GONE
                findViewById<TextView>(R.id.windowTitle).text = "Search Song"
                itemSelected = false
                adapter.updateItems(searchResults)
            }
            return null
        }

    }
    var itemSelected = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search_song_online)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<Button>(R.id.playlistBackBtn).setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                finish()
            }
        }
        findViewById<EditText>(R.id.SOOSearchBar).addTextChangedListener(object :
            TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // Filter the track list based on the search query
                val query = s.toString().lowercase()

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Not needed
            }
        })

        findViewById<EditText>(R.id.SOOSearchBar).setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
                // If the event is a key-down event on the "enter" button
                if ((event.action == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)
                ) {

                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v?.windowToken, 0)
                    // Youtube Search
                    findViewById<ProgressBar>(R.id.loadingBar).visibility = View.VISIBLE
                    searchYouTube((v as EditText).text.toString(), BuildConfig.API_KEY, { results ->
                        runOnUiThread {
                            findViewById<ProgressBar>(R.id.loadingBar).visibility = View.GONE

                            searchResults = results.toMutableList()
                            val recyclerView: RecyclerView = findViewById(R.id.tracklistview)
                            recyclerView.layoutManager =
                                LinearLayoutManager(this@SearchSongOnlineActivity)
                            findViewById<ProgressBar>(R.id.loadingBar).visibility = View.GONE
                            adapter = SearchResultAdapter(searchResults, { item ->
                                if(!itemSelected) {
                                    itemSelected = true
                                    findViewById<ProgressBar>(R.id.loadingBar).visibility =
                                        View.VISIBLE
                                    findViewById<TextView>(R.id.windowTitle).text = "Downloading..."
                                    var selectedItem = mutableListOf(item)
                                    adapter.updateItems(selectedItem)
                                    var storageDir =
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val newDir = File(storageDir, "Zam")
                                    if (!newDir.exists())
                                        newDir.mkdirs()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        var res = downloadMusic(
                                            "https://youtube.com/watch?v=${item.ID}",
                                            newDir.path
                                        )
                                        if (res != null) {
                                            runOnUiThread {
                                                findViewById<ProgressBar>(R.id.loadingBar).visibility =
                                                    View.GONE
                                                findViewById<TextView>(R.id.windowTitle).text =
                                                    "Downloaded"
                                                var selectedItem = mutableListOf(item)
                                                adapter.updateItems(selectedItem)
                                                val resultIntent = Intent()
                                                resultIntent.putExtra("result", res)
                                                setResult(RESULT_OK, resultIntent)
                                                finish()
                                            }
                                        }
                                    }
                                }
                            })
                            recyclerView.adapter = adapter
                        }
                    })
                    return true
                }
                return false
            }
        })

    }
}