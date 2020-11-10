package net.fabricmc.bot

import com.gitlab.kordlib.core.entity.channel.DmChannel
import com.gitlab.kordlib.core.event.Event
import com.kotlindiscord.kord.extensions.checks.*
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.enums.Roles

/**
 * Default check we do for almost every event and command, message creation flavour.
 *
 * Ensures:
 * * That the message was sent to the configured primary guild
 * * That we didn't send the message
 * * That another bot/webhook didn't send the message
 *
 * @param event The event to run this check against.
 */
suspend fun defaultCheck(event: Event): Boolean {
    val logger = KotlinLogging.logger {}

    val message = messageFor(event)?.asMessage()

    return when {
        message == null -> {
            logger.debug { "Failing check: Message for event $event is null. This type of event may not be supported." }
            false
        }

        message.author == null -> {
            logger.debug { "Failing check: Message sent by a webhook or system message" }
            false
        }

        message.author!!.id == bot.kord.getSelf().id -> {
            logger.debug { "Failing check: We sent this message" }
            false
        }

        message.author!!.isBot == true -> {
            logger.debug { "Failing check: This message was sent by another bot" }
            false
        }

        message.getChannelOrNull() is DmChannel -> {
            logger.debug { "Passing check: This message was sent in a DM" }
            true
        }

        message.getGuildOrNull()?.id != config.getGuild().id -> {
            logger.debug { "Failing check: Not in the correct guild" }
            false
        }

        else -> {
            logger.debug { "Passing check" }
            true
        }
    }
}

/**
 * Check to ensure an event happened within the bot commands channel.
 *
 * @param event The event to run this check against.
 */
suspend fun inBotChannel(event: Event): Boolean {
    val logger = KotlinLogging.logger {}

    val channel = channelFor(event)

    return when {
        channel == null -> {
            logger.debug { "Failing check: Channel is null" }
            false
        }

        channel.id != config.getChannel(Channels.BOT_COMMANDS).id -> {
            logger.debug { "Failing check: Not in bot commands" }
            false
        }

        else -> {
            logger.debug { "Passing check" }
            true
        }
    }
}

/**
 * Check that checks that the user is at least a moderator, or that the event
 * happened in the bot commands channel.
 */
suspend fun botChannelOrTraineeModerator(event: Event): Boolean = or(
        ::inBotChannel,
        topRoleHigherOrEqual(config.getRole(Roles.TRAINEE_MODERATOR))
)(event)

/**
 * Check that ensures an event wasn't fired by a bot. If an event doesn't
 * concern a specific user, then this check will pass.
 */
suspend fun isNotBot(event: Event): Boolean {
    val logger = KotlinLogging.logger {}

    val user = userFor(event)

    return when {
        user == null -> {
            logger.debug { "Passing check: User for event $event is null." }
            true
        }

        user.asUser().isBot == true -> {
            logger.debug { "Failing check: User $user is a bot." }
            false
        }

        else -> {
            logger.debug { "Passing check." }
            true
        }
    }
}

/**
 * Check that ensures an event didn't happen in an ignored channel.
 *
 * This check will pass if the event isn't one that is channel-relevant.
 */
suspend fun isNotIgnoredChannel(event: Event): Boolean {
    val logger = KotlinLogging.logger {}
    val channelId = channelIdFor(event)

    if (channelId == null) {
        logger.debug { "Passing check: Event is not channel-relevant." }
        return true
    }

    return if (channelId !in config.ignoredChannels) {
        logger.debug { "Passing check: Event is not in an ignored channel." }

        true
    } else {
        logger.debug { "Failing check: Event is in an ignored channel." }

        false
    }
}
