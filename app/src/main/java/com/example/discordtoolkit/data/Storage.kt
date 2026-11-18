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
                out.add(DmEntry(o.optString("c"), o.optString("u"), o.optString("n")))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveDms(botId: String, list: List<DmEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("c", it.channelId).put("u", it.userId).put("n", it.name)) }
        prefs?.edit()?.putString("dms_$botId", arr.toString())?.apply()
    }
}
