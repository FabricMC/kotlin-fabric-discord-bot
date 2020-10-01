package net.fabricmc.bot.extensions

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import net.fabricmc.bot.enums.InfractionTypes
import net.fabricmc.bot.extensions.infractions.*

private const val UNITS = "**__Durations__**\n\n" +
        "Durations are specified in pairs of amounts and units - for example, `12d` would be 12 days. " +
        "Compound durations are supported - for example, `2d12h` would be 2 days and 12 hours.\n\n" +
        "The following units are supported:\n\n" +
        "" +
        "**Seconds:** `s`, `sec`, `second`, `seconds`\n" +
        "**Minutes:** `m`, `mi`, `min`, `minute`, `minutes`\n" +
        "**Hours:** `h`, `hour`, `hours`\n" +
        "**Days:** `d`, `day`, `days`\n" +
        "**Weeks:** `w`, `week`, `weeks`\n" +
        "**Months:** `mo`, `month`, `months`\n" +
        "**Years:** `y`, `year`, `years`"

/**
 * Infractions extension, containing commands used to apply and remove infractions.
 */
class InfractionsExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "infractions"

    override suspend fun setup() {
        // region: Infraction creation commands
        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.BAN,
                        "Permanently or temporarily ban a user.\n\n$UNITS",
                        "ban",
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.KICK,
                        "Kick a user from the server.",
                        "kick",
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.MUTE,
                        "Permanently or temporarily mute a user, server-wide." +
                                "\n\n$UNITS",
                        "mute",
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.META_MUTE,
                        "Permanently or temporarily mute a user, from the meta channel only." +
                                "\n\n$UNITS",
                        "mute-meta",
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.REACTION_MUTE,
                        "Permanently or temporarily prevent a user from adding reactions to " +
                                "messages.\n\n$UNITS",
                        "mute-reactions",
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.REQUESTS_MUTE,
                        "Permanently or temporarily mute a user, from the requests channel only." +
                                "\n\n$UNITS",
                        "mute-requests",
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.SUPPORT_MUTE,
                        "Permanently or temporarily mute a user, from the player-support channel " +
                                "only.\n\n$UNITS",
                        "mute-support",
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.WARN,
                        "Officially warn a user for their actions.",
                        "warn",
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.NOTE,
                        "Add a note for a user.",
                        "note",
                        ::applyInfraction
                )
        )
        // endregion

        // region: Infraction removal commands
        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.BAN,
                        "Pardon all permanent or temporary bans for a user.",
                        "unban",
                        ::pardonInfraction
                )
        )

        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.MUTE,
                        "Pardon all permanent or temporary server-wide mutes for a user.",
                        "unmute",
                        ::pardonInfraction
                )
        )

        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.META_MUTE,
                        "Pardon all permanent or temporary meta channel mutes for a user.",
                        "unmute-meta",
                        ::pardonInfraction
                )
        )

        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.REACTION_MUTE,
                        "Pardon all permanent or temporary reaction mutes for a user.",
                        "unmute-reactions",
                        ::pardonInfraction
                )
        )

        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.REQUESTS_MUTE,
                        "Pardon all permanent or temporary requests channel mutes for a user.",
                        "unmute-requests",
                        ::pardonInfraction
                )
        )

        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.SUPPORT_MUTE,
                        "Pardon all permanent or temporary support channel mutes for a user.",
                        "unmute-support",
                        ::pardonInfraction
                )
        )
        // endregion
    }
}
