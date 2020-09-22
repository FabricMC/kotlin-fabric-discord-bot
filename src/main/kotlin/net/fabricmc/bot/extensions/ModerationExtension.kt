package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.behavior.channel.edit
import com.gitlab.kordlib.core.entity.channel.Channel
import com.gitlab.kordlib.core.entity.channel.TextChannel
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.toSeconds
import net.time4j.Duration
import net.time4j.IsoUnit
import java.awt.Color

/**
 * Moderation extension, containing non-infraction commands for server management.
 */
class ModerationExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "moderation"

    /**
     * Arguments for the slowmode command.
     *
     * @param durations List of durations representing the slowmode interval.
     * @param channel Channel to set the slowmode interval in.
     **/
    data class SlowmodeArgs(
        val durations: List<Duration<IsoUnit>>,
        val channel: Channel?
    )

    override suspend fun setup() {
        command {
            name = "slowmode"
            aliases = arrayOf("slow", "sm")

            description = """
                Enable slowmode for a channel, with the given message interval.
                
                By default, this comment will use the current channel - specify one after the duration
                to target that channel instead.

                Omit the duration or set it to `0s` to disable.
            """.trimIndent()

            signature<SlowmodeArgs>()

            check(::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
            )

            action {
                with(parse<SlowmodeArgs>()) {
                    val duration = this.durations
                            .fold(Duration.ofZero<IsoUnit>()) { left, right -> left.plus(right) }
                            .toSeconds().toInt()

                    val channel = (this.channel ?: message.channel) as TextChannel

                    channel.edit { rateLimitPerUser = duration }

                    message.channel.createEmbed {
                        color = Color.RED

                        description = "Slowmode set to $duration seconds in ${channel.mention}"
                    }
                }
            }
        }
    }
}
