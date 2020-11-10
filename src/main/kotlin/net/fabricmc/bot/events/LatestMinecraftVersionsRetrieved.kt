package net.fabricmc.bot.events

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.events.ExtensionEvent
import net.fabricmc.bot.extensions.MinecraftLatest

/**
 * Event fired when the version check extension gets the latest versions of Minecraft.
 *
 * @param versions Data class instance representing the latest versions.
 */
class LatestMinecraftVersionsRetrieved(
        override val bot: ExtensibleBot,
        val versions: MinecraftLatest
) : ExtensionEvent
