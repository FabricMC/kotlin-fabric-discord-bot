package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.entity.GuildEmoji
import com.gitlab.kordlib.core.event.gateway.ReadyEvent
import com.gitlab.kordlib.core.event.guild.EmojisUpdateEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import kotlinx.coroutines.flow.toSet
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.enums.Emojis

/**
 * Extension in charge of keeping track of the emojis in a configured emoji guild.
 *
 * A static API for retrieving an emoji is available.
 */
class EmojiExtension(bot: ExtensibleBot) : Extension(bot) {
    companion object {
        private val emojis: MutableMap<String, GuildEmoji> = mutableMapOf()

        /**
         * Get an emoji mention by string name. Defaults to `:name:` if the emoji can't be found.
         *
         * @param name Emoji to retrieve.
         */
        fun getEmoji(name: String) =
                emojis[name]?.mention ?: "`:$name:`"

        /**
         * Get an emoji mention by [Emojis] enum. Defaults to `:name:` if the emoji can't be found.
         *
         * @param emoji Emoji to retrieve.
         */
        fun getEmoji(emoji: Emojis) =
                emojis[emoji.emoji]?.mention ?: "`:${emoji.emoji}:`"
    }

    override val name = "emoji"

    override suspend fun setup() {
        event<ReadyEvent> {
            action {
                val emojiGuild = config.getEmojiGuild()
                emojis.clear()

                populateEmojis(emojiGuild.emojis.toSet())
            }
        }

        event<EmojisUpdateEvent> {
            check(inGuild(config.getEmojiGuild()))

            action {
                populateEmojis(it.emojis)
            }
        }
    }

    private fun populateEmojis(newEmojis: Collection<GuildEmoji>) {
        emojis.clear()

        newEmojis.forEach {
            if (it.name != null) {
                emojis[it.name!!] = it
            }
        }
    }
}
