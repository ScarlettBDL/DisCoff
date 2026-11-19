package com.example.discordtoolkit.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.discordtoolkit.discord.DmEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda el token y la lista de DMs cifrados en el dispositivo.
 * Si el cifrado no está disponible, no guarda nada (nunca guarda el token en claro).
 */
/** Un bot guardado en el dispositivo. */
data class Account(val id: String, val name: String, val token: String)

class Storage(context: Context) {
    private val prefs: SharedPreferences? = try {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "discoff_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }

    fun accounts(): List<Account> {
        val raw = prefs?.getString("accounts", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Account(o.optString("i"), o.optString("n"), o.optString("t"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeAccounts(list: List<Account>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("i", it.id).put("n", it.name).put("t", it.token)) }
        prefs?.edit()?.putString("accounts", arr.toString())?.apply()
    }

    /** Agrega el bot a la lista o, si ya estaba (mismo id), actualiza su nombre y token. */
    fun upsertAccount(account: Account) {
        writeAccounts(accounts().filter { it.id != account.id } + account)
    }

    fun removeAccount(id: String) {
        writeAccounts(accounts().filter { it.id != id })
    }

    fun removeAccountByToken(token: String) {
        writeAccounts(accounts().filter { it.token != token })
    }

    fun token(): String? = prefs?.getString("token", null)

    fun saveToken(token: String) {
        prefs?.edit()?.putString("token", token)?.apply()
    }

    fun clearToken() {
        prefs?.edit()?.remove("token")?.apply()
    }

    fun dms(botId: String): List<DmEntry> {
        val raw = prefs?.getString("dms_$botId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<DmEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val avatar = if (o.isNull("a")) null else o.optString("a")
                out.add(DmEntry(o.optString("c"), o.optString("u"), o.optString("n"), avatar))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveDms(botId: String, list: List<DmEntry>) {
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject().put("c", it.channelId).put("u", it.userId).put("n", it.name)
            if (it.avatar != null) o.put("a", it.avatar)
            arr.put(o)
        }
        prefs?.edit()?.putString("dms_$botId", arr.toString())?.apply()
    }

    /** Guarda un texto cualquiera (JSON) cifrado; sirve para el registro de moderación y las plantillas. */
    fun blob(key: String): String? = prefs?.getString(key, null)

    fun saveBlob(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }
}
