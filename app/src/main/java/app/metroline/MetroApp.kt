package app.metroline

import android.app.Application
import app.metroline.data.Network

/** Parses the bundled network once per process. */
class MetroApp : Application() {
    val network: Network by lazy { Network.parse(assets.open("network.json").bufferedReader().readText()) }
}
