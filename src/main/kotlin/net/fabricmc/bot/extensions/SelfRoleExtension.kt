package net.fabricmc.bot.extensions

import com.gitlab.kordlib.rest.builder.message.EmbedBuilder
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.hasRole
import com.kotlindiscord.kord.extensions.utils.respond
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colors
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.utils.requireBotChannel

private const val DELETE_DELAY = 10_000L  // 10 seconds

/**
 * Extension to allow users to apply roles to themselves.
 */
class SelfRoleExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "selfrole"

    override suspend fun setup() {
        command {
            name = "devlife"
            description = "Toggle hiding community channels, leaving only ones about development."

            action {
                if (!message.requireBotChannel(delay = DELETE_DELAY, allowDm = false)) {
                    return@action
                }

                val member = config.getGuild().getMemberOrNull(message.author!!.id) ?: return@action
                val devLife = config.getRole(Roles.DEV_LIFE)
                val alreadyLivingTheDevLife = member.hasRole(devLife)
                val confirmation: EmbedBuilder.() -> Unit

                confirmation = if (!alreadyLivingTheDevLife) {
                    member.addRole(devLife.id, "Requested via !devlife");
                    {
                        color = Colors.POSITIVE
                        title = "Living the dev life!"
                        description = "You will no longer see community channels. Run the command again to toggle back."
                    }
                } else {
                    member.removeRole(devLife.id, "Requested via !devlife");
                    {
                        color = Colors.POSITIVE
                        title = "No longer living the dev life."
                        description =
                                "You will once again see community channels. Run the command again to toggle back."
                    }
                }

                message.respond {
                    embed(confirmation)
                }
            }
        }
    }
}
