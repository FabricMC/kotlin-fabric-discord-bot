package net.fabricmc.bot.extensions

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.edit
import dev.kord.core.entity.PermissionOverwrite
import dev.kord.core.entity.channel.*
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.commands.converters.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.optionalDuration
import com.kotlindiscord.kord.extensions.commands.converters.optionalNumber
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.Scheduler
import com.kotlindiscord.kord.extensions.utils.respond
import com.kotlindiscord.kord.extensions.utils.toHuman
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colors
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.utils.modLog
import net.fabricmc.bot.utils.requireMainGuild
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

private const val SLOWMODE_LIMIT = 60 * 60 * 6  // Six hours
private const val DEFAULT_LOCK_MINUTES = 5L

private const val UNITS = "**__Durations__**\n" +
        "Durations are specified in pairs of amounts and units - for example, `12d` would be 12 days. " +
        "Compound durations are supported - for example, `2d12h` would be 2 days and 12 hours.\n\n" +
        "The following units are supported:\n\n" +

        "**Seconds:** `s`, `sec`, `second`, `seconds`\n" +
        "**Minutes:** `m`, `mi`, `min`, `minute`, `minutes`\n" +
        "**Hours:** `h`, `hour`, `hours`\n" +
        "**Days:** `d`, `day`, `days`\n" +
        "**Weeks:** `w`, `week`, `weeks`\n" +
        "**Months:** `mo`, `month`, `months`\n" +
        "**Years:** `y`, `year`, `years`"

/**
 * Moderation extension, containing non-infraction commands for server management.
 */
class ModerationExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "moderation"

    private val scheduler = Scheduler()
    private val lockJobs: MutableMap<Snowflake, UUID> = mutableMapOf()

    override suspend fun setup() {
        command {
            name = "lock"
            aliases = arrayOf("shh")

            description = "Lock the channel and prevent users from talking within it. Defaults to the current " +
                    "channel, and a 5 minute duration.\n\n$UNITS"

            signature = "[duration] [channel]"

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
            )

            action {
                if (!message.requireMainGuild(null)) {
                    return@action
                }

                with(parse(::DurationChannelCommandArgs)) {
                    val author = message.author!!
                    val channelObj = (channel ?: message.channel).asChannel() as GuildChannel

                    val durationObj = duration ?: if (durationInt != null) {
                        Duration.of(durationInt!!, ChronoUnit.SECONDS)
                    } else {
                        Duration.of(DEFAULT_LOCK_MINUTES, ChronoUnit.MINUTES)
                    }

                    val perms = channelObj.getPermissionOverwritesForRole(channelObj.guildId)  // @everyone
                            ?: PermissionOverwrite.forEveryone(channelObj.guildId)

                    val permsObj = PermissionOverwrite.forEveryone(
                            channelObj.guildId,
                            perms.allowed,
                            perms.denied + Permission.SendMessages + Permission.AddReactions
                    )

                    channelObj.addOverwrite(permsObj)

                    modLog {
                        color = Colors.BLURPLE
                        title = "Channel locked"

                        description = "Channel locked for ${durationObj.toHuman()}: ${channelObj.mention}"

                        field {
                            name = "Moderator"
                            value = "${author.mention} (${author.tag} / " +
                                    "`${author.id}`)"
                        }

                        timestamp = Instant.now()
                    }

                    message.respond {
                        embed {
                            color = Colors.POSITIVE
                            title = "Channel locked"

                            description = "Channel locked for ${durationObj.toHuman()}: ${channelObj.mention}"
                        }
                    }

                    val channelId = channelObj.id

                    if (lockJobs.containsKey(channelId)) {
                        scheduler.cancelJob(lockJobs[channelId]!!)
                        lockJobs.remove(channelId)
                    }

                    lockJobs[channelId] = scheduler.schedule<Nothing?>(durationObj.toMillis(), null) {
                        channelObj.addOverwrite(
                                PermissionOverwrite.forEveryone(
                                        channelObj.guildId,
                                        perms.allowed,
                                        perms.denied - Permission.SendMessages - Permission.AddReactions
                                )
                        )

                        modLog {
                            color = Colors.BLURPLE
                            title = "Channel unlocked"

                            description = "Channel unlocked automatically: ${channelObj.mention}"
                        }

                        lockJobs.remove(channelId)
                    }
                }
            }
        }

        command {
            name = "unlock"
            aliases = arrayOf("un-lock", "unshh", "un-shh")

            description = "Unlock a previously-unlocked channel. Defaults to the current channel if you don't " +
                    "specify one."

            signature = "[channel]"

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
            )

            action {
                if (!message.requireMainGuild(null)) {
                    return@action
                }

                with(parse(::UnlockArgs)) {
                    val author = message.author!!
                    val channelObj = (channel ?: message.channel).asChannel() as GuildChannel

                    val perms = channelObj.getPermissionOverwritesForRole(channelObj.guildId)  // @everyone
                            ?: PermissionOverwrite.forEveryone(channelObj.guildId)

                    val permsObj = PermissionOverwrite.forEveryone(
                            config.guildSnowflake,
                            perms.allowed,
                            perms.denied - Permission.SendMessages - Permission.AddReactions
                    )

                    channelObj.addOverwrite(permsObj)

                    modLog {
                        color = Colors.BLURPLE
                        title = "Channel unlocked"

                        description = "Channel unlocked: ${channelObj.mention}"

                        field {
                            name = "Moderator"
                            value = "${author.mention} (${author.tag} / " +
                                    "`${author.id}`)"
                        }

                        timestamp = Instant.now()
                    }

                    message.respond {
                        embed {
                            color = Colors.POSITIVE
                            title = "Channel unlocked"

                            description = "Channel unlocked: ${channelObj.mention}"
                        }
                    }

                    val channelId = channelObj.id

                    if (lockJobs.containsKey(channelId)) {
                        scheduler.cancelJob(lockJobs[channelId]!!)
                        lockJobs.remove(channelId)
                    }
                }
            }
        }

        command {
            name = "slowmode"
            aliases = arrayOf("slow", "sm")

            description = "Enable slowmode for a channel, with the given message interval.\n\n" +

                    "By default, this comment will use the current channel - specify one after the duration" +
                    "to target that channel instead.\n\n" +

                    "Omit the duration or set it to `0s` to disable.\n\n" +

                    UNITS

            signature = "[duration] [channel]"

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
            )

            action {
                if (!message.requireMainGuild(null)) {
                    return@action
                }

                with(parse(::DurationChannelCommandArgs)) {
                    val author = message.author!!

                    if (this.duration != null && this.durationInt != null) {
                        message.respond("Provide an integer or a duration with units, not both.")

                        return@action
                    }

                    val durationObj = duration ?: if (durationInt != null) {
                        Duration.of(durationInt!!, ChronoUnit.SECONDS)
                    } else {
                        Duration.ZERO
                    }

                    val seconds: Int = durationObj.seconds.toInt()

                    if (seconds > SLOWMODE_LIMIT) {
                        message.respond("Duration should be no longer than 6 hours.")

                        return@action
                    }

                    val channel = (this.channel ?: message.channel.asChannel()) as TextChannel

                    if (seconds > 0) {
                        modLog {
                            color = Colors.BLURPLE
                            title = "Slowmode enabled"

                            description = "Slowmode set to ${durationObj.toHuman()} in ${channel.mention}"

                            field {
                                name = "Moderator"
                                value = "${author.mention} (${author.tag} / " +
                                        "`${author.id}`)"
                            }

                            timestamp = Instant.now()
                        }

                        message.respond {
                            embed {
                                color = Colors.POSITIVE
                                title = "Slowmode enabled"

                                description = "Slowmode set to ${durationObj.toHuman()} in ${channel.mention}"
                            }
                        }
                    } else {
                        modLog {
                            color = Colors.BLURPLE
                            title = "Slowmode disabled"

                            description = "Slowmode disabled in ${channel.mention}"

                            field {
                                name = "Moderator"
                                value = "${author.mention} (${author.tag} / " +
                                        "`${author.id}`)"
                            }

                            timestamp = Instant.now()
                        }

                        message.respond {
                            embed {
                                color = Colors.POSITIVE
                                title = "Slowmode disabled"

                                description = "Slowmode disabled in ${channel.mention}"
                            }
                        }
                    }

                    channel.edit {
                        rateLimitPerUser = seconds
                    }
                }
            }
        }
    }

    /**
     * Arguments for commands that take a duration and channel.
     *
     * @property durationInt Int-style duration representing the duration.
     * @property duration Duration object representing the duration.
     * @property channel Channel to action the command in.
     **/
    @Suppress("UndocumentedPublicProperty")
    class DurationChannelCommandArgs : Arguments() {
        val durationInt by optionalNumber("durationInt")
        val duration by optionalDuration("duration")
        val channel by optionalChannel("channel")
    }

    /**
     * Arguments for the unlock command.
     *
     * @property channel Channel to action the command in.
     */
    @Suppress("UndocumentedPublicProperty")
    class UnlockArgs : Arguments() {
        val channel by optionalChannel("channel")
    }
}
