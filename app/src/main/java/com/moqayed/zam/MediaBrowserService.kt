package com.moqayed.zam

import android.content.Intent
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture

class MediaBrowserService : MediaLibraryService() {

    private lateinit var librarySession: MediaLibrarySession
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        player = MainActivity.musicService?.player!!

        val callback = object : MediaLibrarySession.Callback {
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootItem = MediaItem.Builder()
                    .setMediaId("root")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Root")
                            .build()
                    ).build()

                val future = SettableFuture.create<LibraryResult<MediaItem>>()
                future.set(LibraryResult.ofItem(rootItem, null))
                return future
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {

                val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                future.set(LibraryResult.ofItemList(MainActivity.currentTracksPlaylist.mediaItems?.toMutableList()!!, null))
                return future
            }
        }

        librarySession = MediaLibrarySession.Builder(this, player, callback)
            .setId("my_music_session")
            .build()

//        setSession(librarySession)
    }

    override fun onDestroy() {
        librarySession.release()
        player.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        TODO("Not yet implemented")
    }
}
