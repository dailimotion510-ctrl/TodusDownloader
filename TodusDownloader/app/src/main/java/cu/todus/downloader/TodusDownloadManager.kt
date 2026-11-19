package cu.todus.downloader

import android.content.Context
import android.os.Environment
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.net.URLDecoder
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

data class DownloadProgress(
    val filename: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val done: Boolean = false,
    val error: String? = null
)

// ── SNI spoof: igual que httpx extensions={"sni_hostname": "s3.todus.cu"} ────
private class SniSocketFactory(
    private val sniHost: String,
    private val delegate: SSLSocketFactory
) : SSLSocketFactory() {
    override fun getDefaultCipherSuites()  = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites() = delegate.supportedCipherSuites
    override fun createSocket(s: Socket, h: String, p: Int, a: Boolean) =
        (delegate.createSocket(s, sniHost, p, a) as SSLSocket).sni()
    override fun createSocket(h: String, p: Int) =
        (delegate.createSocket(h, p) as SSLSocket).sni()
    override fun createSocket(h: InetAddress, p: Int) =
        (delegate.createSocket(h, p) as SSLSocket).sni()
    override fun createSocket(h: String, p: Int, lh: InetAddress, lp: Int) =
        (delegate.createSocket(h, p, lh, lp) as SSLSocket).sni()
    override fun createSocket(a: InetAddress, p: Int, la: InetAddress, lp: Int) =
        (delegate.createSocket(a, p, la, lp) as SSLSocket).sni()
    private fun SSLSocket.sni() = apply {
        sslParameters = SSLParameters().apply {
            serverNames = listOf(SNIHostName(sniHost))
        }
    }
}

private val trustAll = object : X509TrustManager {
    override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
    override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

object TodusDownloadManager {

    // Headers exactos del script — estos son los que engañan al billing de ETECSA
    private fun Request.Builder.wgetHeaders(): Request.Builder = this
        .header("Host",            "s3.todus.cu")
        .header("User-Agent",      "Wget/1.21.3")
        .header("Accept",          "*/*")
        .header("Accept-Encoding", "identity")

    private val client: OkHttpClient by lazy {
        val ctx = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAll), null)
        }
        OkHttpClient.Builder()
            .sslSocketFactory(SniSocketFactory("s3.todus.cu", ctx.socketFactory), trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    // ── Detectar si la URL es directa de s3 o necesita resolución ─────────────
    fun isDirectUrl(url: String) =
        url.contains("s3.todus.cu") || url.contains("storage.todus.cu")

    // ── Test HEAD — igual que el script original ───────────────────────────────
    fun testConnection(url: String): String {
        val req = Request.Builder().url(url).wgetHeaders().head().build()
        client.newCall(req).execute().use { r ->
            val size = r.header("Content-Length")?.toLongOrNull()
                ?.let { "%.1f MB".format(it / 1_048_576.0) } ?: "tamaño desconocido"
            return "HTTP ${r.code}  •  $size"
        }
    }

    // ── Descarga directa (URL s3.todus.cu) ────────────────────────────────────
    fun downloadDirect(
        context: Context,
        url: String,
        onProgress: (DownloadProgress) -> Unit
    ): File {
        // Nombre: último segmento del path (el hash)
        val hash    = url.substringAfterLast("/").substringBefore("?")
        val filename = if (hash.length > 20) "$hash.mp4" else hash

        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val dest = File(destDir, filename)

        val req = Request.Builder().url(url).wgetHeaders().get().build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful)
                throw RuntimeException("HTTP ${resp.code}: ${resp.message}")

            val body  = resp.body ?: throw RuntimeException("Body vacío")
            val total = body.contentLength()
            var dl    = 0L

            dest.outputStream().use { out ->
                val buf = ByteArray(65_536)
                body.byteStream().use { input ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        dl += n
                        onProgress(DownloadProgress(filename, dl, total))
                    }
                }
            }
        }

        onProgress(DownloadProgress(filename, dest.length(), dest.length(), done = true))
        return dest
    }

    // ── Resolución XMPP (todus:// o links que no son s3 directo) ─────────────
    fun resolveAndDownload(
        context: Context,
        token: String,
        rawInput: String,
        onProgress: (DownloadProgress) -> Unit
    ): File {
        val encoded = when {
            rawInput.startsWith("todus://") -> rawInput.removePrefix("todus://")
            "todus.cu" in rawInput ->
                rawInput.substringAfter("todus.cu/").removePrefix("share/")
            else -> rawInput
        }

        val body = okhttp3.FormBody.Builder()
            .add("encoded_uri", encoded)
            .build()

        val authClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val req = Request.Builder()
            .url("https://${TodusConfig.STORAGE_HOST}/v1/presigned/download")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "ToDus ${TodusConfig.APP_VERSION} Android")
            .post(body)
            .build()

        val s3url = authClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful)
                throw RuntimeException("HTTP ${resp.code}")
            val json = JSONObject(resp.body!!.string())
            json.optString("download_url").ifEmpty { json.optString("url") }
                .ifEmpty { throw RuntimeException("URL vacía") }
        }

        return downloadDirect(context, s3url, onProgress)
    }
}
