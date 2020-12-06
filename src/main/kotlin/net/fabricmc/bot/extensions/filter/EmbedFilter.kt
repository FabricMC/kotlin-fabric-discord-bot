package net.fabricmc.bot.extensions.filter

import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.optional.Optional
import dev.kord.core.entity.Message
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.Channel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageUpdateEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.getUrl
import net.fabricmc.bot.utils.readable

/**
 * Filter class intended for finding removing messages, and alerting staff when selfbots and
 * modded clients post embeds.
 */
class EmbedFilter(bot: ExtensibleBot) : Filter(bot) {
    override val concerns = arrayOf(FilterConcerns.EMBEDS)

    override suspend fun checkCreate(event: MessageCreateEvent, content: String): Boolean {
        doCheck(event.message)

        return true  // We aren't removing the message, so let processing continue
    }

    override suspend fun checkEdit(event: MessageUpdateEvent, content: String): Boolean {
        doCheck(event.getMessage())

        return true  // We aren't removing the message, so let processing continue
    }

    private suspend fun doCheck(message: Message) {
        val embeds = message.embeds.filter { it.provider == null && it.video == null && it.url == null }

        if (embeds.isNotEmpty()) {
            sendAlert {
                this.content = getMessage(message.author!!.asUser(), message, message.channel.asChannel(), embeds.size)
            }
        }

        for (messageEmbed in embeds) {
            sendAlert(false) {
                embed {
                    if (messageEmbed.color != null) color = messageEmbed.color
                    if (messageEmbed.description != null) description = messageEmbed.description
                    if (messageEmbed.data.image is Optional.Value) image = messageEmbed.data.image.value?.url?.value
                    if (messageEmbed.timestamp != null) timestamp = messageEmbed.timestamp
                    if (messageEmbed.title != null) title = messageEmbed.title
                    if (messageEmbed.url != null) url = messageEmbed.url

                    if (messageEmbed.author != null) {
                        author {
                            if (messageEmbed.author!!.iconUrl != null) {
                                icon = messageEmbed.author!!.iconUrl
                            }

                            if (messageEmbed.author!!.name != null) {
                                name = messageEmbed.author!!.name
                            }

                            if (messageEmbed.author!!.url != null) {
                                url = messageEmbed.author!!.url
                            }
                        }
                    }

                    for (messageField in messageEmbed.fields) {
                        field { name = messageField.name; value = messageField.value }
                    }

                    if (messageEmbed.footer != null) {
                        footer {
                            text = messageEmbed.footer!!.text

                            if (messageEmbed.footer!!.iconUrl !== null) icon = messageEmbed.footer!!.iconUrl
                        }
                    }

                    if (messageEmbed.thumbnail != null && messageEmbed.thumbnail!!.url != null) {
                        thumbnail {
                            url = messageEmbed.thumbnail!!.url!!
                        }
                    }
                }
            }
        }
    }

    private suspend fun getMessage(user: User, message: Message, channel: Channel, count: Int): String {
        val channelMessage = if (channel.type == ChannelType.GuildText) {
            "in ${channel.mention}"
        } else {
            "in a DM"
        }

        val jumpMessage = if (channel.type == ChannelType.GuildText) {
            "[$count suspicious embed/s posted](${message.getUrl()})"
        } else {
            "$count suspicious embed/s posted"
        }

        return "$jumpMessage by ${user.readable()} $channelMessage:"
    }
}
