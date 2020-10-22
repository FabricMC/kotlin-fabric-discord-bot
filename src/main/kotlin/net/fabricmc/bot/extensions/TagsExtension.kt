package net.fabricmc.bot.extensions

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.extensions.Extension

/**
 * Extension in charge of keeping track of and exposing tags.
 *
 * This extension is Git-powered, all the tags are stored in a git repository.
 */
class TagsExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "tags"

    override suspend fun setup() {
        TODO("Not yet implemented")
    }
}
