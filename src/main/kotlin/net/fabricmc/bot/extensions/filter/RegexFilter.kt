package net.fabricmc.bot.extensions.filter

import dev.kord.common.entity.ChannelType
import dev.kord.core.entity.Message
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.Channel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageUpdateEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.getUrl

private val flagRegex = "\\(\\?[a-z]+\\)".toRegex()

/**
 * Filter class intended for finding messages and alerting staff based on regular expression matches.
 *
 * This class is *heavily* inspired by the work done by the fine folks at Python Discord.
 * You can find their bot code here: https://github.com/python-discord/bot
 */
class RegexFilter(bot: ExtensibleBot) : Filter(bot) {
    private val regexes = loadRegexes()

    override val concerns = arrayOf(FilterConcerns.CONTENT, FilterConcerns.EMBEDS)

    override suspend fun checkCreate(event: MessageCreateEvent, content: String): Boolean {
        doCheck(event.message, content)

        return true  // We aren't removing the message, so let processing continue
    }

    override suspend fun checkEdit(event: MessageUpdateEvent, content: String): Boolean {
        doCheck(event.getMessage(), content)

        return true  // We aren't removing the message, so let processing continue
    }

    private suspend fun doCheck(message: Message, content: String) {
        val embeds = message.embeds.filter { it.provider != null && it.video != null }
        val matches: MutableSet<String> = mutableSetOf()

        var match: MatchResult?

        for (pattern in regexes) {
            match = pattern.find(content)

            if (match != null) {
                matches += match.value
            }

            for (embed in embeds) {
                match = pattern.find(embed.description ?: "")

                if (match != null) {
                    matches += match.value
                }

                match = pattern.find(embed.title ?: "")

                if (match != null) {
                    matches += match.value
                }
            }
        }

        if (matches.isNotEmpty()) {
            sendAlert {
                embed {
                    title = "Regex filter triggered!"
                    description = getMessage(message.author!!, message, message.channel.asChannel(), matches)
                }
            }
        }
    }

    private suspend fun getMessage(user: User, message: Message, channel: Channel, matches: Set<String>): String {
        val channelMessage = if (channel.type == ChannelType.GuildText) {
            "in ${channel.mention}"
        } else {
            "in a DM"
        }

        val matchesString = matches.joinToString(", ") { "`$it`" }

        val jumpMessage = if (channel.type == ChannelType.GuildText) {
            "[the following message](${message.getUrl()})"
        } else {
            "the following message"
        }

        return "Regex filter triggered by " +
                "${user.mention} (`${user.id.value}`) $channelMessage, " +
                "with $jumpMessage (${matches.size} matches):\n\n" +
                "${message.content}\n\n" +
                "**Matches:** $matchesString"
    }

    private fun loadRegexes(): List<Regex> {
        val resource = RegexFilter::class.java.getResource("/regex/regexFilter.regex")

        return resource.readText()
                .split("\r\n", "\n")
                .filter { it.isNotEmpty() && it.startsWith("#").not() && !it.matches(flagRegex) }
                .map { it.toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS)) }
    }
}
