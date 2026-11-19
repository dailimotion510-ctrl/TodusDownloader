package cu.todus.downloader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cu.todus.downloader.databinding.ActivityRegisterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegisterBinding
    private var pendingPhone = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.layoutCode.visibility = View.GONE

        b.btnSendSms.setOnClickListener {
            val phone = b.etPhone.text.toString().trim()
            if (phone.length < 8) { toast("Teléfono inválido"); return@setOnClickListener }
            pendingPhone = phone
            requestSms(phone)
        }

        b.btnVerify.setOnClickListener {
            val code = b.etCode.text.toString().trim()
            if (code.isEmpty()) { toast("Ingresa el código"); return@setOnClickListener }
            verifyCode(code)
        }
    }

    private fun requestSms(phone: String) {
        setLoading(true)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { TodusAuth.requestSms(phone) }
            }.onSuccess { ok ->
                setLoading(false)
                if (ok) {
                    b.layoutCode.visibility = View.VISIBLE
                    b.btnSendSms.isEnabled = false
                    toast("SMS enviado a $phone")
                } else toast("Error enviando SMS")
            }.onFailure { e ->
                setLoading(false)
                toast("Error: ${e.message}")
            }
        }
    }

    private fun verifyCode(code: String) {
        setLoading(true)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    TodusAuth.verifyCode(pendingPhone, code)
                }
            }.onSuccess { password ->
                Prefs.saveCredentials(this@RegisterActivity, pendingPhone, password)
                setLoading(false)
                toast("¡Registrado!")
                startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                finish()
            }.onFailure { e ->
                setLoading(false)
                toast("Código incorrecto: ${e.message}")
            }
        }
    }

    private fun setLoading(on: Boolean) {
        b.progressBar.visibility = if (on) View.VISIBLE else View.GONE
        b.btnSendSms.isEnabled  = !on
        b.btnVerify.isEnabled   = !on
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
