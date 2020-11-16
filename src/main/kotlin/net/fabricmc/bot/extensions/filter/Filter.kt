package net.fabricmc.bot.extensions.filter

import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.channel.GuildMessageChannel
import com.gitlab.kordlib.core.event.message.MessageCreateEvent
import com.gitlab.kordlib.core.event.message.MessageUpdateEvent
import com.gitlab.kordlib.rest.builder.message.MessageCreateBuilder
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.deleteWithDelay
import com.kotlindiscord.kord.extensions.utils.dm
import com.kotlindiscord.kord.extensions.utils.respond
import net.fabricmc.bot.utils.alertMessage

/** How long to wait before removing notification messages in channels - 10 seconds. **/
const val DELETE_DELAY = 10_000L

/**
 * Class representing a single type of message filter.
 *
 * Subclasses are expected to implement exactly one type of message filter, and they must implement
 * the [checkCreate] function. This function should check whether a message is problematic, action it
 * appropriately, and return `false` if processing should stop here, or `true` if other filters
 * should be checked.
 *
 * If your filter doesn't delete or otherwise action the message (that is to say, it's only for alerting),
 * then it may be best to return `true` so the rest of the filters can be executed. Whether this is
 * truly the case will depend on what exactly your filter is doing, though.
 *
 * @param bot Current bot instance
 */
abstract class Filter(val bot: ExtensibleBot) {
    /** Message appended to notification reasons sent to users. **/
    open val mistakeMessage = "If you feel that this was a mistake, please feel free to contact a member of staff."

    /**
     * An array of [FilterConcerns] - filters won't be executed if their concerns aren't met.
     *
     * This means that we don't waste time executing filters when the data they filter against isn't present.
     **/
    abstract val concerns: Array<FilterConcerns>

    /**
     * Check whether a message should be actioned, action it, and return a value based on
     * whether we should continue processing filters.
     *
     * This function is only executed on message creation.
     *
     * @param event The event containing the message to be checked against
     * @param content Sanitized message content (eg, with spoilers removed)
     *
     * @return `false` if processing should stop here, `true` if other filters should be checked
     */
    abstract suspend fun checkCreate(event: MessageCreateEvent, content: String): Boolean

    /**
     * Check whether a message should be actioned, action it, and return a value based on
     * whether we should continue processing filters.
     *
     * This function is only executed on message edits.
     *
     * @param event The event containing the message to be checked against
     * @param content Sanitized message content (eg, with spoilers removed)
     *
     * @return `false` if processing should stop here, `true` if other filters should be checked
     */
    abstract suspend fun checkEdit(event: MessageUpdateEvent, content: String): Boolean

    /**
     * Send an alert to the alerts channel.
     *
     * This function works just like the [GuildMessageChannel.createMessage] function.
     */
    suspend fun sendAlert(mention: Boolean = true, builder: suspend MessageCreateBuilder.() -> Unit): Message =
            alertMessage {
                builder()

                if (mention) {
                    content = if (content == null) {
                        "@here"
                    } else {
                        "@here $content"
                    }
                }
            }

    /**
     * Send a notification to a user - attempting to DM first, and then using a channel.
     *
     * @param eventMessage Message object from the event corresponding with this filtering attempt
     * @param reason Human-readable reason to send to the user
     */
    suspend fun sendNotification(eventMessage: Message, reason: String): Message {
        val message = "$reason\n\n$mistakeMessage"
        var sent = eventMessage.author!!.dm(message)

        if (sent == null) { // DMs disabled
            sent = eventMessage.respond(message)
            sent.deleteWithDelay(DELETE_DELAY)
        }
        return sent
    }
}
