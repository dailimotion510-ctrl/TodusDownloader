package cu.todus.downloader

import android.content.Context

object Prefs {
    private const val NAME  = "todus_prefs"
    private const val KEY_PHONE    = "phone"
    private const val KEY_PASSWORD = "password"
    private const val KEY_TOKEN    = "token"

    private fun ctx(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun saveCredentials(context: Context, phone: String, password: String) {
        ctx(context).edit()
            .putString(KEY_PHONE, phone)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getPhone(context: Context)    = ctx(context).getString(KEY_PHONE, null)
    fun getPassword(context: Context) = ctx(context).getString(KEY_PASSWORD, null)

    fun hasCredentials(context: Context) =
        getPhone(context) != null && getPassword(context) != null

    fun saveToken(context: Context, token: String) {
        ctx(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context) = ctx(context).getString(KEY_TOKEN, null)

    fun clear(context: Context) = ctx(context).edit().clear().apply()
}
