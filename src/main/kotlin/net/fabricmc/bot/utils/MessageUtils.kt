package net.fabricmc.bot.utils

import com.gitlab.kordlib.core.behavior.channel.createMessage
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.channel.DmChannel
import com.gitlab.kordlib.rest.builder.message.MessageCreateBuilder
import com.kotlindiscord.kord.extensions.getTopRole
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.deleteWithDelay
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.enums.Roles

private const val DELETE_DELAY = 1000L * 30L  // 30 seconds

/**
 * Respond to a message in the channel it was sent to, mentioning the author.
 *
 * @param content Message content.
 */
suspend fun Message.respond(content: String): Message = respond { this.content = content }

/**
 * Respond to a message in the channel it was sent to, mentioning the author.
 *
 * @param builder Builder lambda for populating the message fields.
 */
suspend fun Message.respond(builder: MessageCreateBuilder.() -> Unit): Message {
    val mention = if (this.author != null && this.getChannelOrNull() !is DmChannel) {
        "${this.author!!.mention} "
    } else {
        ""
    }

    return channel.createMessage {
        builder()

        allowedMentions {
            if (author != null) {
                users.add(author!!.id)
            }
        }

        content = "$mention$content"
    }
}

/**
 * Check that this message happened in either the bot channel or a DM, or that the author is at least a given role.
 *
 * If none of those things are true, a response message will be created instructing the user to try again in
 * the bot commands channel.
 *
 * @param delay How long (in milliseconds) to wait before deleting the response message
 * @param role Minimum role required to bypass the channel requirement, defaulting to trainee moderator, or null
 * to disallow any role bypass
 * @param allowDm Whether to consider DM channels as an appropriate context
 * @param deleteOriginal Whether to delete the original message as well as the response, using the same given delay
 * (true by default)
 * @return true if the message was posted in an appropriate context, false otherwise
 */
suspend fun Message.requireBotChannel(
        delay: Long = DELETE_DELAY,
        role: Roles? = Roles.TRAINEE_MODERATOR,
        allowDm: Boolean = true,
        deleteOriginal: Boolean = true): Boolean {
    val botCommands = config.getChannel(Channels.BOT_COMMANDS)
    val roleObj = if (role != null) config.getRole(role) else null

    val topRole = if (getGuildOrNull() != null) {
        this.getAuthorAsMember()!!.getTopRole()
    } else {
        null
    }

    @Suppress("UnnecessaryParentheses")  // In this case, it feels more readable
    if (
            (allowDm && this.getChannelOrNull() is DmChannel)
            || (roleObj != null && topRole != null && topRole >= roleObj)
            || this.channelId == botCommands.id
    ) return true

    this.respond(
            "Please use ${botCommands.mention} for this command."
    ).deleteWithDelay(delay)

    if (deleteOriginal && this.getChannelOrNull() !is DmChannel) this.deleteWithDelay(delay)

    return false
}

/**
 * Check that this message happened in a guild channel.
 *
 * If it didn't, a response message will be created instructing the user that the current command can't be used via a
 * private message.
 *
 * @param role Minimum role required to bypass the channel requirement, defaulting to trainee moderator, or null
 * to disallow any role bypass
 * @return true if the message was posted in an appropriate context, false otherwise
 */
suspend fun Message.requireGuildChannel(role: Roles? = Roles.TRAINEE_MODERATOR): Boolean {
    val roleObj = if (role != null) config.getRole(role) else null

    val topRole = if (getGuildOrNull() != null) {
        this.getAuthorAsMember()!!.getTopRole()
    } else {
        null
    }

    @Suppress("UnnecessaryParentheses")  // In this case, it feels more readable
    if (
            (roleObj != null && topRole != null && topRole >= roleObj)
            || this.getChannelOrNull() !is DmChannel
    ) return true

    this.respond(
            "This command is not available via private message."
    )

    return false
}
