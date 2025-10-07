package com.moqayed.zam

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import androidx.media3.ui.PlayerNotificationManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.moqayed.zam.MainActivity.Companion.setPlayerNotLoadingUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicService : MediaSessionService() {
    companion object {
        public var playlist: Playlist? = null
        public var items: MutableList<MediaItem>? = null
        public var targetMediaItem: MediaItem? = null
    }
    private val binder = MusicBinder()
    public lateinit var player: ExoPlayer
    public lateinit var mediaSession: MediaSession
//    public lateinit var mediaSessionConnnector: MediaSessionConnector(mediaSession)
    private val SHUFFLE_ACTION = "ACTION_SHUFFLE"
    private val REPEAT_ACTION = "ACTION_REPEAT"
    private val customCommandShuffle = SessionCommand(SHUFFLE_ACTION, Bundle.EMPTY)
    private val customCommandRepeat = SessionCommand(REPEAT_ACTION, Bundle.EMPTY)

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }
//
//    override fun onGetRoot(
//        clientPackageName: String,
//        clientUid: Int,
//        rootHints: Bundle?
//    ): androidx.media.MediaBrowserServiceCompat.BrowserRoot? {
//        // Allow connections from any client.
//        // You might want to restrict this based on clientPackageName or clientUid
//        // for security reasons in a real application.
//        return androidx.media.MediaBrowserServiceCompat.BrowserRoot(
//            "root_id", null)
//    }
//
//    override fun onLoadChildren(
//        parentId: String,
//        result: androidx.media.MediaBrowserServiceCompat.Result<MutableList<android.support.v4.media.MediaBrowserCompat.MediaItem>>
//    ) {
//        val mediaItemsCompat = mutableListOf<android.support.v4.media.MediaBrowserCompat.MediaItem>()
//        items?.forEach { mediaItem ->
//            val description = android.support.v4.media.MediaDescriptionCompat.Builder()
//                .setMediaId(mediaItem.mediaId)
//                .setTitle(mediaItem.mediaMetadata.title)
//                .setSubtitle(mediaItem.mediaMetadata.artist)
//                .setIconBitmap(mediaItem.mediaMetadata.artworkData?.let { BitmapFactory.decodeByteArray(it, 0, it.size) })
//                .build()
//            mediaItemsCompat.add(android.support.v4.media.MediaBrowserCompat.MediaItem(description, android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
//        }
//        result.sendResult(mediaItemsCompat)
//    }


    override fun onCreate() {
        super.onCreate()

        val shuffleButton = CommandButton.Builder(CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName("Shuffle")
            .setEnabled(true)
            .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
//            .setSessionCommand(customCommandShuffle)
//            .setSlots(CommandButton.SLOT_BACK)
            .build()

        val repeatButton = CommandButton.Builder(CommandButton.ICON_REPEAT_ALL)
            .setDisplayName("Repeat")
            .setEnabled(true)
            .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE)
//            .setSessionCommand(customCommandRepeat)
//            .setSlots(CommandButton.SLOT_FORWARD)
            .build()

        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true) // Auto-pause when headphones are unplugged
            .build()
        // Initialize MediaSession
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntentForActivity())
//            .setMediaButtonPreferences(ImmutableList.of(shuffleButton, repeatButton))
//            .setCommandButtonsForMediaItems(listOf(shuffleButton, repeatButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ConnectionResult {
                    // Set available player and session commands.
                    Log.i("MusicService_Controller", controller.packageName)
//                    Log.i("MusicService_Controller", controller.maxCommandsForMediaItems.toString())
//                    session.setMediaButtonPreferences(ImmutableList.of(shuffleButton, repeatButton))
                    return ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                .add(customCommandShuffle)
                                .add(customCommandRepeat)
                                .build()
                        )
                        .build()
                }
                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        SHUFFLE_ACTION -> {
                            // Handle repeat action
                            var repeatMode = session.player.repeatMode
                            if(repeatMode == Player.REPEAT_MODE_OFF)
                                session.player.repeatMode = Player.REPEAT_MODE_ALL
                            else if(repeatMode == Player.REPEAT_MODE_ALL)
                                session.player.repeatMode = Player.REPEAT_MODE_ONE
                            else
                                session.player.repeatMode = Player.REPEAT_MODE_OFF
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        REPEAT_ACTION -> {
                            session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
//            .setCustomLayout(ImmutableList.of(shuffleButton, repeatButton)) // Add buttons here
            .build()
        mediaSession?.player = player
        mediaSession.mediaNotificationControllerInfo?.let {
            mediaSession.setAvailableCommands(
                it,
                SessionCommands.EMPTY,
                Player.Commands.Builder()
                    .add(Player.COMMAND_SET_SHUFFLE_MODE)
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .build()
            )
        }
//        mediaSession = MediaSessionCompat(this, "MusicService_MSC")

        // Set up a foreground notification for media playback
        startForegroundService()
    }
    private fun createMediaNotification(): Notification {
// And the user can play, skip to next or previous, and seek
        val channelId = "music_channel"

        // Create notification channel (needed for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Music Player", NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // Create MediaStyle notification
        var notification = PlayerNotificationManager.Builder(this, 1, channelId)
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player) =
                    player.currentMediaItem?.mediaMetadata?.title.toString()

                override fun getCurrentContentText(player: Player) =
                    player.currentMediaItem?.mediaMetadata?.artist.toString()

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? {
                    if(player.currentMediaItem?.mediaMetadata?.artworkData != null) {
                        return BitmapFactory.decodeByteArray(player.currentMediaItem?.mediaMetadata?.artworkData, 0, player.currentMediaItem?.mediaMetadata?.artworkData!!.size)
                    }
                    else
                        return null
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    return pendingIntentForActivity()
                }
            })
            .setCustomActionReceiver(object : PlayerNotificationManager.CustomActionReceiver {
                override fun createCustomActions(context: Context, instanceId: Int): Map<String, NotificationCompat.Action> {
                    val shuffleintent = Intent(this@MusicService, MainActivity::class.java).apply {
                        putExtra("ACTION", "SHUFFLE")
                    }
                    var shufflependingintent = PendingIntent.getActivity(this@MusicService, 0, shuffleintent, PendingIntent.FLAG_IMMUTABLE)
                    val repeatintent = Intent(this@MusicService, MainActivity::class.java).apply {
                        putExtra("ACTION", "REPEAT")
                    }
                    var repeatpendingintent = PendingIntent.getActivity(this@MusicService, 0, repeatintent, PendingIntent.FLAG_IMMUTABLE)

                    return mapOf(
                        SHUFFLE_ACTION to NotificationCompat.Action(
                            R.drawable.baseline_shuffle_24, "Shuffle", shufflependingintent
                        ),
                        REPEAT_ACTION to NotificationCompat.Action(
                            R.drawable.baseline_repeat_24, "Repeat", repeatpendingintent
                        )
                    )
                }

                override fun getCustomActions(player: Player): List<String> {
                    return listOf(SHUFFLE_ACTION, REPEAT_ACTION)
                }

                override fun onCustomAction(player: Player, action: String, intent: Intent) {
                    when (action) {
                        SHUFFLE_ACTION -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                        REPEAT_ACTION -> player.repeatMode = (player.repeatMode + 1) % 3
                    }
                }
            })
            .setSmallIconResourceId(R.drawable.ic_music_note)
            .setStopActionIconResourceId(R.drawable.ic_stop)
            .setRewindActionIconResourceId(R.drawable.baseline_shuffle_24)
            .setFastForwardActionIconResourceId(R.drawable.baseline_repeat_24)
            .build()
        notification.setMediaSessionToken(mediaSession!!.platformToken)

        notification.setUsePreviousAction(true)
        notification.setUseNextAction(true)
        notification.setUseStopAction(true)
        notification.setUsePreviousAction(true)
        notification.setUseFastForwardAction(false)
        notification.setUseRewindAction(false)
        notification.setUseChronometer(true)

        notification.setPlayer(player)
        notification.setPriority(NotificationCompat.PRIORITY_HIGH)
        notification.setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)

//        session.setPlaybackState(playbackStateBuilder.build())
//        style.setMediaSession(session.sessionToken)
        val previousintent = Intent(this@MusicService, MusicService::class.java).apply {
            putExtra("ACTION", "SHUFFLE")
        }
        var previouspendingintent = PendingIntent.getActivity(this@MusicService, 0, previousintent, PendingIntent.FLAG_IMMUTABLE)
        val playpauseintent = Intent(this@MusicService, MusicService::class.java).apply {
            putExtra("ACTION", "REPEAT")
        }
        var playpausependingintent = PendingIntent.getActivity(this@MusicService, 0, playpauseintent, PendingIntent.FLAG_IMMUTABLE)
        val nextintent = Intent(this@MusicService, MusicService::class.java).apply {
            putExtra("ACTION", "REPEAT")
        }
        var nextpendingintent = PendingIntent.getActivity(this@MusicService, 0, nextintent, PendingIntent.FLAG_IMMUTABLE)

        val shuffleintent = Intent(this@MusicService, MusicService::class.java).apply {
            putExtra("ACTION", "SHUFFLE")
        }
        var shufflependingintent = PendingIntent.getActivity(this@MusicService, 0, shuffleintent, PendingIntent.FLAG_IMMUTABLE)
        val repeatintent = Intent(this@MusicService, MusicService::class.java).apply {
            putExtra("ACTION", "REPEAT")
        }
        var repeatpendingintent = PendingIntent.getActivity(this@MusicService, 0, repeatintent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(player.currentMediaItem?.mediaMetadata?.title)
            .setContentText(player.currentMediaItem?.mediaMetadata?.artist)
            .setSmallIcon(R.drawable.default_music_icon)
            .setLargeIcon(if(player.currentMediaItem?.mediaMetadata?.artworkData != null) {
                        BitmapFactory.decodeByteArray(player.currentMediaItem?.mediaMetadata?.artworkData, 0, player.currentMediaItem?.mediaMetadata?.artworkData!!.size)
                    }
                    else
                        null)
//            .addAction(androidx.media3.session.R.drawable.media3_icon_shuffle_off, "Shuffle", shufflependingintent)
            .addAction(androidx.media3.session.R.drawable.media3_icon_previous, "Previous Track", previouspendingintent)
            .addAction(androidx.media3.session.R.drawable.media3_icon_pause, "Pause", playpausependingintent)
            .addAction(androidx.media3.session.R.drawable.media3_icon_next, "Next Track", nextpendingintent)
//            .addAction(androidx.media3.session.R.drawable.media3_icon_repeat_all, "Repeat", repeatpendingintent)
            .setStyle(androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(0,1,2,3,4))
            .setOngoing(true)
            .build()
    }


    // Inside your MusicService class
    private fun pendingIntentForActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Optional: If MainActivity can be launched in different ways,
            // you might set an action to help it identify this specific launch.
            // action = "com.yourapp.ACTION_SHOW_PLAYER"
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)
    }
//    private fun pendingIntentForActivity(): PendingIntent {
//        val intent = Intent(this, MainActivity::class.java)
//        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
//    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun startForegroundService() {
        val notification = createMediaNotification()
        startForeground(1, notification)
    }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action: String? = intent?.getStringExtra("ACTION")
        val restore: Boolean = intent?.getBooleanExtra("RESTORE", false) == true
        val position: Long = intent?.getLongExtra("POSITION", 0)?:0
        val index: Int = intent?.getIntExtra("INDEX", 0)?:0
        if(action == "PLAY") {
            if(!restore) {
                playMusic(targetMediaItem)
            }
            else {
                playMusic(index, position)
            }
        }
        else if(action ==  "PAUSE") {
            pauseMusic()
        }
        else if(action ==  "RESUME") {
            resumeMusic()
        }
        else if(action ==  "STOP") {
            resumeMusic()
        }


        return START_STICKY
    }
    private var currentPlaylist: Playlist? = null
    fun playMusic(targetItem: MediaItem?) {
        CoroutineScope(Dispatchers.IO).launch {
            var passedPlaylist = QueueManager.getMemoryQueue()
            var match = true
            if (currentPlaylist != null && currentPlaylist!!.mediaItems != null) {
                if (currentPlaylist?.mediaItems?.size == passedPlaylist?.mediaItems?.size) {
//                if (passedPlaylist == currentPlaylist) {
                    for (i in 0..currentPlaylist!!.mediaItems!!.size - 1) {
                        if (currentPlaylist!!.mediaItems!![i] != passedPlaylist!!.mediaItems!![i]) {
                            match = false
                            break
                        }
                    }
//                } else {
//                    match = false
//                }
                } else {
                    match = false
                }
            } else {
                match = false
            }
            if (!match) {
                Toast.makeText(
                    this@MusicService,
                    "Added " + passedPlaylist!!.mediaItems!!.size + " items to queue",
                    Toast.LENGTH_SHORT
                ).show()
                currentPlaylist = passedPlaylist
//            Thread.sleep(20)
                CoroutineScope(Dispatchers.Main).launch {
                    player.setMediaItems(currentPlaylist!!.mediaItems!!.filter { it.mediaMetadata.isPlayable == true })
                    player.prepare()
                }
            }
            CoroutineScope(Dispatchers.Main).launch {
                if (targetItem != null) {
                    player.seekTo(currentPlaylist!!.mediaItems!!.indexOf(targetItem), 0)
                }

                player.play()
                setPlayerNotLoadingUI()
            }
        }
    }
    fun playMusic(index: Int, position: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            var passedPlaylist = QueueManager.getMemoryQueue()
            currentPlaylist = passedPlaylist
//        Thread.sleep(20)
            CoroutineScope(Dispatchers.Main).launch {
                player.setMediaItems(currentPlaylist!!.mediaItems!!.filter { it.mediaMetadata.isPlayable == true })
                player.prepare()
                player.seekTo(index, position)
                setPlayerNotLoadingUI()
//            player.play()
            }
        }
    }

    fun playMusic(uris: Array<Uri>, targetUri: Uri?) {
        var match = true
        if (player.mediaItemCount == uris.size) {
            for (i in 0..player.mediaItemCount - 1) {
                if (player.getMediaItemAt(i).localConfiguration?.uri != uris[i]) {
                    match = false
                    break
                }
            }
        } else {
            match = false
        }
        if (!match) {
            var mediaitems: List<MediaItem> = listOf()
            Thread {
                for (uri in uris) {
                    var mediaitem = MainActivity.getAudioMetadataFFmpeg(uri) // short time

                    mediaitems += mediaitem
                }
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@MusicService,
                        "Added " + mediaitems.size + " items to queue",
                        Toast.LENGTH_SHORT
                    ).show()
                    player.setMediaItems(mediaitems)

                    if (targetUri != null) {
                        var target = 0
                        for (i in 0..player.mediaItemCount) {
                            if (player.getMediaItemAt(i).localConfiguration?.uri == targetUri) {
                                target = i
                                break
                            }
                        }
                        player.seekTo(target, 0)
                    }
                    player.prepare()
                    player.play()
                }
            }.start()
        }
        else {
            if (targetUri != null) {
                var target = 0
                for (i in 0..player.mediaItemCount) {
                    if (player.getMediaItemAt(i).localConfiguration?.uri == targetUri) {
                        target = i
                        break
                    }
                }
                player.seekTo(target, 0)
            }
            player.prepare()
            player.play()
        }
    }
    fun resumeMusic() {
        player.play()
    }
    fun pauseMusic() {
        player.pause()
    }

    fun stopMusic() {
        player.stop()
        stopSelf()
    }
}
