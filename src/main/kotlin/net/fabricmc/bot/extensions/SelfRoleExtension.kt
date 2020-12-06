package net.fabricmc.bot.extensions

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import dev.kord.core.behavior.channel.createEmbed

/**
 * Extension to allow users to apply roles to themselves.
 */
class SelfRoleExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "selfrole"

    override suspend fun setup() {
        command {
            name = "devlife"
            description = "Learn how to hide channels"

            action {
                message.channel.createEmbed {
                    title = "Here's how to hide muted channels:"
                    image = "https://cdn.discordapp.com/attachments/565822936712347658/784465611152425040/guide.png"

                    description = "**1)** Right-click on a channel you'd like to hide, and then click on " +
                            "**Mute Channel** (or hover it and click **Until I turn it back on**). You can also " +
                            "click on the channel to view it and click on **the bell icon** at the top of the " +
                            "window.\n\n" +

                            "**2)** Observe that the channel has now been muted.\n\n" +

                            "**3)** Right-click the space above any category or below the channels list, and " +
                            "then click on **Hide Muted Channels**.\n\n" +

                            "**4)** Success! Your least-favourite channel has been muted. If you'd like to view " +
                            "any channels that you've hidden, simply reverse the above process.\n\n\n" +


                            "If you're on mobile, you can still do this by holding down on the channel you'd " +
                            "like to hide to mute it, and then tapping the server name at the top of the list " +
                            "of channels to hide your muted channels."
                }
            }
        }
    }
}
