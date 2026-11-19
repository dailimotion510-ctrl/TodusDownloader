package cu.todus.downloader

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cu.todus.downloader.databinding.ActivityDownloadBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadActivity : AppCompatActivity() {

    private lateinit var b: ActivityDownloadBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(b.root)

        val rawUrl = intent.getStringExtra("url") ?: run { finish(); return }
        val token  = intent.getStringExtra("token")  // puede ser null si es URL directa

        b.tvFilename.text = "Preparando…"
        b.progressBar.isIndeterminate = true

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (TodusDownloadManager.isDirectUrl(rawUrl)) {
                        // URL s3.todus.cu directa — solo SNI + headers, sin auth
                        b.tvStatus.text.also {
                            runOnUiThread { b.tvStatus.text = "URL directa s3 — descargando…" }
                        }
                        TodusDownloadManager.downloadDirect(
                            this@DownloadActivity, rawUrl
                        ) { prog -> runOnUiThread { updateProgress(prog) } }
                    } else {
                        // Necesita resolución XMPP primero
                        val t = token ?: throw RuntimeException("Necesitas iniciar sesión para este tipo de URL")
                        runOnUiThread { b.tvStatus.text = "Resolviendo URL…" }
                        TodusDownloadManager.resolveAndDownload(
                            this@DownloadActivity, t, rawUrl
                        ) { prog -> runOnUiThread { updateProgress(prog) } }
                    }
                }
            }.onSuccess { file ->
                b.progressBar.progress = 100
                b.progressBar.isIndeterminate = false
                b.tvStatus.text = "✓ Guardado:\n${file.absolutePath}"
                toast("Descarga completa")
            }.onFailure { e ->
                b.progressBar.isIndeterminate = false
                b.tvStatus.text = "Error: ${e.message}"
                toast("Falló: ${e.message}")
            }
        }
    }

    private fun updateProgress(p: DownloadProgress) {
        b.tvFilename.text = p.filename
        b.progressBar.isIndeterminate = false
        if (p.totalBytes > 0) {
            val pct = (p.bytesDownloaded * 100 / p.totalBytes).toInt()
            b.progressBar.progress = pct
            b.tvStatus.text = "$pct%  •  ${p.bytesDownloaded.mb()} / ${p.totalBytes.mb()} MB"
        } else {
            b.tvStatus.text = "${p.bytesDownloaded.mb()} MB descargados"
        }
    }

    private fun Long.mb() = "%.1f".format(this / 1_048_576.0)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
