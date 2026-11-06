package app.teletype.sdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.play
import platform.AVFoundation.pause
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformVideoPlayer(url: String, modifier: Modifier) {
    val nsUrl = NSURL.URLWithString(url)
    val player = nsUrl?.let { AVPlayer(uRL = it) }
    DisposableEffect(url) {
        player?.play()
        onDispose { player?.pause() }
    }
    UIKitView(
        modifier = modifier,
        factory = {
            val controller = AVPlayerViewController()
            controller.player = player
            controller.showsPlaybackControls = true
            controller.view
        },
    )
}
