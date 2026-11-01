package app.metroline

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.metroline.data.Store
import app.metroline.ui.App
import app.metroline.ui.Nav

class MainActivity : ComponentActivity() {
    private val nav = Nav()
    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val label = runCatching { intent?.getStringExtra(Shortcuts.EXTRA_BOOKMARK) }.getOrNull() ?: return
        if (label.length > 64) return
        store.place(label)?.let { nav.startFrom(it.stationId) }
    }
}
