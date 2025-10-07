package com.moqayed.zam

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.max

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [PlaylistsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class PlaylistsFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private var v: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }
    fun calculateColumnCount(context: Context, columnWidthDp: Float): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return max(1, (screenWidthDp / columnWidthDp).toInt())
    }

    fun searchPlaylists() {
        val playlistview = v?.findViewById<GridLayout>(R.id.PlaylistsCardView)
        val columnCount = calculateColumnCount(requireContext(), 170f) // Each column ~100dp
        playlistview?.columnCount = columnCount
        (requireContext() as Activity).runOnUiThread {
            playlistview?.removeViews(1, playlistview.childCount-1)
        }
        var playlists = context?.getDir("Playlists", Context.MODE_PRIVATE)?.listFiles()
        if(playlists != null) {
            for(playlistFile in playlists) {
                (requireContext() as Activity).runOnUiThread {
                    if (playlistview != null) {
                        MainActivity.createPlaylistView(
                            requireContext(),
                            playlistview,
                            playlistFile.name.replace(".m3u",""),
                            playlistFile.path,
                            null
                        )
                    }
                }
            }
        }
    }
    private val activityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            searchPlaylists()
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        v = inflater.inflate(R.layout.fragment_playlists, container, false)
        v?.findViewById<LinearLayout>(R.id.createPlaylistBtn)?.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Call your function here when touch is released
                activityLauncher.launch(Intent(requireContext(), CreatePlaylistActivity::class.java))
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
        searchPlaylists()
        return v
    }
    override fun onResume() {
        super.onResume()
        searchPlaylists()
    }
    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment PlaylistsFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            PlaylistsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}