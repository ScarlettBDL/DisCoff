package com.example.discordtoolkit.ui

enum class ChannelType { TEXT, VOICE }

data class Channel(val id: String, val name: String, val type: ChannelType)

data class Category(val id: String, val name: String, val channels: List<Channel>)

data class Guild(val id: String, val name: String, val initials: String, val color: Long)

data class BotUser(val name: String, val status: String)

data class DirectMessage(val id: String, val name: String)

data class Tool(val id: String, val title: String, val description: String)

/** Datos de mentira para armar la UI. Se reemplazan después por los reales de la API de Discord. */
object FakeData {
    val bot = BotUser(name = "Mi Bot", status = "Conectado")

    val guilds = listOf(
        Guild("g1", "Servidor de prueba", "SP", 0xFF3B3F8F),
        Guild("g2", "Demo", "De", 0xFF1F6F54),
        Guild("g3", "Comunidad", "Co", 0xFF8F3B3B),
        Guild("g4", "Staff", "St", 0xFF6B4A1E),
        Guild("g5", "Pruebas", "Pr", 0xFF2E5A7A),
        Guild("g6", "Dev", "Dv", 0xFF5A2E7A)
    )

    val directMessages = listOf(
        DirectMessage("d1", "Usuario 1"),
        DirectMessage("d2", "Usuario 2"),
        DirectMessage("d3", "Usuario 3")
    )

    val tools = listOf(
        Tool("roles", "Roles masivos", "Creá varios roles de una sola vez"),
        Tool("embeds", "Embeds", "Armá y mandá embeds sin webhooks"),
        Tool("mod", "Moderación", "Ban, softban, mute y más"),
        Tool("msgs", "Mensajes", "Leé canales y enviá mensajes")
    )

    fun categoriesFor(guildId: String): List<Category> = listOf(
        Category(
            id = "$guildId-info",
            name = "Información",
            channels = listOf(
                Channel("$guildId-reglas", "reglas", ChannelType.TEXT),
                Channel("$guildId-anuncios", "anuncios", ChannelType.TEXT)
            )
        ),
        Category(
            id = "$guildId-com",
            name = "Comunidad",
            channels = listOf(
                Channel("$guildId-general", "general", ChannelType.TEXT),
                Channel("$guildId-media", "multimedia", ChannelType.TEXT),
                Channel("$guildId-bots", "bots", ChannelType.TEXT)
            )
        ),
        Category(
            id = "$guildId-voz",
            name = "Canales de voz",
            channels = listOf(
                Channel("$guildId-voz1", "Sala 1", ChannelType.VOICE),
                Channel("$guildId-voz2", "Sala 2", ChannelType.VOICE)
            )
        )
    )
}
