package net.fabricmc.bot.utils

import com.gitlab.kordlib.core.behavior.channel.createMessage
import com.gitlab.kordlib.core.behavior.execute
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.channel.GuildMessageChannel
import com.gitlab.kordlib.rest.builder.message.EmbedBuilder
import com.gitlab.kordlib.rest.builder.message.MentionTypes
import com.gitlab.kordlib.rest.builder.message.MessageCreateBuilder
import kotlinx.coroutines.delay
import mu.KotlinLogging
import net.fabricmc.bot.bot
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.ensureWebhook
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.extensions.ActionLogExtension
import java.time.Instant

private const val ACTION_LOG_DELAY = 1000L * 5L  // 5 seconds
private const val ACTION_LOG_ATTEMPTS = 12  // For a total of 60 seconds
private val actionLogExtension by lazy { bot.extensions["action log"] as ActionLogExtension }

/**
 * Convenience function to send an embed to the current action log channel.
 *
 * Embeds will have a timestamp applied automatically.
 *
 * @param body Lambda for building the embed.
 */
suspend fun actionLog(body: suspend EmbedBuilder.() -> Unit): Message {
    val logger = KotlinLogging.logger {}
    var attempts = 0

    while (!actionLogExtension.hasChannel) {
        if (attempts > ACTION_LOG_ATTEMPTS) {
            error("Failed to send action log message; action log category still processing after 60 seconds.")
        }

        logger.info { "Holding back action log message; action log category still processing" }
        delay(ACTION_LOG_DELAY)

        attempts += 1
    }

    val channel = actionLogExtension.channel
    val builder = EmbedBuilder()

    body.invoke(builder)

    builder.timestamp = Instant.now()

    val webhook = ensureWebhook(channel)

    return webhook.execute(webhook.token!!) {
        embeds += builder.toRequest()
    }
}

/**
 * Convenience function to send an embed to the moderator log channel.
 *
 * Embeds will have a timestamp applied automatically.
 *
 * @param body Lambda for building the embed.
 */
suspend fun modLog(body: suspend EmbedBuilder.() -> Unit): Message {
    val builder = EmbedBuilder()

    body.invoke(builder)
    builder.timestamp = Instant.now()

    val webhook = ensureWebhook(Channels.MODERATOR_LOG)

    return webhook.execute(webhook.token!!) {
        embeds += builder.toRequest()
    }
}

/**
 * Convenience function to send an embed to the alerts channel.
 *
 * Embeds will have a timestamp applied automatically.
 *
 * @param mention Whether to send a @here mention with the embed.
 * @param body Lambda for building the embed.
 */
suspend fun alert(mention: Boolean = true, body: suspend EmbedBuilder.() -> Unit): Message {
    val builder = EmbedBuilder()
    val channel = config.getChannel(Channels.ALERTS) as GuildMessageChannel

    body.invoke(builder)
    builder.timestamp = Instant.now()

    return channel.createMessage {
        if (mention) {
            content = "@here"
        }

        embed = builder

        allowedMentions {
            types += MentionTypes.Everyone
        }
    }
}

/**
 * Convenience function to send a message to the alerts channel.
 *
 * Embeds will have a timestamp applied automatically.
 *
 * @param body Lambda for building the embed.
 */
suspend fun alertMessage(body: suspend MessageCreateBuilder.() -> Unit): Message {
    val channel = config.getChannel(Channels.ALERTS) as GuildMessageChannel

    return channel.createMessage {
        body()

        allowedMentions { types += MentionTypes.Everyone }
        embed?.timestamp = Instant.now()
    }
}
