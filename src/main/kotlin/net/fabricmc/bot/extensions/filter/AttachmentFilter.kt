package net.fabricmc.bot.extensions.filter

import com.gitlab.kordlib.core.behavior.channel.createMessage
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.event.message.MessageCreateEvent
import com.gitlab.kordlib.core.event.message.MessageUpdateEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.deleteIgnoringNotFound

// TODO: Think about allowing undisplayable extensions in specific channels

private val whitelist = setOf(
        // These can all be displayed directly on Discord
        "3g2", "3gp", "avi", "bmp", "flac", "gif", "h264", "jpeg", "jpg", "m4v",
        "mkv", "mov", "mp3", "mp4", "mpeg", "mpg", "ogg", "png", "svg", "tiff",
        "wav", "wmv",

        // Undisplayable, but common filetypes posted on the server
        "jar",  // Java Archive
        "log",  // Text log file
        "txt"   // Text file
).sorted()

private val whitelistString = whitelist.joinToString(", ") { "`$it`" }

/**
 * Filter class intended for finding and removing messages, and alerting staff when banned attachment
 * types are uploaded.
 *
 * We mostly filter files based on extensions that Discord can't preview natively, but we also allow
 * a few other formats in the interest of making branding work possible on Discord.
 *
 * This class is *heavily* inspired by the work done by the fine folks at Python Discord.
 * You can find their bot code here: https://github.com/python-discord/bot
 */
class AttachmentFilter(bot: ExtensibleBot) : Filter(bot) {
    override val concerns = arrayOf(FilterConcerns.ATTACHMENTS)

    override suspend fun checkCreate(event: MessageCreateEvent, content: String): Boolean = doCheck(event.message)
    override suspend fun checkEdit(event: MessageUpdateEvent, content: String): Boolean = doCheck(event.getMessage())

    private suspend fun doCheck(message: Message): Boolean {
        val attachmentExtensions = message.attachments.map { it.filename.split(".").last() }
        val forbiddenExtensions: MutableList<String> = mutableListOf()

        for (extension in attachmentExtensions) {
            if (extension !in whitelist) {
                forbiddenExtensions += extension
            }
        }

        val forbidden = forbiddenExtensions.isNotEmpty()

        if (forbidden) {
            message.deleteIgnoringNotFound()

            val forbiddenExtensionsString = forbiddenExtensions.joinToString(", ") { "`$it`" }

            message.channel.createMessage {
                content = message.getAuthorAsMember()!!.mention

                embed {
                    title = "Disallowed attachment"

                    description = "It looks like you've uploaded one or more files with file extensions " +
                            "that Discord cannot display ($forbiddenExtensionsString).\n\n" +
                            "If you're uploading code or other text files, please use " +
                            "[Hastebin](https://hastebin.com/) or another pastebin service.\n\n" +
                            "We allow attachments with the following extensions: $whitelistString"
                }
            }
        }

        return forbidden.not()
    }
}
