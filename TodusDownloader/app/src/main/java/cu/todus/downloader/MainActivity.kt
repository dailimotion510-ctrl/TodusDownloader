package cu.todus.downloader

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cu.todus.downloader.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // URL directa s3 → no necesita cuenta
        val hasAccount = Prefs.hasCredentials(this)
        b.tvPhone.text = if (hasAccount) "Cuenta: ${Prefs.getPhone(this)}"
                         else "Modo directo (s3.todus.cu)"

        b.btnTest.setOnClickListener {
            val url = b.etUrl.text.toString().trim()
            if (url.isEmpty()) { toast("Pega una URL"); return@setOnClickListener }
            testUrl(url)
        }

        b.btnDownload.setOnClickListener {
            val url = b.etUrl.text.toString().trim()
            if (url.isEmpty()) { toast("Pega una URL"); return@setOnClickListener }
            startDownload(url)
        }

        b.btnLogin.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Refrescar token si hay cuenta
        if (hasAccount && Prefs.getToken(this) == null) refreshToken()
    }

    private fun testUrl(url: String) {
        b.tvStatus.text = "Probando…"
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { TodusDownloadManager.testConnection(url) }
            }.onSuccess { b.tvStatus.text = "✓ $it\n0 bytes consumidos" }
             .onFailure  { b.tvStatus.text = "✗ ${it.message}" }
        }
    }

    private fun startDownload(url: String) {
        Intent(this, DownloadActivity::class.java).apply {
            putExtra("url",   url)
            putExtra("token", Prefs.getToken(this@MainActivity))
            startActivity(this)
        }
    }

    private fun refreshToken() {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    TodusAuth.getToken(Prefs.getPhone(this@MainActivity)!!, Prefs.getPassword(this@MainActivity)!!)
                }
            }.onSuccess { Prefs.saveToken(this@MainActivity, it) }
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
