package net.fabricmc.bot.extensions

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.behavior.ban
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import net.fabricmc.bot.commands.InfractionSetCommand
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.enums.InfractionTypes
import net.fabricmc.bot.enums.Roles

/**
 * Infractions extension, containing commands used to apply and remove infractions.
 */
class InfractionsExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "infractions"

    override suspend fun setup() {
        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.BAN,
                        "Permanently or temporarily ban a user.",
                        "ban"
                ) { id, reason -> config.getGuild().ban(Snowflake(id)) { this.reason = reason } }
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.KICK,
                        "Kick a user from the server.",
                        "kick"
                ) { id, reason -> config.getGuild().kick(Snowflake(id), reason) }
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.MUTE,
                        "Permanently or temporarily mute a user, server-wide.",
                        "mute"
                ) { id, _ -> config
                        .getGuild()
                        .getMemberOrNull(Snowflake(id))
                        ?.addRole(config.getRoleSnowflake(Roles.MUTED))
                }
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.META_MUTE,
                        "Permanently or temporarily mute a user, from the meta channel only.",
                        "mute-meta"
                ) { id, _ -> config
                        .getGuild()
                        .getMemberOrNull(Snowflake(id))
                        ?.addRole(config.getRoleSnowflake(Roles.NO_META))
                }
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.REACTION_MUTE,
                        "Permanently or temporarily prevent a user from adding reactions to messages.",
                        "mute-reactions"
                ) { id, _ -> config
                        .getGuild()
                        .getMemberOrNull(Snowflake(id))
                        ?.addRole(config.getRoleSnowflake(Roles.NO_REACTIONS))
                }
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.REQUESTS_MUTE,
                        "Permanently or temporarily mute a user, from the requests channel only.",
                        "mute-requests"
                ) { id, _ -> config
                        .getGuild()
                        .getMemberOrNull(Snowflake(id))
                        ?.addRole(config.getRoleSnowflake(Roles.NO_REQUESTS))
                }
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.SUPPORT_MUTE,
                        "Permanently or temporarily mute a user, from the player-support channel only.",
                        "mute-support"
                ) { id, _ -> config
                        .getGuild()
                        .getMemberOrNull(Snowflake(id))
                        ?.addRole(config.getRoleSnowflake(Roles.NO_SUPPORT))
                }
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.WARN,
                        "Officially warn a user for their actions.",
                        "warn"
                ) { id, _ -> }  // Nothing
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.NOTE,
                        "Add a note for a user.",
                        "note"
                ) { id, _ -> }  // Nothing
        )
    }
}
