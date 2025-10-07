package com.moqayed.zam

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.moqayed.zam.MainFragment.AppDatabase
import com.moqayed.zam.MainFragment.Companion.playlist
import com.moqayed.zam.MainFragment.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private var v: View? = null
/**
 * A simple [Fragment] subclass.
 * Use the [TracksFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class TracksFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }
    fun updateRecyclerView() {
        val recyclerView: RecyclerView = v!!.findViewById(R.id.tracklistview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        var adapter = TrackRowAdapter(MainFragment.mediaItems, { item ->
            var tempPlaylist = Playlist()
            tempPlaylist.mediaItems = MainFragment.mediaItems
            tempPlaylist.setName("Tracks")
            if(context != null) {
                QueueManager.setQueue(requireContext(), tempPlaylist)
            }
            MainActivity.playTrack(requireContext(), MainFragment.mediaItems.indexOf(item))
        })
        recyclerView.adapter = adapter
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        v = inflater.inflate(R.layout.fragment_tracks, container, false)
        updateRecyclerView()
        Thread {
            while(!MainFragment.mediaItemsVarUpdated) {

            }
            CoroutineScope(Dispatchers.Main).launch {
                val recyclerView: RecyclerView = v!!.findViewById(R.id.tracklistview)
                (recyclerView.adapter as TrackRowAdapter).updateItems(MainFragment.mediaItems)
            }
        }.start()
        return v
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment TracksFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            TracksFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}