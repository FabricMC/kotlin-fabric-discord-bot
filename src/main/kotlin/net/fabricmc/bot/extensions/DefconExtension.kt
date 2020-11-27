package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.event.gateway.ReadyEvent
import com.gitlab.kordlib.core.event.guild.MemberJoinEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.commands.converters.optionalBoolean
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.dm
import com.kotlindiscord.kord.extensions.utils.respond
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colors
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.extensions.infractions.instantToDisplay
import net.fabricmc.bot.isNew
import net.fabricmc.bot.utils.alert

private const val KICK_MESSAGE = "" +
        "Hello, thanks for joining the server!\n\n" +

        "Unfortunately, we're currently dealing with a raid or another large-scale attack, and we are currently " +
        "unable to accept newly-created users.\n\n" +

        "We hope to have the situation resolved soon, but feel free to try again later. Sorry for the " +
        "inconvenience, and we hope to see you around soon!"


/**
 * DEFCON extension, basic tools to use for low-tier raids.
 */
class DefconExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "defcon"
    private var isEnabled = false

    override suspend fun setup() {
        event<ReadyEvent> {
            action {
                // ...
            }
        }

        event<MemberJoinEvent> {
            check(inGuild(config.getGuild()))
            check { isEnabled }
            check { it.member.isNew() }

            action {
                it.member.dm(KICK_MESSAGE)
                it.member.kick("DEFCON enforcement")

                alert(false) {
                    title = "DEFCON enforcement"
                    description = "Prevented a user from joining as their account was created within the last " +
                            "three days.\n\n" +

                            "**User ID:** `${it.member.id}`\n" +
                            "**User tag:** `${it.member.tag}`\n" +
                            "**Creation date:** `${instantToDisplay(it.member.id.timeStamp)}`"

                    color = Colors.NEGATIVE
                }
            }
        }

        command {
            name = "defcon"
            description = "DEFCON management command.\n\n" +

                    "DEFCON is a basic tool for low-tier raids that prevents new users from joining the server. It " +
                    "should only be used during a raid, and not pre-emptively, as we don't want to discourage users " +
                    "from joining the server.\n\n" +

                    "Omit the argument to check whether DEFCON is enabled."

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
            )

            signature(::DefconArguments)

            action {
                with(parse(::DefconArguments)) {
                    if (enable == null) {
                        message.respond(
                                "DEFCON status: **${statusText(isEnabled).capitalize()}**"
                        )

                        return@action
                    }

                    if (enable == isEnabled) {
                        message.respond("DEFCON is already ${statusText(isEnabled)}.")
                    }

                    isEnabled = enable!!

                    message.respond("DEFCON is now ${statusText(isEnabled)}.")
                }
            }
        }
    }

    /** Given a boolean, output "enabled" or "disabled". **/
    fun statusText(enabled: Boolean) = if (enabled) "enabled" else "disabled"

    /** @suppress **/
    @Suppress("UndocumentedPublicProperty")
    class DefconArguments : Arguments() {
        val enable by optionalBoolean("enable")
    }
}
