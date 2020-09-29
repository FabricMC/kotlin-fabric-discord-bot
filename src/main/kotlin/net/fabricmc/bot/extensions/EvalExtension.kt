package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles
import javax.script.ScriptEngineManager

/**
 * Extension that provides an eval command.
 *
 * Only loaded in development. Restricted to the admin role.
 */
class EvalExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "eval"

    private val scriptEngine = ScriptEngineManager().getEngineByExtension("kts")

    override suspend fun setup() {
        command {
            name = "eval"
            description = "Evaluate some Kotlin code. Admins only. **This is unsafe and should" +
                    "only be used for debugging!**"
            signature = "<code>"

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.ADMIN))
            )

            action {
                var args = this.args.joinToString(" ")

                if (args.startsWith("```") && args.endsWith("```")) {
                    args = args.trim('`')

                    if (args.startsWith("kt")) {
                        args = args.substring(2)
                    } else if (args.startsWith("kotlin")) {
                        @Suppress("MagicNumber")
                        args = args.substring(6)
                    }
                }

                println(args)  // For testing

                @Suppress("TooGenericExceptionCaught")  // Anything could happen really
                try {
                    val result = scriptEngine.eval(args)

                    message.channel.createEmbed {
                        title = "Eval output"
                        color = Colours.BLURPLE

                        description = "```kt\n$result\n```"
                    }
                } catch (e: Exception) {
                    message.channel.createEmbed {
                        title = "Eval output"
                        color = Colours.NEGATIVE

                        description = "```kt\n$e\n```"
                    }
                }
            }
        }
    }
}
