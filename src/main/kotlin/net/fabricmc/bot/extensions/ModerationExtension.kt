package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.behavior.channel.edit
import com.gitlab.kordlib.core.entity.channel.Channel
import com.gitlab.kordlib.core.entity.channel.TextChannel
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.toSeconds
import net.time4j.Duration
import net.time4j.IsoUnit

private const val SLOWMODE_LIMIT = 60 * 60 * 6  // Six hours

/**
 * Moderation extension, containing non-infraction commands for server management.
 */
class ModerationExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "moderation"

    /**
     * Arguments for the slowmode command.
     *
     * @param durationInt Int-style duration representing the slowmode interval.
     * @param duration Duration representing the slowmode interval.
     * @param channel Channel to set the slowmode interval in.
     **/
    data class SlowmodeArgs(
            val durationInt: Int? = null,
            val duration: Duration<IsoUnit>? = null,
            val channel: Channel? = null
    )

    override suspend fun setup() {
        command {
            name = "slowmode"
            aliases = arrayOf("slow", "sm")

            description = "Enable slowmode for a channel, with the given message interval.\n\n" +

                    "By default, this comment will use the current channel - specify one after the duration" +
                    "to target that channel instead.\n\n" +

                    "Omit the duration or set it to `0s` to disable."

            signature = "[duration] [channel]"

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
            )

            action {
                with(parse<SlowmodeArgs>()) {
                    if (this.duration != null && this.durationInt != null) {
                        message.channel.createMessage(
                                "${message.author!!.mention} Provide an integer or a duration with units, not both."
                        )

                        return@action
                    }

                    val duration = this.duration?.toSeconds()?.toInt() ?: this.durationInt ?: 0

                    if (duration > SLOWMODE_LIMIT) {
                        message.channel.createMessage(
                                "${message.author!!.mention} Duration should be no longer than 6 hours."
                        )

                        return@action
                    }

                    val channel = (this.channel ?: message.channel.asChannel()) as TextChannel

                    channel.edit { rateLimitPerUser = duration }

                    message.channel.createEmbed {
                        color = Colours.POSITIVE

                        description = "Slowmode set to $duration seconds in ${channel.mention}"
                        title = ""
                    }
                }
            }
        }
    }
}
