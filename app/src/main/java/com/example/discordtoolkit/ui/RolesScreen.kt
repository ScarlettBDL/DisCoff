package com.example.discordtoolkit.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.discordtoolkit.discord.Permissions
import com.example.discordtoolkit.ui.theme.DcColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RolesScreen(state: UiState, vm: AppViewModel) {
    val guild = state.guilds.firstOrNull { it.id == state.rolesGuildId }
    val drafts = vm.roleDrafts
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var colorFor by remember { mutableStateOf<RoleDraft?>(null) }
    var permsFor by remember { mutableStateOf<RoleDraft?>(null) }

    BackHandler { vm.closeRoles() }

    fun copy(text: String, label: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    val pendingCount = drafts.count { it.status is RoleStatus.Draft || it.status is RoleStatus.Failed }
    val created = drafts.mapNotNull { (it.status as? RoleStatus.Created) }

    Column(
        Modifier
            .fillMaxSize()
            .background(DcColors.Panel)
            .systemBarsPadding()
            .imePadding()
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { vm.closeRoles() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = DcColors.TextPrimary, fontSize = 30.sp)
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Roles masivos",
                    color = DcColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (guild != null) {
                    Text(
                        guild.name,
                        color = DcColors.TextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DcColors.Border)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (drafts.isEmpty()) {
                item {
                    Text(
                        "Tocá “Crear rol” para ir armando los roles que necesites (nombre, color y permisos) " +
                            "y después crealos todos juntos con el botón de abajo.\n\n" +
                            "El bot necesita el permiso “Gestionar roles” y solo puede dar permisos que él mismo tenga.",
                        color = DcColors.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
            items(drafts, key = { it.key }) { draft ->
                RoleRow(
                    draft = draft,
                    onColor = { colorFor = draft },
                    onPermissions = { permsFor = draft },
                    onRemove = { vm.removeRoleDraft(draft) },
                    onCopy = { id -> copy(id, "ID copiado") }
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, DcColors.Border, RoundedCornerShape(12.dp))
                        .clickable {
                            vm.addRoleDraft()
                            scope.launch {
                                delay(60)
                                listState.animateScrollToItem(maxOf(drafts.lastIndex, 0))
                            }
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = DcColors.TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Crear rol", color = DcColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Botones de abajo
        Column(
            Modifier
                .fillMaxWidth()
                .background(DcColors.Surface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = vm::createRoles,
                enabled = pendingCount > 0 && !state.creatingRoles,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.creatingRoles) "Creando roles…" else "Crear roles ($pendingCount)")
            }
            if (created.isNotEmpty()) {
                Button(
                    onClick = {
                        copy(created.joinToString("\n") { it.mention }, "IDs copiados (${created.size})")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DcColors.SurfaceHigh,
                        contentColor = DcColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copiar todos los IDs (${created.size})")
                }
            }
        }
    }

    val pickingColor = colorFor
    if (pickingColor != null) {
        ColorPickerDialog(
            initial = pickingColor.color,
            onDismiss = { colorFor = null },
            onPick = {
                pickingColor.color = it
                colorFor = null
            }
        )
    }

    val editingPerms = permsFor
    if (editingPerms != null) {
        PermissionsDialog(
            draft = editingPerms,
            otherRoles = drafts.count { it.isEditable && it !== editingPerms },
            onApplyAll = { value ->
                vm.applyPermissionsToAll(value)
                permsFor = null
            },
            onDismiss = { permsFor = null }
        )
    }
}

@Composable
private fun RoleRow(
    draft: RoleDraft,
    onColor: () -> Unit,
    onPermissions: () -> Unit,
    onRemove: () -> Unit,
    onCopy: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val status = draft.status

    // El rol recién agregado abre el teclado para escribir el nombre.
    LaunchedEffect(draft.focusPending) {
        if (draft.focusPending) {
            draft.focusPending = false
            try {
                focusRequester.requestFocus()
                keyboard?.show()
            } catch (e: Exception) {
                // si todavía no se dibujó, el usuario toca el campo
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = draft.name,
                onValueChange = { draft.name = it.take(100) },
                enabled = draft.isEditable,
                singleLine = true,
                placeholder = { Text("Nombre del rol", color = DcColors.TextMuted) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DcColors.Surface,
                    unfocusedContainerColor = DcColors.Surface,
                    disabledContainerColor = DcColors.Surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = DcColors.TextPrimary,
                    unfocusedTextColor = DcColors.TextPrimary,
                    disabledTextColor = DcColors.TextMuted
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
            // Cuadrado de color
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (draft.color == 0) DcColors.SurfaceHigh else Color(0xFF000000.toInt() or draft.color))
                    .border(1.dp, DcColors.Border, RoundedCornerShape(10.dp))
                    .clickable(enabled = draft.isEditable, onClick = onColor),
                contentAlignment = Alignment.Center
            ) {
                if (draft.color == 0) Text("🎨", fontSize = 18.sp)
            }
            // Tuerca de permisos
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DcColors.Surface)
                    .clickable(enabled = draft.isEditable, onClick = onPermissions),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Permisos",
                    tint = if (draft.permissions != 0L) DcColors.Accent else DcColors.TextMuted
                )
            }
            // Copiar el ID (si ya se creó) o quitar el borrador
            if (status is RoleStatus.Created) {
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DcColors.AccentSoft)
                        .clickable { onCopy(status.mention) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Copiar", color = DcColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = status !is RoleStatus.Creating, onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = DcColors.TextMuted)
                }
            }
        }
        when (status) {
            is RoleStatus.Failed -> Text(
                status.message,
                color = DcColors.Danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 3.dp)
            )
            is RoleStatus.Creating -> Text(
                "Creando…",
                color = DcColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 3.dp)
            )
            is RoleStatus.Created -> Text(
                "✓ Creado · ${status.mention}",
                color = DcColors.Online,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 3.dp)
            )
            RoleStatus.Draft -> Unit
        }
    }
}

@Composable
private fun PermissionsDialog(
    draft: RoleDraft,
    otherRoles: Int,
    onApplyAll: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var perms by remember { mutableLongStateOf(draft.permissions) }
    val isAdmin = perms and Permissions.administrator != 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DcColors.Surface,
        title = { Text(if (draft.name.isBlank()) "Permisos del rol" else "Permisos de ${draft.name}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Si no marcás nada, el rol usa los permisos por defecto de @everyone. " +
                        "El bot solo puede dar permisos que él mismo tenga.",
                    color = DcColors.TextMuted,
                    fontSize = 13.sp
                )
                if (isAdmin) {
                    Text(
                        "Administrador da acceso total y el bot también tiene que ser administrador para poder crearlo.",
                        color = DcColors.Danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Permissions.groups.forEach { group ->
                    Text(
                        group.title,
                        color = DcColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                    )
                    group.perms.forEach { perm ->
                        val checked = perms and perm.mask != 0L
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { perms = perms xor perm.mask }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { perms = perms xor perm.mask },
                                colors = CheckboxDefaults.colors(checkedColor = DcColors.Accent)
                            )
                            Text(perm.name, color = DcColors.TextPrimary, fontSize = 15.sp)
                        }
                    }
                }
                if (otherRoles > 0) {
                    TextButton(
                        onClick = { onApplyAll(perms) },
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text("Aplicar a todos los roles (${otherRoles + 1})")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                draft.permissions = perms
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
