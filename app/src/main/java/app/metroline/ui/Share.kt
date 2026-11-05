package app.metroline.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun shareRoute(context: Context, picture: Bitmap?, text: String) {
    val file = picture?.let {
        runCatching {
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                File(dir, "route.png").also { f -> f.outputStream().use { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) } }
            }
        }.getOrNull()
    }
    val uri = file?.let { runCatching { FileProvider.getUriForFile(context, "${context.packageName}.share", it) }.getOrNull() }
    if (uri == null) {
        chooser(context, Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text))
        return
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    chooser(context, send)
}

private fun chooser(context: Context, send: Intent) {
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app available to share with", Toast.LENGTH_SHORT).show()
    }
}

fun stack(top: Bitmap, bottom: Bitmap): Bitmap {
    fun software(b: Bitmap) = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
    val a = software(top)
    val b = software(bottom)
    val out = Bitmap.createBitmap(maxOf(a.width, b.width), a.height + b.height, Bitmap.Config.ARGB_8888)
    Canvas(out).apply {
        drawBitmap(a, 0f, 0f, null)
        drawBitmap(b, 0f, a.height.toFloat(), null)
    }
    return out
}

object WhatsAppTicket {
    private const val NUMBER = "919650855800"
    val systems = setOf("dmrc", "dmrc_airport", "rmgl")

    fun open(context: Context) {
        val link = Uri.parse("https://wa.me/$NUMBER?text=Hi")
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b", null)) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, link).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        Toast.makeText(context, "WhatsApp is needed to book tickets", Toast.LENGTH_SHORT).show()
    }
}
