package app.metroline

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import app.metroline.data.Store
import app.metroline.data.Tour
import app.metroline.ui.App
import app.metroline.ui.Nav

class MainActivity : ComponentActivity() {
    private val nav = Nav()
    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener(::fadeOut)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val net = (application as MetroApp).network
        store = Store(this, net) { Shortcuts.sync(this, net, it) }
        Shortcuts.sync(this, net, store.places)
        if (savedInstanceState == null) handle(intent)
        setContent { App(net, store, nav) { finish() } }
    }

    /**
     * The launch screen stays only while the app loads. If that was quicker than the logo's draw-in (Android 12+),
     * the rest of the draw-in plays first so the logo never disappears half drawn; then it fades out.
     */
    private fun fadeOut(splash: SplashScreenViewProvider) {
        // The start time is wall-clock time (epoch millis), not time since boot.
        val rest = (splash.iconAnimationStartMillis + splash.iconAnimationDurationMillis - System.currentTimeMillis())
            .coerceIn(0, 600)
        val ease = PathInterpolator(0.22f, 1f, 0.36f, 1f)
        runCatching {
            // The logo gets its own fade: on Android 12+ it is drawn on a separate surface that ignores the
            // splash view's alpha, and would otherwise stay on top of the app while the background fades.
            splash.iconView.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setStartDelay(rest).setDuration(320)
                .setInterpolator(ease).start()
        }
        splash.view.animate().alpha(0f).setStartDelay(rest).setDuration(320).setInterpolator(ease)
            .withEndAction { splash.remove() }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val label = runCatching { intent?.getStringExtra(Shortcuts.EXTRA_BOOKMARK) }.getOrNull() ?: return
        if (label.length > 64) return
        store.place(label)?.let {
            // A bookmark shortcut is a clear "take me there": close a replayed tour instead of opening behind it.
            if (store.tour == Tour.Replay) store.finishTour()
            nav.startFrom(it.stationId)
        }
    }
}
