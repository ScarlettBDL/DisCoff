package com.example.discordtoolkit.discord

/** Permiso de Discord: el bit que se suma en el campo `permissions` de un rol. */
class Perm(val name: String, val bit: Int) {
    val mask: Long get() = 1L shl bit
}

class PermGroup(val title: String, val perms: List<Perm>)

object Permissions {
    const val ADMINISTRATOR_BIT = 3

    val groups: List<PermGroup> = listOf(
        PermGroup(
            "General",
            listOf(
                Perm("Administrador", 3),
                Perm("Ver canales", 10),
                Perm("Gestionar canales", 4),
                Perm("Gestionar roles", 28),
                Perm("Gestionar servidor", 5),
                Perm("Ver registro de auditoría", 7),
                Perm("Gestionar webhooks", 29),
                Perm("Gestionar emojis y stickers", 30),
                Perm("Crear invitaciones", 0),
                Perm("Cambiar apodo", 26),
                Perm("Gestionar apodos", 27)
            )
        ),
        PermGroup(
            "Moderación",
            listOf(
                Perm("Expulsar miembros", 1),
                Perm("Banear miembros", 2),
                Perm("Aislar miembros (timeout)", 40)
            )
        ),
        PermGroup(
            "Texto",
            listOf(
                Perm("Enviar mensajes", 11),
                Perm("Enviar mensajes en hilos", 38),
                Perm("Crear hilos públicos", 35),
                Perm("Crear hilos privados", 36),
                Perm("Insertar enlaces", 14),
                Perm("Adjuntar archivos", 15),
                Perm("Añadir reacciones", 6),
                Perm("Usar emojis externos", 18),
                Perm("Usar stickers externos", 37),
                Perm("Mencionar @everyone", 17),
                Perm("Gestionar mensajes", 13),
                Perm("Gestionar hilos", 34),
                Perm("Ver historial de mensajes", 16),
                Perm("Usar comandos de aplicaciones", 31),
                Perm("Enviar encuestas", 49)
            )
        ),
        PermGroup(
            "Voz",
            listOf(
                Perm("Conectarse", 20),
                Perm("Hablar", 21),
                Perm("Video / transmitir", 9),
                Perm("Usar detección de voz", 25),
                Perm("Prioridad de palabra", 8),
                Perm("Silenciar miembros", 22),
                Perm("Ensordecer miembros", 23),
                Perm("Mover miembros", 24)
            )
        ),
        PermGroup(
            "Eventos",
            listOf(
                Perm("Gestionar eventos", 33),
                Perm("Crear eventos", 44)
            )
        )
    )

    val administrator: Long get() = 1L shl ADMINISTRATOR_BIT
}
