package net.fabricmc.bot.utils

import com.gitlab.kordlib.core.behavior.channel.createMessage
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.rest.builder.message.MessageCreateBuilder

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
    val mention = if (this.author != null) {
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
