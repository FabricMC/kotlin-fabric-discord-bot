package net.fabricmc.bot.extensions

import com.gitlab.kordlib.common.entity.Permission
import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.behavior.channel.edit
import com.gitlab.kordlib.core.entity.PermissionOverwrite
import com.gitlab.kordlib.core.entity.channel.Channel
import com.gitlab.kordlib.core.entity.channel.GuildChannel
import com.gitlab.kordlib.core.entity.channel.TextChannel
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.Scheduler
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.toHuman
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
    private val lockJobs: MutableMap<Long, UUID> = mutableMapOf()

    /**
     * Arguments for commands that take a duration and channel.
     *
     * @param durationInt Int-style duration representing the duration.
     * @param duration Duration object representing the duration.
     * @param channel Channel to action the command in.
     **/
    data class DurationChannelCommandArgs(
            val durationInt: Long? = null,
            val duration: Duration? = null,
            val channel: Channel? = null
    )

    /**
     * Arguments for the unlock command.
     *
     * @param channel Channel to action the command in.
     */
    data class UnlockArgs(
            val channel: Channel? = null
    )

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
                with(parse<DurationChannelCommandArgs>()) {
                    val author = message.author!!
                    val channelObj = (channel ?: message.channel).asChannel() as GuildChannel

                    val durationObj = duration ?: if (durationInt != null) {
                        Duration.of(durationInt, ChronoUnit.SECONDS)
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

                    val modLog = config.getChannel(Channels.MODERATOR_LOG) as TextChannel

                    modLog.createEmbed {
                        color = Colours.BLURPLE
                        title = "Channel locked"

                        description = "Channel locked for ${durationObj.toHuman()}: ${channelObj.mention}"

                        field {
                            name = "Moderator"
                            value = "${author.mention} (${author.username}#${author.discriminator} / " +
                                    "`${author.id.longValue}`)"
                        }

                        timestamp = Instant.now()
                    }

                    message.channel.createEmbed {
                        color = Colours.POSITIVE
                        title = "Channel locked"

                        description = "Channel locked for ${durationObj.toHuman()}: ${channelObj.mention}"
                    }

                    val channelId = channelObj.id.longValue

                    if (lockJobs.containsKey(channelId)) {
                        scheduler.cancelJob(lockJobs[channelId]!!)
                        lockJobs.remove(channelId)
                    }

                    lockJobs[channelId] = scheduler.schedule<Nothing?>(durationObj.toMillis(), null) {
                        channelObj.addOverwrite(
                                PermissionOverwrite.forEveryone(
                                        channelObj.guildId,
                                        perms.allowed,
                                        perms.denied - Permission.SendMessages + Permission.AddReactions
                                )
                        )

                        modLog.createEmbed {
                            color = Colours.BLURPLE
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
                with(parse<UnlockArgs>()) {
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

                    val modLog = config.getChannel(Channels.MODERATOR_LOG) as TextChannel

                    modLog.createEmbed {
                        color = Colours.BLURPLE
                        title = "Channel unlocked"

                        description = "Channel unlocked: ${channelObj.mention}"

                        field {
                            name = "Moderator"
                            value = "${author.mention} (${author.username}#${author.discriminator} / " +
                                    "`${author.id.longValue}`)"
                        }

                        timestamp = Instant.now()
                    }

                    message.channel.createEmbed {
                        color = Colours.POSITIVE
                        title = "Channel unlocked"

                        description = "Channel unlocked: ${channelObj.mention}"
                    }

                    val channelId = channelObj.id.longValue

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
                with(parse<DurationChannelCommandArgs>()) {
                    val author = message.author!!

                    if (this.duration != null && this.durationInt != null) {
                        message.channel.createMessage(
                                "${author.mention} Provide an integer or a duration with units, not both."
                        )

                        return@action
                    }

                    val durationObj = duration ?: if (durationInt != null) {
                        Duration.of(durationInt, ChronoUnit.SECONDS)
                    } else {
                        Duration.ZERO
                    }

                    val seconds: Int = durationObj.seconds.toInt()

                    if (seconds > SLOWMODE_LIMIT) {
                        message.channel.createMessage(
                                "${author.mention} Duration should be no longer than 6 hours."
                        )

                        return@action
                    }

                    val channel = (this.channel ?: message.channel.asChannel()) as TextChannel

                    channel.edit { rateLimitPerUser = seconds }

                    val modLog = config.getChannel(Channels.MODERATOR_LOG) as TextChannel

                    if (seconds > 0) {
                        modLog.createEmbed {
                            color = Colours.BLURPLE
                            title = "Slowmode enabled"

                            description = "Slowmode set to ${durationObj.toHuman()} in ${channel.mention}"

                            field {
                                name = "Moderator"
                                value = "${author.mention} (${author.username}#${author.discriminator} / " +
                                        "`${author.id.longValue}`)"
                            }

                            timestamp = Instant.now()
                        }

                        message.channel.createEmbed {
                            color = Colours.POSITIVE
                            title = "Slowmode enabled"

                            description = "Slowmode set to ${durationObj.toHuman()} in ${channel.mention}"
                        }
                    } else {
                        modLog.createEmbed {
                            color = Colours.BLURPLE
                            title = "Slowmode disabled"

                            description = "Slowmode disabled in ${channel.mention}"

                            field {
                                name = "Moderator"
                                value = "${author.mention} (${author.username}#${author.discriminator} / " +
                                        "`${author.id.longValue}`)"
                            }

                            timestamp = Instant.now()
                        }

                        message.channel.createEmbed {
                            color = Colours.POSITIVE
                            title = "Slowmode disabled"

                            description = "Slowmode disabled in ${channel.mention}"
                        }
                    }
                }
            }
        }
    }
}
