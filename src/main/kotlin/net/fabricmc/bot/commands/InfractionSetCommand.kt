package net.fabricmc.bot.commands

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.core.event.message.MessageCreateEvent
import com.gitlab.kordlib.rest.request.RestRequestException
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.commands.Command
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import mu.KotlinLogging
import net.fabricmc.bot.bot
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.InfractionTypes
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.runSuspended
import net.time4j.Duration
import net.time4j.IsoUnit
import net.time4j.PlainTimestamp
import net.time4j.format.expert.ChronoFormatter
import net.time4j.format.expert.PatternType
import java.util.*

/** Data class representing the arguments for an infraction type that doesn't expire.
 *
 * @param member The member to infract.
 * @param memberLong The ID of the member to infract, if they're not on the server.
 * @param reason The reason for the infraction.
 */
data class NonExpiringCommandArgs(
        val member: User? = null,
        val memberLong: Long? = null,
        val reason: List<String>
)

/** Data class representing the arguments for an infraction type that expires.
 *
 * @param member The member to infract.
 * @param memberLong The ID of the member to infract, if they're not on the server.
 * @param duration How long to infract the user for.
 * @param reason The reason for the infraction.
 */
data class ExpiringCommandArgs(
        val member: User? = null,
        val memberLong: Long? = null,
        val duration: Duration<IsoUnit> = Duration.ofZero(),
        val reason: List<String>
)

private val timeFormatter = ChronoFormatter.ofTimestampPattern(
        "dd/MM/yyyy 'at' HH:mm", PatternType.CLDR_24, Locale.ENGLISH
)

private val mySqlTimeFormatter = ChronoFormatter.ofTimestampPattern(
        "yyyy-MM-dd HH:mm:ss", PatternType.CLDR_24, Locale.ENGLISH
)

private val logger = KotlinLogging.logger {}

/**
 * A command type for applying an infraction to a user.
 *
 * This command just handles the database work and notification, you'll still need to apply an [infrAction] to
 * apply the infraction on Discord.
 *
 * @param type The type of infraction to apply.
 * @param commandDescription The description to use for this command.
 * @param commandName The name of this command.
 * @param infrAction How to apply the infraction to the user.
 */
class InfractionSetCommand(extension: Extension, private val type: InfractionTypes,
                           private val commandDescription: String,
                           private val commandName: String,
                           private val infrAction: suspend CommandContext.(targetId: Snowflake, reason: String) -> Unit
) : Command(extension) {
    private val queries = config.db.infractionQueries

    private val commandBody: suspend CommandContext.() -> Unit = body@{
        if (type.expires) {
            val args = parse<ExpiringCommandArgs>()

            if (args.member != null && args.memberLong != null) {
                message.channel.createMessage(
                        "${message.author!!.mention} Please specify exactly one user argument, not two."
                )

                return@body
            }

            if (args.member == null && args.memberLong == null) {
                message.channel.createMessage(
                        "${message.author!!.mention} Please specify a user to apply this infraction to."
                )

                return@body
            }

            val memberId = args.member?.id?.longValue ?: args.memberLong!!

            if (type.requirePresent && config.getGuild().getMemberOrNull(Snowflake(memberId)) == null) {
                message.channel.createMessage(
                        "${message.author!!.mention} The specified user is not present on the server."
                )

                return@body
            }

            val reason = args.reason.joinToString(", ")

            val now = PlainTimestamp.nowInSystemTime()
            val expires = if (args.duration != Duration.ofZero<IsoUnit>()) {
                now.plus(args.duration)
            } else {
                now.plus(args.duration)
            }

            val infraction = runSuspended {
                queries.addInfraction(
                        reason, message.author!!.id.longValue, memberId, expires?.print(mySqlTimeFormatter),
                        true, type
                )

                queries.getLastInfraction().executeAsOne()
            }

            val messageText = if (expires != null) {
                "<@!${infraction.target_id}> has been " +
                        "${type.actionText} until ${formatTimestamp(expires)}.\n\n" +
                        "Reason: ${infraction.reason}"
            } else {
                "<@!${infraction.target_id}> has been permanently" +
                        "${type.actionText}.\n\n" +
                        "Reason: ${infraction.reason}"
            }

            if (type.relay) {
                try {
                    val targetObj = bot.kord.getUser(Snowflake(memberId))

                    targetObj?.getDmChannel()?.createEmbed {
                        color = Colours.NEGATIVE
                        title = type.actionText.capitalize() + "!"

                        description = if (expires != null) {
                            "You have been " +
                                    "${type.actionText} until ${formatTimestamp(expires)}.\n\n" +
                                    "Reason: ${infraction.reason}"
                        } else {
                            "You have been permanently" +
                                    "${type.actionText}.\n\n" +
                                    "Reason: ${infraction.reason}"
                        }

                        footer {
                            text = "Infraction ID: ${infraction.target_id}"
                        }
                    }
                } catch (e: RestRequestException) {
                    logger.debug(e) { "Unable to DM user with ID $memberId, they're probably not in the guild." }
                }
            }

            infrAction.invoke(this, Snowflake(memberId), reason)

            message.channel.createEmbed {
                color = Colours.POSITIVE
                title = "Infraction created: ${infraction.id}"

                description = messageText

                footer {
                    text = "User ID: ${infraction.target_id}"
                }
            }
        } else {
            val args = parse<NonExpiringCommandArgs>()

            if (args.member != null && args.memberLong != null) {
                message.channel.createMessage(
                        "${message.author!!.mention} Please specify exactly one user argument, not two."
                )

                return@body
            }

            if (args.member == null && args.memberLong == null) {
                message.channel.createMessage(
                        "${message.author!!.mention} Please specify a user to apply this infraction to."
                )

                return@body
            }

            val memberId = args.member?.id?.longValue ?: args.memberLong!!

            if (type.requirePresent && config.getGuild().getMemberOrNull(Snowflake(memberId)) == null) {
                message.channel.createMessage(
                        "${message.author!!.mention} The specified user is not present on the server."
                )

                return@body
            }

            val reason = args.reason.joinToString(", ")

            val infraction = runSuspended {
                queries.addInfraction(reason, message.author!!.id.longValue, memberId, null, true, type)
                queries.getLastInfraction().executeAsOne()
            }

            val messageText = "<@!${infraction.target_id}> has been." +
                    "\n\n" +
                    "Reason: ${infraction.reason}"

            if (type.relay) {
                try {
                    val targetObj = bot.kord.getUser(Snowflake(memberId))

                    targetObj?.getDmChannel()?.createEmbed {
                        color = Colours.NEGATIVE
                        title = type.actionText.capitalize() + "!"

                        description = "You have been " +
                                "${type.actionText}.\n\n" +
                                "Reason: ${infraction.reason}"

                        footer {
                            text = "Infraction ID: ${infraction.target_id}"
                        }
                    }
                } catch (e: RestRequestException) {
                    logger.debug(e) { "Unable to DM user with ID $memberId, they're probably not in the guild." }
                }
            }

            infrAction.invoke(this, Snowflake(memberId), reason)

            message.channel.createEmbed {
                color = Colours.POSITIVE
                title = "Infraction created: ${infraction.id}"

                description = messageText

                footer {
                    text = "User ID: ${infraction.target_id}"
                }
            }
        }
    }

    private fun formatTimestamp(ts: PlainTimestamp): String = ts.print(timeFormatter)

    override val checkList: MutableList<suspend (MessageCreateEvent) -> Boolean> = mutableListOf(
            ::defaultCheck,
            { topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))(it) }  // Gotta be suspended
    )

    init {
        this.name = commandName
        this.description = commandDescription

        signature = if (type.expires) {
            "<user/id> <duration> <reason...>"
        } else {
            "<user/id> <reason...>"
        }

        action(commandBody)
    }
}
