package net.fabricmc.bot.extensions.infractions

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.behavior.channel.MessageChannelBehavior
import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.core.entity.channel.TextChannel
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
import net.fabricmc.bot.database.Infraction
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Channels
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
                           private val infrAction: suspend CommandContext.(targetId: Long, reason: String) -> Unit
) : Command(extension) {
    private val queries = config.db.infractionQueries

    private val commandBody: suspend CommandContext.() -> Unit = {
        if (type.expires) {
            val args = parse<ExpiringCommandArgs>()

            applyInfraction(
                    args.member,
                    args.memberLong,
                    args.duration,
                    args.reason.joinToString(" "),
                    message,
                    this)
        } else {
            val args = parse<NonExpiringCommandArgs>()

            applyInfraction(
                    args.member,
                    args.memberLong,
                    null,
                    args.reason.joinToString(" "),
                    message,
                    this
            )
        }
    }

    private fun formatTimestamp(ts: PlainTimestamp): String = ts.print(timeFormatter)

    private fun getMemberId(member: User?, id: Long?) =
            if (member == null && id == null) {
                Pair(null, "Please specify a user to apply this infraction to.")
            } else if (member != null && id != null) {
                Pair(null, "Please specify exactly one user argument, not two.")
            } else {
                Pair(member?.id?.longValue ?: id!!, null)
            }

    private suspend fun getUserMissingMessage(id: Long) =
            if (!type.requirePresent) {
                null
            } else if (config.getGuild().getMemberOrNull(Snowflake(id)) == null) {
                "The specified user is not present on the server."
            } else {
                null
            }

    private fun getInfractionMessage(public: Boolean, infraction: Infraction, expires: PlainTimestamp?): String {
        var message = if (public) {
            "<@!${infraction.target_id}> has been "
        } else {
            "You have been "
        }

        message += if (expires == null) {
            "${type.actionText}."
        } else {
            "${type.actionText} until ${formatTimestamp(expires)}."
        }

        message += "\n\n"

        message += if (type == InfractionTypes.NOTE) {
            "Message: ${infraction.reason}"
        } else {
            "Reason: ${infraction.reason}"
        }

        return message
    }

    private suspend fun relayInfraction(infraction: Infraction, expires: PlainTimestamp?) {
        if (type.relay) {
            try {
                val targetObj = bot.kord.getUser(Snowflake(infraction.target_id))

                targetObj?.getDmChannel()?.createEmbed {
                    color = Colours.NEGATIVE
                    title = type.actionText.capitalize() + "!"

                    description = getInfractionMessage(false, infraction, expires)

                    footer {
                        text = "Infraction ID: ${infraction.id}"
                    }
                }
            } catch (e: RestRequestException) {
                logger.debug(e) {
                    "Unable to DM user with ID ${infraction.target_id}, they're probably not in the guild."
                }
            }
        }
    }

    private suspend fun sendInfractionToChannel(channel: MessageChannelBehavior, infraction: Infraction,
                                                expires: PlainTimestamp?) {
        channel.createEmbed {
            color = Colours.POSITIVE
            title = "Infraction created"

            description = getInfractionMessage(true, infraction, expires)

            footer {
                text = "ID: ${infraction.id}"
            }
        }
    }

    private suspend fun sendInfractionToModLog(infraction: Infraction, expires: PlainTimestamp?) {
        val channel = config.getChannel(Channels.MODERATOR_LOG) as TextChannel
        var descriptionText = getInfractionMessage(true, infraction, expires)

        descriptionText += "\n\nUser ID: `${infraction.target_id}`"

        channel.createEmbed {
            color = Colours.NEGATIVE
            title = "User ${infraction.infraction_type.actionText}"

            description = descriptionText

            footer {
                text = "ID: ${infraction.id}"
            }
        }
    }

    private suspend fun applyInfraction(memberObj: User?, memberLong: Long?, duration: Duration<IsoUnit>?,
                                        reason: String, message: Message, context: CommandContext) {
        val author = message.author!!
        val (memberId, memberMessage) = getMemberId(memberObj, memberLong)

        if (memberId == null) {
            message.channel.createMessage("${author.mention} $memberMessage")
            return
        }

        val memberMissingMessage = getUserMissingMessage(memberId)

        if (memberMissingMessage != null) {
            message.channel.createMessage("${author.mention} $memberMissingMessage")
            return
        }

        val expires = if (duration != Duration.ofZero<IsoUnit>() && duration != null) {
            PlainTimestamp.nowInSystemTime().plus(duration)
        } else {
            null
        }

        val infraction = runSuspended {
            queries.addInfraction(
                    reason, author.id.longValue, memberId, expires?.print(mySqlTimeFormatter),  // null for forever
                    true, type
            )

            queries.getLastInfraction().executeAsOne()
        }

        relayInfraction(infraction, expires)
        infrAction.invoke(context, memberId, reason)
        sendInfractionToChannel(message.channel, infraction, expires)
        sendInfractionToModLog(infraction, expires)
    }

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
