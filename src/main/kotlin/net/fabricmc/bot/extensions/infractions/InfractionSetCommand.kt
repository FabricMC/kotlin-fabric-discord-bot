package net.fabricmc.bot.extensions.infractions

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.behavior.channel.MessageChannelBehavior
import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.core.event.message.MessageCreateEvent
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.commands.Command
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.converters.*
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.dm
import com.kotlindiscord.kord.extensions.utils.runSuspended
import mu.KotlinLogging
import net.fabricmc.bot.bot
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colors
import net.fabricmc.bot.database.Infraction
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.InfractionTypes
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.utils.modLog
import net.fabricmc.bot.utils.requireMainGuild
import java.time.Duration
import java.time.Instant

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
                           aliasList: Array<String> = arrayOf(),
        // This can't be suspending, see comment in InfractionActions.applyInfraction
                           private val infrAction: Infraction.(
                                   targetId: Snowflake, expires: Instant?
                           ) -> Unit
) : Command(extension) {
    private val queries = config.db.infractionQueries

    private val commandBody: suspend CommandContext.() -> Unit = {
        if (message.requireMainGuild(null)) {
            if (type.expires) {
                val args = parse(::InfractionSetExpiringCommandArgs)

                applyInfraction(
                        args.member,
                        args.memberLong,
                        args.duration,
                        args.reason,
                        message
                )
            } else {
                val args = parse(::InfractionSetNonExpiringCommandArgs)

                applyInfraction(
                        args.member,
                        args.memberLong,
                        null,
                        args.reason,
                        message
                )
            }
        }
    }

    private suspend fun getUserMissingMessage(id: Long) =
            if (!type.requirePresent) {
                null
            } else if (config.getGuild().getMemberOrNull(Snowflake(id)) == null) {
                "The specified user is not present on the server."
            } else {
                null
            }

    private fun getInfractionMessage(public: Boolean, infraction: Infraction, expires: Instant?): String {
        var message = if (public) {
            "<@!${infraction.target_id}> has been "
        } else {
            "You have been "
        }

        message += if (expires == null) {
            "${type.actionText}."
        } else {
            "${type.actionText} until ${instantToDisplay(expires)}."
        }

        message += "\n\n"

        message += if (type == InfractionTypes.NOTE) {
            "**Note:** ${infraction.reason}"
        } else {
            "**Infraction Reason:** ${infraction.reason}"
        }

        return message
    }

    private suspend fun relayInfraction(infraction: Infraction, expires: Instant?): Boolean? {
        if (type.relay) {
            val targetObj = bot.kord.getUser(Snowflake(infraction.target_id))

            targetObj?.dm {
                embed {
                    color = Colors.NEGATIVE
                    title = type.actionText.capitalize() + "!"

                    description = getInfractionMessage(false, infraction, expires)

                    description += "\n\nIf you believe this infraction was applied in error or you'd like to " +
                            "appeal it, please " +
                            "[feel free to fill out this form](https://airtable.com/shrzEYjRF4lBteEij)."

                    footer {
                        text = "Infraction ID: ${infraction.id}"
                    }

                    timestamp = Instant.now()
                }
            } ?: return false

            return true
        }

        return null
    }

    private suspend fun sendInfractionToChannel(channel: MessageChannelBehavior, infraction: Infraction,
                                                expires: Instant?, relayResult: Boolean? = null) {
        channel.createEmbed {
            color = Colors.POSITIVE

            author {
                name = "User ${infraction.infraction_type.actionText}"

                if (relayResult == false) {
                    icon = "https://cdn.discordapp.com/emojis/777986567993950239.png"
                }
            }

            description = getInfractionMessage(true, infraction, expires)

            if (relayResult == false) {
                description += "\n\n**Note:** Failed to DM the user, they may have their DMs disabled."
            }

            footer {
                text = "ID: ${infraction.id}"
            }

            timestamp = Instant.now()
        }
    }

    private suspend fun sendInfractionToModLog(infraction: Infraction, expires: Instant?,
                                               actor: User, relayResult: Boolean? = null) {
        var descriptionText = getInfractionMessage(true, infraction, expires)

        descriptionText += "\n\n**User ID:** `${infraction.target_id}`"
        descriptionText += "\n**Moderator:** ${actor.mention} (${actor.tag} / `${actor.id}`)"

        modLog {
            color = Colors.NEGATIVE

            author {
                name = "User ${infraction.infraction_type.actionText}"

                if (relayResult == false) {
                    icon = "https://cdn.discordapp.com/emojis/777986567993950239.png"
                }
            }

            description = descriptionText

            if (relayResult == false) {
                description += "\n\n**Note:** Failed to DM the user, they may have their DMs disabled."
            }

            footer {
                text = "ID: ${infraction.id}"
            }
        }
    }

    private suspend fun ensureUser(member: User?, memberId: Long) = runSuspended {
        val dbUser = config.db.userQueries.getUser(memberId).executeAsOneOrNull()

        if (dbUser == null) {
            if (member != null) {
                config.db.userQueries.insertUser(
                        memberId, member.avatar.url, member.discriminator,
                        true, member.username
                )
            } else {
                config.db.userQueries.insertUser(
                        memberId, "", "0000",
                        false, "Absent User"
                )
            }
        }
    }

    private suspend fun applyInfraction(memberObj: User?, memberLong: Long?, duration: Duration?,
                                        reason: String, message: Message) {
        val author = message.author!!
        val (memberId, memberMessage) = getMemberId(memberObj, memberLong?.let { Snowflake(it) })

        if (memberId == null) {
            message.channel.createMessage("${author.mention} $memberMessage")
            return
        }

        val memberMissingMessage = getUserMissingMessage(memberId.value)

        if (memberMissingMessage != null) {
            message.channel.createMessage("${author.mention} $memberMissingMessage")
            return
        }

        ensureUser(memberObj, memberId.value)

        val expires = if (duration != Duration.ZERO && duration != null) {
            Instant.now().plus(duration)
        } else {
            null
        }

        val infraction = runSuspended {
            if (expires != null) {
                queries.addInfraction(
                        reason,
                        author.id.value,
                        memberId.value,
                        instantToMysql(expires),
                        true,
                        type
                )
            } else {
                queries.addInfraction(
                        reason,
                        author.id.value,
                        memberId.value,
                        null,  // null for forever
                        true,
                        type
                )
            }

            queries.getLastInfraction().executeAsOne()
        }

        val relayResult = relayInfraction(infraction, expires)

        infrAction.invoke(infraction, memberId, expires)

        sendInfractionToChannel(message.channel, infraction, expires, relayResult)
        sendInfractionToModLog(infraction, expires, message.author!!, relayResult)
    }

    override val checkList: MutableList<suspend (MessageCreateEvent) -> Boolean> = mutableListOf(
            ::defaultCheck,
            {
                if (type.notForTrainees) {
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))(it)
                } else {
                    topRoleHigherOrEqual(config.getRole(Roles.TRAINEE_MODERATOR))(it)
                }
            }
    )

    init {
        this.aliases = aliasList
        this.name = commandName
        this.description = commandDescription

        signature = if (type.expires) {
            "<user/id> <duration> <reason ...>"
        } else {
            "<user/id> <reason ...>"
        }

        action(commandBody)
    }

    /** Class representing the arguments for an infraction type that doesn't expire.
     *
     * @property member The member to infract.
     * @property memberLong The ID of the member to infract, if they're not on the server.
     * @property reason The reason for the infraction.
     */
    @Suppress("UndocumentedPublicProperty")
    class InfractionSetNonExpiringCommandArgs : Arguments() {
        val member by optionalUser("member")
        val memberLong by optionalNumber("memberId")
        val reason by coalescedString("reason")
    }

    /** Class representing the arguments for an infraction type that expires.
     *
     * @property member The member to infract.
     * @property memberLong The ID of the member to infract, if they're not on the server.
     * @property duration How long to infract the user for.
     * @property reason The reason for the infraction.
     */
    @Suppress("UndocumentedPublicProperty")
    class InfractionSetExpiringCommandArgs : Arguments() {
        val member by optionalUser("member")
        val memberLong by optionalNumber("memberId")
        val duration by defaultingCoalescedDuration("duration", Duration.ZERO)
        val reason by coalescedString("reason")
    }
}
