package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.rest.builder.message.EmbedBuilder
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.deleteWithDelay
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.hasRole
import net.fabricmc.bot.utils.respond

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
                val botCommands = config.getChannel(Channels.BOT_COMMANDS)

                if (message.channel.id.longValue != botCommands.id.longValue) {
                    message.respond(
                            "Please use ${botCommands.mention} for this command."
                    ).deleteWithDelay(DELETE_DELAY)

                    return@action
                }

                val member = message.getAuthorAsMember() ?: return@action
                val devLife = config.getRole(Roles.DEV_LIFE)
                val alreadyLivingTheDevLife = member.hasRole(devLife)
                val confirmation: EmbedBuilder.() -> Unit

                confirmation = if (!alreadyLivingTheDevLife) {
                    member.addRole(devLife.id, "Requested via !devlife");
                    {
                        color = Colours.POSITIVE
                        title = "Living the dev life!"
                        description = "You will no longer see community channels. Run the command again to toggle back."
                    }
                } else {
                    member.removeRole(devLife.id, "Requested via !devlife");
                    {
                        color = Colours.POSITIVE
                        title = "No longer living the dev life."
                        description =
                                "You will once again see community channels. Run the command again to toggle back."
                    }
                }

                message.channel.createEmbed(confirmation)
            }
        }
    }
}
