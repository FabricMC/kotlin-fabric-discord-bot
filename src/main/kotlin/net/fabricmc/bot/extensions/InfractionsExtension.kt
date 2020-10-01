package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.behavior.channel.createMessage
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.rest.builder.message.EmbedBuilder
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import net.fabricmc.bot.bot
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.database.Infraction
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.InfractionTypes
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.extensions.infractions.*
import net.fabricmc.bot.runSuspended
import java.time.Instant

private const val UNITS = "**__Durations__**\n\n" +
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

@Suppress("UnusedPrivateMember")  // TODO: Gotta AFK
private const val FILTERS = "**__Filters__**\n\n" +
        "Filters are specified as key-value pairs, split by an equals sign - For example," +
        "`targetId=109040264529608704` would match infractions that target gdude. Multiple" +
        "filters are supported, but there are some restrictions.\n\n" +

        "**__Matching users**__\n\n" +
        "**Target:** Either `target` or `targetId`, but not both. The former matches mentions, " +
        "but only works if the user is on the server.\n" +
        "**Infractor:** Either `infractor` or `infractorId`, but not both, with the same restrictions" +
        "as the target filter.\n\n" +

        "**__Other filters__**\n\n" +

        "**Infraction ID:** `id`\n" +
        "**Infraction Type:** `type`, matched against the start of the following types: `banned`, `kicked`, `muted`, " +
        "`meta-muted`, `reaction-muted`, `requests-muted`, `support-muted`, `warned`, `noted`." +
        "**Active:** `active`, either `true` or `false`."

/**
 * Arguments for the infraction search command.
 */
@Suppress("UndocumentedPublicProperty")  // TODO: Gotta AFK
data class InfractionSearchCommandArgs(
        val target: User? = null,
        val targetId: Long? = null,

        val infractor: User? = null,
        val infractorId: Long? = null,

        val id: String? = null,
        val type: String? = null,
        val active: Boolean? = null
)

/**
 * Arguments for infraction commands that only take an ID.
 */
@Suppress("UndocumentedPublicProperty")  // TODO: Gotta AFK
data class InfractionIDCommandArgs(
        val id: String
)

/**
 * Infractions extension, containing commands used to apply and remove infractions.
 */
class InfractionsExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "infractions"
    private val q = config.db.infractionQueries

    @Suppress("UnusedPrivateMember")  // TODO: Gotta AFK
    private fun validateSearchArgs(args: InfractionSearchCommandArgs): String? {
        val atLeastOneFilter = args.target != null
                && args.targetId != null
                && args.infractor != null
                && args.infractorId != null
                && args.id != null
                && args.type != null
                && args.active != null

        if (!atLeastOneFilter) {
            return "Please provide at least one filter. Try `${net.fabricmc.bot.bot.prefix}help inf search` for " +
                    "more information."
        }

        if (args.target != null && args.targetId != null) {
            return "Please provide either the `target` or `targetId` filter, not both."
        }

        if (args.infractor != null && args.infractorId != null) {
            return "Please provide either the `infractor` or `infractorId` filter, not both."
        }

        return null
    }

    private fun infractionToString(inf: Infraction?): String? {
        if (inf == null) {
            return null
        }

        val verb = inf.infraction_type.verb.capitalize()
        val infractor = inf.infractor_id
        val target = inf.target_id

        val created = instantToDisplay(mysqlToInstant(inf.created))
        val expired = instantToDisplay(mysqlToInstant(inf.expires)) ?: "Never"

        val active = if (inf.active) "Active" else "Inactive"

        return "**__Details__**\n" +
                "**ID:** ${inf.id}\n" +
                "**Type:** $verb\n\n" +

                "**Moderator:** <@$infractor> (`$infractor`)\n" +
                "**User:** <@$target> (`$target`)\n\n" +

                "**Created:** $created\n" +
                "**Expires:** $expired\n\n" +

                "**Active:** $active\n\n" +
                "" +
                "**__Reason__**\n" +
                inf.reason
    }

    private fun infractionToEmbed(inf: Infraction?): EmbedBuilder? {
        val desc = infractionToString(inf) ?: return null

        return EmbedBuilder().apply {
            title = "Infraction Information"
            color = Colours.BLURPLE

            description = desc

            timestamp = Instant.now()
        }
    }

    override suspend fun setup() {
        // region: Infraction querying commands
        group {
            name = "infractions"
            aliases = arrayOf("inf", "infr", "infraction")

            description = "Commands for querying, searching and managing infractions. Try `" +
                    "${bot.prefix}help inf <subcommand>` for more information on each subcommand."

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
            )

            command {
                name = "get"
                aliases = arrayOf("g")

                description = "Get information on a specific infraction by ID. Infraction IDs are UUIDs, and can" +
                        "be found in the footer of every infraction embed."

                signature<InfractionIDCommandArgs>()

                action {
                    runSuspended {
                        with(parse<InfractionIDCommandArgs>()) {
                            val inf = q.getInfraction(id).executeAsOneOrNull()
                            val embedBuilder = infractionToEmbed(inf)

                            if (embedBuilder == null) {
                                message.channel.createMessage(
                                        "${message.author!!.mention} No such infraction: `$id`"
                                )

                                return@with
                            }

                            message.channel.createMessage { embed = embedBuilder }
                        }
                    }
                }
            }
        }
        // endregion

        // region: Infraction creation commands
        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.BAN,
                        "Permanently or temporarily ban a user.\n\n$UNITS",
                        "ban",
                        arrayOf("b"),
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.KICK,
                        "Kick a user from the server.",
                        "kick",
                        arrayOf("k"),
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
                        arrayOf("m"),
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
                        arrayOf("meta-mute", "mutemeta", "metamute"),
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
                        arrayOf(
                                "mute-reaction", "reactions-mute", "reaction-mute",
                                "mutereactions", "mutereaction", "reactionsmute", "reactionmute"),
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
                        arrayOf(
                                "mute-request", "requests-mute", "request-mute",
                                "muterequests", "muterequest", "requestsmute", "requestmute"
                        ),
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
                        arrayOf("support-mute", "mutesupport", "supportmute"),
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.WARN,
                        "Officially warn a user for their actions.",
                        "warn",
                        arrayOf("w"),
                        ::applyInfraction
                )
        )

        command(
                InfractionSetCommand(
                        this,
                        InfractionTypes.NOTE,
                        "Add a note for a user.",
                        "note",
                        arrayOf("n"),
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
                        arrayOf("ub", "un-ban"),
                        ::pardonInfraction
                )
        )

        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.MUTE,
                        "Pardon all permanent or temporary server-wide mutes for a user.",
                        "unmute",
                        arrayOf("um", "un-mute"),
                        ::pardonInfraction
                )
        )

        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.META_MUTE,
                        "Pardon all permanent or temporary meta channel mutes for a user.",
                        "unmute-meta",
                        arrayOf(
                                "un-mute-meta", "meta-unmute", "meta-un-mute", "un-meta-mute", "unmeta-mute",
                                "unmutemeta", "metaunmute", "unmetamute"
                        ),
                        ::pardonInfraction
                )
        )

        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.REACTION_MUTE,
                        "Pardon all permanent or temporary reaction mutes for a user.",
                        "unmute-reactions",
                        arrayOf(
                                "un-mute-reactions", "reactions-unmute", "reactions-un-mute",
                                "unmutereactions", "reactionsunmute",
                                "un-mute-reaction", "reaction-unmute", "reaction-un-mute",
                                "unmutereaction", "reactionunmute",
                                "un-reactions-mute", "un-reaction-mute",
                                "un-reactionsmute", "un-reactionmute",
                                "unreactionsmute", "unreactionmute"
                        ),
                        ::pardonInfraction
                )
        )

        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.REQUESTS_MUTE,
                        "Pardon all permanent or temporary requests channel mutes for a user.",
                        "unmute-requests",
                        arrayOf(
                                "un-mute-requests", "unmuterequests",
                                "requests-un-mute", "requests-unmute", "requestsunmute",
                                "un-requests-mute", "un-requestsmute", "unrequestsmute"
                        ),
                        ::pardonInfraction
                )
        )

        command(
                InfractionUnsetCommand(
                        this,
                        InfractionTypes.SUPPORT_MUTE,
                        "Pardon all permanent or temporary support channel mutes for a user.",
                        "unmute-support",
                        arrayOf(
                                "un-mute-support", "unmutesupport",
                                "support-un-mute", "support-unmute", "supportunmute",
                                "un-support-mute", "un-supportmute", "unsupportmute"
                        ),
                        ::pardonInfraction
                )
        )
        // endregion
    }
}
