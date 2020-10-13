package net.fabricmc.bot.utils

import com.gitlab.kordlib.core.behavior.channel.createMessage
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.rest.builder.message.MessageCreateBuilder
import com.gitlab.kordlib.rest.request.RestRequestException
import io.ktor.http.HttpStatusCode

/**
 * Send a private message to a user, if they have their DMs enabled.
 *
 * @param builder Builder lambda for populating the message fields.
 * @return The sent message, or `null` if the user has their DMs disabled.
 */
suspend fun User.dm(builder: MessageCreateBuilder.() -> Unit): Message? {
    val channel = try {
        this.getDmChannel()
    } catch (e: RestRequestException) {
        if (e.code == HttpStatusCode.Forbidden.value) {
            // They have DMs disabled
            return null
        } else {
            throw e
        }
    }

    return channel.createMessage { builder() }
}

/**
 * Send a private message to a user, if they have their DMs enabled.
 *
 * @param content Message content.
 * @return The sent message, or `null` if the user has their DMs disabled.
 */
suspend fun User.dm(content: String) = this.dm { this.content = content }
