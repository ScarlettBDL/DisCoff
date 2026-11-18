package com.example.discordtoolkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.discordtoolkit.data.Storage
import com.example.discordtoolkit.ui.theme.DcColors
import org.json.JSONArray
import org.json.JSONObject

class TemplateItem(val name: String, val data: JSONObject)

/** Plantillas guardadas en el teléfono (cifradas): embeds y conjuntos de roles. */
class Templates(private val storage: Storage, private val botId: () -> String?) {
    companion object {
        const val EMBED = "embed"
        const val ROLES = "roles"
    }

    var embeds by mutableStateOf<List<TemplateItem>>(emptyList())
    var roleSets by mutableStateOf<List<TemplateItem>>(emptyList())

    private fun key(kind: String): String? = botId()?.let { "tpl_${kind}_$it" }

    fun load() {
        embeds = read(EMBED)
        roleSets = read(ROLES)
    }

    private fun read(kind: String): List<TemplateItem> {
        val raw = key(kind)?.let { storage.blob(it) } ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                TemplateItem(o.optString("n"), o.getJSONObject("d"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun list(kind: String): List<TemplateItem> = if (kind == EMBED) embeds else roleSets

    private fun write(kind: String, items: List<TemplateItem>) {
        if (kind == EMBED) embeds = items else roleSets = items
        val k = key(kind) ?: return
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("n", it.name).put("d", it.data)) }
        storage.saveBlob(k, arr.toString())
    }

    /** Guarda con ese nombre; si ya existía una con el mismo nombre, la reemplaza. */
    fun save(kind: String, name: String, data: JSONObject) {
        val clean = name.trim().ifEmpty { return }
        write(kind, list(kind).filter { it.name != clean } + TemplateItem(clean, data))
    }

    fun delete(kind: String, index: Int) {
        write(kind, list(kind).filterIndexed { i, _ -> i != index })
    }

    fun reset() {
        embeds = emptyList()
        roleSets = emptyList()
    }
}

/** Diálogo para guardar lo actual como plantilla y cargar o borrar las guardadas. */
@Composable
internal fun TemplatesDialog(
    title: String,
    items: List<String>,
    canSave: Boolean,
    onSave: (String) -> Unit,
    onLoad: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DcColors.Surface,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                FormField("Nombre de la plantilla", name, { name = it }, 60)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSave(name)
                        name = ""
                    },
                    enabled = canSave && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar lo actual") }
                Spacer(Modifier.height(12.dp))
                if (items.isEmpty()) {
                    Text("Todavía no guardaste plantillas.", color = DcColors.TextMuted, fontSize = 13.sp)
                }
                items.forEachIndexed { index, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DcColors.Panel)
                            .clickable { onLoad(index) }
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item, color = DcColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDelete(index) }) { Text("🗑") }
                    }
                }
                Text(
                    "Tocá una plantilla para cargarla.",
                    color = DcColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
