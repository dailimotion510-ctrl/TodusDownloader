package cu.todus.downloader

import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.UUID

object TodusConfig {
    const val XMPP_HOST     = "im.todus.cu"
    const val PORT_REGISTER = 1756
    const val PORT_AUTH     = 1757
    const val APP_VERSION   = "1.3.5"
    const val STORAGE_HOST  = "storage.todus.cu"
}

class TodusXmpp(
    private val host: String,
    private val port: Int,
    private val timeoutMs: Int = 8_000
) {
    private lateinit var socket: Socket
    private lateinit var writer: PrintWriter
    private lateinit var reader: BufferedReader

    fun connect() {
        socket = Socket(host, port).apply { soTimeout = timeoutMs }
        writer = PrintWriter(socket.getOutputStream(), true)
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    }

    fun send(xml: String) { writer.print(xml); writer.flush() }

    fun recv(): String = try {
        val buf = CharArray(8192)
        val n = reader.read(buf)
        if (n > 0) String(buf, 0, n) else ""
    } catch (e: Exception) { "" }

    fun close() = runCatching { socket.close() }
}

object TodusAuth {

    private fun streamOpen() =
        """<?xml version="1.0"?><stream:stream xmlns="jabber:client" """ +
        """xmlns:stream="http://etherx.jabber.org/streams" """ +
        """to="${TodusConfig.XMPP_HOST}" version="1.0">"""

    fun requestSms(phone: String): Boolean {
        val x = TodusXmpp(TodusConfig.XMPP_HOST, TodusConfig.PORT_REGISTER)
        x.connect(); x.send(streamOpen()); x.recv()
        val id = UUID.randomUUID().toString().take(8)
        x.send("""<iq type="set" id="r_$id"><query xmlns="jabber:iq:register"><phone>$phone</phone></query></iq>""")
        val r = x.recv(); x.close()
        return "result" in r
    }

    fun verifyCode(phone: String, code: String): String {
        val x = TodusXmpp(TodusConfig.XMPP_HOST, TodusConfig.PORT_REGISTER)
        x.connect(); x.send(streamOpen()); x.recv()
        val id = UUID.randomUUID().toString().take(8)
        x.send("""<iq type="set" id="v_$id"><query xmlns="jabber:iq:register"><phone>$phone</phone><code>$code</code></query></iq>""")
        val r = x.recv(); x.close()
        return Regex("<password>([^<]+)</password>").find(r)?.groupValues?.get(1)
            ?: throw RuntimeException("Sin contraseña: $r")
    }

    fun getToken(phone: String, password: String): String {
        val sasl = Base64.encodeToString(
            "\u0000$phone\u0000$password".toByteArray(), Base64.NO_WRAP
        )
        val x = TodusXmpp(TodusConfig.XMPP_HOST, TodusConfig.PORT_AUTH)
        x.connect()
        x.send(streamOpen()); x.recv()
        x.send("""<auth xmlns="urn:ietf:params:xml:ns:xmpp-sasl" mechanism="PLAIN">$sasl</auth>""")
        val auth = x.recv()
        if ("<success" !in auth) { x.close(); throw RuntimeException("Auth fallida: $auth") }
        x.send(streamOpen()); x.recv()
        val bid = UUID.randomUUID().toString().take(8)
        x.send("""<iq type="set" id="b_$bid"><bind xmlns="urn:ietf:params:xml:ns:xmpp-bind"><resource>android_${TodusConfig.APP_VERSION}</resource></bind></iq>""")
        x.recv()
        val tid = UUID.randomUUID().toString().take(8)
        x.send("""<iq type="get" id="t_$tid"><token xmlns="urn:xmpp:auth-token"/></iq>""")
        val tr = x.recv(); x.close()
        return Regex("<token[^>]*>([^<]+)</token>").find(tr)?.groupValues?.get(1)
            ?: throw RuntimeException("Sin token: $tr")
    }
}
