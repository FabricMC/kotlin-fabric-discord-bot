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
import java.time.Instant

/** Data class representing the arguments for the infraction pardoning command.
 *
 * @param member The member to pardon.
 * @param memberLong The ID of the member to pardon, if they're not on the server.
 */
data class InfractionUnsetCommandArgs(
        val member: User? = null,
        val memberLong: Long? = null
)

private val logger = KotlinLogging.logger {}

/**
 * A command type for pardoning an infraction for a user.
 *
 * This command just handles the database work and notification, you'll still need to apply an [infrAction] to
 * apply the change on Discord.
 *
 * @param type The type of infraction to apply.
 * @param commandDescription The description to use for this command.
 * @param commandName The name of this command.
 * @param infrAction How to apply the infraction to the user.
 */
class InfractionUnsetCommand(extension: Extension, private val type: InfractionTypes,
                             private val commandDescription: String,
                             private val commandName: String,
                             private val aliasList: Array<String> = arrayOf(),
        // This can't be suspending, see comment in InfractionActions.applyInfraction
                             private val infrAction: Infraction.(
                                     targetId: Long, expires: Instant?
                             ) -> Unit
) : Command(extension) {
    private val queries = config.db.infractionQueries

    private val commandBody: suspend CommandContext.() -> Unit = {
        val args = parse<InfractionUnsetCommandArgs>()

        undoInfraction(
                args.member,
                args.memberLong,
                message
        )
    }

    private fun getInfractionMessage(public: Boolean, infraction: Infraction, showReason: Boolean = false): String {
        var message = if (public) {
            "<@!${infraction.target_id}> is no longer ${type.actionText}."
        } else {
            "You are no longer ${type.actionText}."
        }

        message += "\n\n"

        if (showReason) {
            message += if (type == InfractionTypes.NOTE) {
                "**Infraction Message:** ${infraction.reason}"
            } else {
                "**Infraction Reason:** ${infraction.reason}"
            }
        }

        return message
    }

    private suspend fun sendToUser(infraction: Infraction) {
        if (type.relay) {
            try {
                val targetObj = bot.kord.getUser(Snowflake(infraction.target_id))

                targetObj?.getDmChannel()?.createEmbed {
                    color = Colours.POSITIVE
                    title = type.actionText.capitalize() + "!"

                    description = getInfractionMessage(false, infraction, true)

                    footer {
                        text = "Infraction ID: ${infraction.id}"
                    }

                    timestamp = Instant.now()
                }
            } catch (e: RestRequestException) {
                logger.debug(e) {
                    "Unable to DM user with ID ${infraction.target_id}, they're probably not in the guild."
                }
            }
        }
    }

    private suspend fun sendToChannel(channel: MessageChannelBehavior, infraction: Infraction) {
        channel.createEmbed {
            color = Colours.POSITIVE
            title = "Infraction pardoned"

            description = getInfractionMessage(true, infraction)

            footer {
                text = "ID: ${infraction.id}"
            }

            timestamp = Instant.now()
        }
    }

    private suspend fun sendToModLog(infraction: Infraction, actor: User) {
        val channel = config.getChannel(Channels.MODERATOR_LOG) as TextChannel
        var descriptionText = getInfractionMessage(true, infraction, true)

        descriptionText += "\n\n**User ID:** `${infraction.target_id}`"
        descriptionText += "\n**Moderator:** ${actor.mention} (`${actor.id.longValue}`)"

        channel.createEmbed {
            color = Colours.POSITIVE
            title = "Infraction Pardoned"

            description = descriptionText

            footer {
                text = "ID: ${infraction.id}"
            }

            timestamp = Instant.now()
        }
    }

    private suspend fun undoInfraction(memberObj: User?, memberLong: Long?, message: Message) {
        val author = message.author!!
        val (memberId, memberMessage) = getMemberId(memberObj, memberLong)

        if (memberId == null) {
            message.channel.createMessage("${author.mention} $memberMessage")
            return
        }

        val infractions = runSuspended {
            queries.getInfractionsByUser(memberId).executeAsList()
        }.filter { it.active && it.infraction_type == type }

        if (infractions.isEmpty()) {
            message.channel.createMessage(
                    "${author.mention} Unable to find a matching active infraction for that user."
            )

            return
        }

        for (infraction in infractions) {
            val expires = mysqlToInstant(infraction.expires)

            infrAction.invoke(infraction, memberId, expires)

            sendToUser(infraction)
            sendToChannel(message.channel, infraction)
            sendToModLog(infraction, message.author!!)
        }
    }

    override val checkList: MutableList<suspend (MessageCreateEvent) -> Boolean> = mutableListOf(
            ::defaultCheck,
            { topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))(it) }  // Gotta be suspended
    )

    init {
        this.aliases = aliasList
        this.name = commandName
        this.description = commandDescription

        signature = "<user/id>"

        action(commandBody)
    }
}
