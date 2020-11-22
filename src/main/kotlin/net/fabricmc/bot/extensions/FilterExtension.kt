package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.event.message.MessageCreateEvent
import com.gitlab.kordlib.core.event.message.MessageUpdateEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleLower
import com.kotlindiscord.kord.extensions.extensions.Extension
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.extensions.filter.*
import net.fabricmc.bot.isNotIgnoredChannel

private val logger = KotlinLogging.logger {}

/**
 * Filter extension, for filtering messages and alerting staff based on message content.
 *
 * This is a relatively complex extension, which is concerned with a lot of potential
 * edge cases and tricky filtering. In order to lower the cognitive load, filtering
 * behaviour is split up into a bunch of [Filter] classes that work independently
 * of the rest of this system.
 */
class FilterExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name: String = "filter"

    private val filters: Array<Filter> = arrayOf(
//        AttachmentFilter(bot),  // We can re-add this if we need it
            UrlFilter(bot),

            // Non-actioning filters come last, in case a message was already removed.
            EmbedFilter(bot),
            RegexFilter(bot)
    )

    /**
     * Sanitize message content for filtering.
     *
     * This currently removes most Markdown formatting.
     *
     * @param content Message content to sanitize.
     * @return Sanitized message content.
     */
    fun sanitizeMessage(content: String): String =
            content.replace("|", "")
                    .replace("\\", "")
                    .replace("*", "")
                    .replace("_", "")
                    .replace("`", "")

    override suspend fun setup() {
        event<MessageCreateEvent> {
            check(
                    ::defaultCheck,
                    ::isNotIgnoredChannel,
                    topRoleLower(config.getRole(Roles.MODERATOR))
            )

            action {
                with(it) {
                    for (filter in filters) {
                        var matchedConcerns = false

                        if (filter.concerns.contains(FilterConcerns.CONTENT) &&
                                this.message.content.isNotEmpty()
                        ) matchedConcerns = true

                        if (filter.concerns.contains(FilterConcerns.EMBEDS) &&
                                this.message.embeds.isNotEmpty()
                        ) matchedConcerns = true

                        if (filter.concerns.contains(FilterConcerns.ATTACHMENTS) &&
                                this.message.attachments.isNotEmpty()
                        ) matchedConcerns = true

                        if (!matchedConcerns) continue

                        @Suppress("TooGenericExceptionCaught")  // Anything could happen here.
                        try {
                            if (!filter.checkCreate(this, sanitizeMessage(message.content))) break
                        } catch (e: Exception) {
                            logger.catching(e)
                        }
                    }
                }
            }
        }

        event<MessageUpdateEvent> {
            check(
                    ::defaultCheck,
                    topRoleLower(config.getRole(Roles.MODERATOR))
            )

            action {
                with(it) {
                    for (filter in filters) {
                        var matchedConcerns = false

                        if (filter.concerns.contains(FilterConcerns.CONTENT) &&
                                getMessage().content.isNotEmpty()
                        ) matchedConcerns = true

                        if (filter.concerns.contains(FilterConcerns.EMBEDS) &&
                                getMessage().embeds.isNotEmpty()
                        ) matchedConcerns = true

                        if (filter.concerns.contains(FilterConcerns.ATTACHMENTS) &&
                                getMessage().attachments.isNotEmpty()
                        ) matchedConcerns = true

                        if (!matchedConcerns) continue

                        @Suppress("TooGenericExceptionCaught")  // Anything could happen here.
                        try {
                            if (!filter.checkEdit(this, sanitizeMessage(new.content.value ?: ""))) break
                        } catch (e: Exception) {
                            logger.catching(e)
                        }
                    }
                }
            }
        }
    }
}
