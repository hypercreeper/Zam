package com.moqayed.zam

import android.net.Uri
import androidx.media3.common.MediaItem

class Playlist {
    private var name: String? = null
    private var uri: Uri? = null
    private var songUris: Array<Uri>? = null
    public var mediaItems: MutableList<MediaItem>? = null
    public fun getName(): String? {
        return name
    }
    public fun setName(name: String) {
        this.name = name
    }
    public fun getUri(): Uri? {
        return uri
    }
    public fun setUri(location: Uri?) {
        this.uri = location;
    }
    public fun getSongUris(): Array<Uri>? {
        return songUris
    }
    public fun setSongUris(uris: Array<Uri>) {
        this.songUris = uris
    }
//    public fun setMediaItems(items: MutableList<MediaItem>) {
//        this.mediaItems = items
//        var temp = arrayOf<Uri>()
//        if(mediaItems != null) {
//            for (item in mediaItems!!) {
//                temp += item.localConfiguration!!.uri
//            }
//            songUris = temp
//        }
//    }
    public fun convertMediaItemsToSongUris(): Array<Pair<Uri, Boolean>>? {
        var temp = arrayOf<Pair<Uri, Boolean>>()
        if(mediaItems != null) {
            for (item in mediaItems!!) {
                temp += Pair(item.localConfiguration!!.uri, item.mediaMetadata.isPlayable == true)
            }
            return temp
        }
        else {
            return null
        }
    }
}