package net.fabricmc.bot.extensions

import com.gitlab.kordlib.cache.api.query
import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.cache.data.MessageData
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.core.entity.channel.Channel
import com.gitlab.kordlib.core.entity.channel.GuildMessageChannel
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import mu.KotlinLogging
import net.fabricmc.bot.*
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.utils.modLog
import net.fabricmc.bot.utils.requireGuildChannel
import net.fabricmc.bot.utils.respond
import java.time.Instant

/** Maximum number of deleted messages allowed without the force flag. **/
private const val MAX_DELETION_SIZE = 50

/** Milliseconds offset of the since parameter to make sure to delete the command invocation. **/
private const val SINCE_OFFSET = 100L

private val logger = KotlinLogging.logger {}

private const val HELP =
        "Bulk-delete messages that match a set of filters - up to 200 messages, with a soft limit of " +
                "$MAX_DELETION_SIZE  messages.\n\n" +

                "Please note that Discord limits bulk deletion of messages; you can only bulk delete messages that " +
                "are less than two weeks old, and only up to 200 at a time. Additionally, this command currently " +
                "only operates on messages that are in the bot's message cache.\n\n" +

                "__**Filters**__\n" +
                "Filters may be combined in any order, and are specified as `key=value` pairs. For example, " +
                "`count=25` will apply a limit of 25 deleted messages.\n\n" +

                "**Note:** The `in` and `since` filters are exclusive, and may not be combined.\n\n" +

                "__**Multiple-target filters**__\n" +
                "The following filters may be specified multiple times, and can be used to broaden a search. For " +
                "example, you can combine two `user` filters in order to filter for messages posted by either " +
                "user.\n\n" +

                "**Channel:** `in`, matches messages in a specific channel only.\n" +
                "**Regex:** `regex`, matches message content against a regular expression.\n" +
                "**User:** `user`, matches only messages posted by a particular user.\n\n" +

                "__**Single-target filters**__\n" +
                "The following filters may only be specified once.\n\n" +

                "**Bot Only:** `botOnly`, specify `true` to match only messages sent by bots.\n" +

                "**Count:** `count`, the maximum number of messages to clean. Omit for no limit. If multiple " +
                "channels are specified using the `in` filter, this limit is per-channel.\n" +

                "**Since:** `since`, specify the earliest message to clean up, messages between this and the latest " +
                "matched one will be removed.\n\n" +

                "**__Additional options__**\n" +
                "**Dry-run:** `dryRun`, specify `true` to get a total count of messages that would be deleted, " +
                "instead of actually deleting them.\n" +
                "**Force:** `force`, specify `true` to override the $MAX_DELETION_SIZE messages soft limit. Only " +
                "available to admins."


/**
 * Arguments for the clean command.
 */
@Suppress("ConstructorParameterNaming", "UndocumentedPublicProperty")
data class CleanArguments(
        val user: List<User>? = null,
        val regex: List<Regex>? = null,
        val `in`: List<Channel>? = null,
        val since: Message? = null,
        val botOnly: Boolean = false,
        val count: Int = -1,
        val force: Boolean = false,
        val dryRun: Boolean = false
)

/**
 * Extension providing a bulk message deletion command for mods+.
 *
 * This extension was written by Akarys for Kotlin Discord originally. We've
 * modified it here to suit our community better.
 */
class CleanExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "clean"

    override suspend fun setup() {

        command {
            name = "clean"
            description = HELP

            aliases = arrayOf("clear", "c")

            check(::defaultCheck)
            check(topRoleHigherOrEqual(config.getRole(Roles.MODERATOR)))
            signature = "<filter> [filter ...] [dryRun=false] [force=false]"

            hidden = true

            action {
                if (!message.requireGuildChannel(null)) {
                    return@action
                }

                if (args.isEmpty()) {
                    message.channel.createMessage(
                            ":x: Please provide at least one filter"
                    )

                    return@action
                }
                with(parse<CleanArguments>()) {
                    val cleanNotice = """
                        Cleaning with :
                        Users: ${user?.joinToString(", ") { "${it.username}#${it.discriminator}" }}
                        Regex: ${regex?.joinToString(", ")}
                        Channels: ${
                        `in`?.joinToString(", ") {
                            it.id.longValue.toString()
                        } ?: message.channelId.longValue
                    }
                        Since: ${since?.id?.longValue}
                        Bot-only: $botOnly
                        Count: $count
                        Force: $force
                        """.trimIndent()

                    val channels = when {
                        since != null -> {
                            if (!`in`.isNullOrEmpty()) {
                                message.channel.createMessage(":x: Cannot use the `in` and `since` options together")
                                return@action
                            }

                            listOf(since.channelId.longValue)
                        }

                        `in`.isNullOrEmpty() -> listOf(message.channelId.longValue)
                        else -> `in`.map { it.id.longValue }
                    }

                    val userIds = user?.map { it.id.longValue }
                    val sinceTimestamp = since?.timestamp?.minusMillis(SINCE_OFFSET)

                    logger.debug { cleanNotice }

                    var removalCount = 0

                    for (channelId in channels) {
                        var query = bot.kord.cache.query<MessageData> {
                            MessageData::channelId eq channelId

                            if (!userIds.isNullOrEmpty()) {
                                run {
                                    MessageData::authorId `in` userIds
                                }
                            }

                            if (botOnly) {
                                run {
                                    MessageData::authorIsBot eq true
                                }
                            }

                            if (!regex.isNullOrEmpty()) {
                                run {
                                    MessageData::content predicate {
                                        regex.all { regex ->
                                            regex.matches(it)
                                        }
                                    }
                                }
                            }

                            if (sinceTimestamp != null) {
                                run {
                                    MessageData::timestamp predicate {
                                        Instant.parse(it).isAfter(sinceTimestamp)
                                    }
                                }
                            }
                        }.toCollection()

                        if (count > 0) {
                            query = query.sortedBy { Instant.parse(it.timestamp) }.reversed().slice(0..count)
                        }

                        if (query.size > MAX_DELETION_SIZE && !dryRun) {
                            if (message.getAuthorAsMember()?.hasRole(config.getRole(Roles.ADMIN)) == false) {
                                message.channel.createMessage(
                                        ":x: Cannot delete more than $MAX_DELETION_SIZE, " +
                                                "please ask an admin to run this command with the `force:true` flag."
                                )
                                return@action
                            } else {
                                if (!force) {
                                    message.channel.createMessage(
                                            ":x: Cannot delete more than $MAX_DELETION_SIZE, " +
                                                    "run this command with the `force:true` flag to force it."
                                    )
                                    return@action
                                }
                            }
                        }

                        val cleanCount = "Messages to clean: ${query.joinToString(", ") { it.id.toString() }}"

                        logger.debug { cleanCount }
                        // TODO: Log the cleanNotice and cleanCount to #moderator-log

                        val channel = bot.kord.getChannel(Snowflake(channelId))

                        if (channel is GuildMessageChannel) {
                            if (!dryRun) {
                                channel.bulkDelete(query.map { Snowflake(it.id) })
                            }

                            removalCount += query.size
                        } else {
                            logger.warn { "Error retrieving channel $channelId : $channel" }
                        }
                    }

                    if (dryRun) {
                        message.respond(
                                "**Dry-run:** $removalCount messages would have " +
                                        "been cleaned."
                        )
                    } else {
                        sendToModLog(this, message, removalCount)
                    }
                }
            }
        }
    }

    private suspend fun sendToModLog(args: CleanArguments, message: Message, total: Int) {
        val author = message.author!!
        val channel = message.channel

        modLog {
            color = Colours.BLURPLE
            title = "Clean command summary"

            description = "Clean command executed by ${author.mention} in ${channel.mention}."

            field {
                name = "Bot-only"
                inline = true

                value = if (args.botOnly) {
                    "Yes"
                } else {
                    "No"
                }
            }

            if (args.`in` != null && args.`in`.isNotEmpty()) {
                field {
                    name = "Channels"
                    inline = true

                    value = args.`in`.joinToString(", ") {
                        "${it.mention} (`${it.id.longValue}`)"
                    }
                }
            }

            field {
                name = "Count"
                inline = true

                value = if (args.count >= 0) {
                    args.count.toString()
                } else {
                    "No limit"
                }
            }

            field {
                name = "Force"
                inline = true

                value = if (args.force) {
                    "Yes"
                } else {
                    "No"
                }
            }

            if (args.regex != null && args.regex.isNotEmpty()) {
                field {
                    name = "Regex"
                    inline = true

                    value = args.regex.joinToString(", ") { "`$it`" }
                }
            }

            if (args.since != null) {
                field {
                    name = "Since"
                    inline = true

                    value = args.since.getUrl()
                }
            }

            field {
                name = "Total Removed"
                inline = true

                value = total.toString()
            }

            if (args.user != null && args.user.isNotEmpty()) {
                field {
                    name = "Users"
                    inline = true

                    value = args.user.joinToString(", ") {
                        "${it.mention} (${it.tag} / ${it.id.longValue})"
                    }
                }
            }
        }
    }
}
