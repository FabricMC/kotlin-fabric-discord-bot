package net.fabricmc.bot.extensions

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.behavior.channel.createMessage
import com.gitlab.kordlib.core.behavior.edit
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.core.event.guild.MemberUpdateEvent
import com.gitlab.kordlib.rest.builder.message.EmbedBuilder
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.Paginator
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.checks.topRoleLower
import com.kotlindiscord.kord.extensions.extensions.Extension
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.database.Infraction
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.InfractionTypes
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.enums.getInfractionType
import net.fabricmc.bot.extensions.infractions.*
import net.fabricmc.bot.runSuspended
import net.fabricmc.bot.utils.deltas.MemberDelta
import net.fabricmc.bot.utils.dm
import net.fabricmc.bot.utils.modLog
import net.fabricmc.bot.utils.respond
import java.time.Instant

private const val PAGINATOR_TIMEOUT = 120 * 1000L

private const val UNITS = "**__Durations__**\n" +
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

private const val FILTERS = "**__Filters__**\n" +
        "Filters are specified as key-value pairs, split by an equals sign - For example," +
        "`targetId=109040264529608704` would match infractions that target gdude. Multiple " +
        "filters are supported, but there are some restrictions.\n\n" +

        "**__Matching users__**\n" +

        "**Target:** One of `target`, `targetName` or `targetId`.\n" +
        "**»** `target` Matches a mention, assuming the user is on the server.\n" +
        "**»** `targetId` Matches a user ID, whether or not they're on the server.\n" +
        "**»** `targetName` Searches the database for users with usernames that contain the given value, and uses " +
        "those for the match.\n\n" +

        "**Moderator:** One of `moderator` or `moderatorId`.\n" +
        "**»** `moderator` Matches a mention, assuming the user is on the server.\n" +
        "**»** `moderatorId` Matches a user ID, whether or not they're on the server.\n\n" +

        "**__Other filters__**\n" +

        "**Infraction Type:** `type`, matched against the start of the following types: `banned`, `kicked`, `muted`, " +
        "`meta-muted`, `reaction-muted`, `requests-muted`, `support-muted`, `nick-locked`, `warned`, `noted`.\n\n" +

        "**Active:** `active`, either `true` or `false`."

private val logger = KotlinLogging.logger {}

/**
 * Arguments for the infraction search command.
 */
@Suppress("UndocumentedPublicProperty")
data class InfractionSearchCommandArgs(
        val target: User? = null,
        val targetId: Long? = null,

        val moderator: User? = null,
        val moderatorId: Long? = null,

        val type: String? = null,
        val active: Boolean? = null,

        val targetName: String? = null
)

/**
 * Arguments for the infraction reason command.
 */
@Suppress("UndocumentedPublicProperty")
data class InfractionReasonCommandArgs(
        val id: String,
        val reason: List<String> = listOf()
)

/**
 * Arguments for infraction commands that only take an ID.
 */
@Suppress("UndocumentedPublicProperty")
data class InfractionIDCommandArgs(
        val id: String
)

/**
 * Arguments for the nickname command.
 */
@Suppress("UndocumentedPublicProperty")
data class InfractionNickCommandArgs(
        val target: User? = null,
        val targetId: Long? = null,

        val nickname: List<String> = listOf()
)

/**
 * Infractions extension, containing commands used to apply and remove infractions.
 */
class InfractionsExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "infractions"
    private val infQ = config.db.infractionQueries
    private val userQ = config.db.userQueries

    private val sanctionedNickChanges: Multimap<Long, String> = HashMultimap.create()

    @Suppress("UnusedPrivateMember")
    private fun validateSearchArgs(args: InfractionSearchCommandArgs): String? {
        val atLeastOneFilter = args.target != null
                || args.targetId != null
                || args.moderator != null
                || args.moderatorId != null
                || args.type != null
                || args.active != null
                || args.targetName != null

        if (!atLeastOneFilter) {
            return "Please provide at least one filter. Try `${bot.prefix}help inf search` for " +
                    "more information."
        }

        val targetNulls = arrayOf(args.target, args.targetId, args.targetName).count { it == null }

        if (targetNulls < 2) {
            return "Please provide only one of the `target`, `targetId` or `targetName` filters."
        }

        if (args.moderator != null && args.moderatorId != null) {
            return "Please provide either the `moderator` or `moderatorId` filter, not both."
        }

        return null
    }

    private fun infractionToString(inf: Infraction?): String? {
        if (inf == null) {
            return null
        }

        val verb = inf.infraction_type.verb.capitalize()
        val moderator = inf.moderator_id
        val target = inf.target_id

        val created = instantToDisplay(mysqlToInstant(inf.created))
        val expired = instantToDisplay(mysqlToInstant(inf.expires)) ?: "Never"

        val active = if (inf.active) "Yes" else "No"

        return "**__Details__**\n" +
                "**ID:** ${inf.id}\n" +
                "**Type:** $verb\n\n" +

                "**Moderator:** <@$moderator> (`$moderator`)\n" +
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
        sanctionedNickChanges.clear()

        // region: Utility commands

        command {
            name = "nick"
            description = "Change the nickname of a user, even if they're nick-locked."

            aliases = arrayOf("nickname")

            signature = "<user> [nickname ...]"

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.TRAINEE_MODERATOR))
            )

            action {
                with(parse<InfractionNickCommandArgs>()) {
                    if (target != null && targetId != null) {
                        message.respond("Please specify a user mention or user ID - not both.")
                        return@action
                    }

                    val memberId = getMemberId(target, targetId)

                    if (memberId == null) {
                        message.respond("Please specify a user to change the nick for.")
                        return@action
                    }

                    val member = config.getGuild().getMemberOrNull(Snowflake(memberId))

                    if (member == null) {
                        message.respond("Unable to find that user - are they on the server?")
                        return@action
                    }

                    val oldNick = member.nickname

                    val newNick = if (nickname.isEmpty()) {
                        member.username  // Until Kord figures out this null/missing stuff
                    } else {
                        nickname.joinToString(" ")
                    }

                    sanctionedNickChanges.put(memberId, newNick)

                    member.edit {
                        this.nickname = newNick
                    }

                    modLog {
                        title = "Nickname set"
                        color = Colours.POSITIVE

                        // Until Kord figures out this null/missing stuff
                        description = if (newNick == member.username) {
                            "Nickname for ${member.mention} (${member.tag} / " +
                                    "`${member.id.longValue}`) updated to: $newNick"
                        } else {
                            "Nickname for ${member.mention} (${member.tag} / " +
                                    "`${member.id.longValue}`) removed."
                        }

                        field {
                            name = "Moderator"
                            value = "${message.author!!.mention} (${message.author!!.tag} / " +
                                    "`${message.author!!.id.longValue}`)"
                        }

                        if (oldNick != null) {
                            field {
                                name = "Old Nick"
                                value = oldNick
                            }
                        }
                    }

                    member.dm {
                        embed {
                            title = "Nickname set"
                            color = Colours.NEGATIVE

                            description = if (newNick != null) {
                                "A moderator has updated your nickname to: $newNick"
                            } else {
                                "A moderator has removed your nickname."
                            }

                            timestamp = Instant.now()
                        }
                    }

                    message.respond("User's nickname has been updated.")
                }
            }
        }

        // endregion

        // region: Infraction querying commands
        group {
            name = "infractions"
            aliases = arrayOf("inf", "infr", "infraction")

            description = "Commands for querying, searching and managing infractions. Try `" +
                    "${bot.prefix}help inf <subcommand>` for more information on each subcommand."

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.TRAINEE_MODERATOR))
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
                            val inf = infQ.getInfraction(id).executeAsOneOrNull()
                            val embedBuilder = infractionToEmbed(inf)

                            if (embedBuilder == null) {
                                message.respond(
                                        "No such infraction: `$id`"
                                )

                                return@with
                            }

                            message.channel.createMessage { embed = embedBuilder }
                        }
                    }
                }
            }

            command {
                name = "expire"
                aliases = arrayOf("e")

                description = "Manually expire an infraction by ID."

                check(
                        topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
                )

                signature<InfractionIDCommandArgs>()

                action {
                    runSuspended {
                        with(parse<InfractionIDCommandArgs>()) {
                            val inf = infQ.getInfraction(id).executeAsOneOrNull()

                            if (inf == null) {
                                message.respond(
                                        "No such infraction: `$id`"
                                )

                                return@with
                            }

                            infQ.setInfractionActive(false, inf.id)

                            pardonInfraction(inf, inf.target_id, null, true)

                            modLog {
                                title = "Infraction Manually Expired"
                                color = Colours.BLURPLE

                                description = "<@${inf.target_id}> (`${inf.target_id}`) is no longer " +
                                        "${inf.infraction_type.actionText}.\n\n" +

                                        "**This infraction was expired manually.**"

                                field {
                                    name = "Moderator"
                                    value = "${message.author!!.mention} (${message.author!!.tag} / " +
                                            "`${message.author!!.id.longValue}`)"
                                }

                                footer {
                                    text = "ID: ${inf.id}"
                                }
                            }

                            message.respond(
                                    "Infraction has been manually expired: `$id`"
                            )
                        }
                    }
                }
            }

            command {
                name = "reactivate"
                aliases = arrayOf("ra")

                description = "Manually reactivate an infraction by ID, if it hasn't yet expired."

                check(
                        topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
                )

                signature<InfractionIDCommandArgs>()

                action {
                    runSuspended {
                        with(parse<InfractionIDCommandArgs>()) {
                            val inf = infQ.getInfraction(id).executeAsOneOrNull()

                            if (inf == null) {
                                message.respond(
                                        "No such infraction: `$id`"
                                )

                                return@with
                            }

                            val expires = mysqlToInstant(inf.expires)
                            val delay = getDelayFromNow(expires)

                            if (expires != null && delay < 1) {
                                message.respond(
                                        "Infraction already expired, " +
                                                "not reactivating: `$id`"
                                )

                                return@with
                            }

                            infQ.setInfractionActive(true, inf.id)

                            applyInfraction(inf, inf.target_id, expires, true)

                            modLog {
                                title = "Infraction Manually Reactivated"
                                color = Colours.BLURPLE

                                description = "<@${inf.target_id}> (`${inf.target_id}`) is once again " +
                                        "${inf.infraction_type.actionText}.\n\n" +

                                        "**This infraction was reactivated manually.**"

                                field {
                                    name = "Moderator"
                                    value = "${message.author!!.mention} (${message.author!!.tag} / " +
                                            "`${message.author!!.id.longValue}`)"
                                }

                                footer {
                                    text = "ID: ${inf.id}"
                                }
                            }

                            message.respond(
                                    "Infraction has been manually reactivated: `$id`"
                            )
                        }
                    }
                }
            }

            command {
                name = "reason"
                aliases = arrayOf("r")

                description = "Get or update the reason for a specific infraction."

                check(
                        topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
                )

                signature<InfractionReasonCommandArgs>()

                action {
                    runSuspended {
                        with(parse<InfractionReasonCommandArgs>()) {
                            val inf = infQ.getInfraction(id).executeAsOneOrNull()

                            if (inf == null) {
                                message.respond(
                                        "No such infraction: `$id`"
                                )

                                return@with
                            }

                            if (reason.isEmpty()) {
                                message.respond(
                                        "Reason for infraction `$id` is:\n" +
                                                ">>> ${inf.reason}"
                                )

                                return@with
                            }

                            val joinedReason = reason.joinToString(" ")

                            infQ.setInfractionReason(joinedReason, id)

                            message.respond(
                                    "Reason for infraction `$id` updated to:\n" +
                                            ">>> $joinedReason"
                            )

                            modLog {
                                title = "Infraction reason updated"
                                color = Colours.BLURPLE

                                description = "**Reason:** $joinedReason"

                                field {
                                    name = "Moderator"
                                    value = "${message.author!!.mention} (${message.author!!.tag} / " +
                                            "`${message.author!!.id.longValue}`)"
                                }

                                footer {
                                    text = "ID: ${inf.id}"
                                }
                            }
                        }
                    }
                }
            }

            command {
                name = "search"
                aliases = arrayOf("s", "find", "f")

                description = "Search for infractions using a set of filters.\n\n$FILTERS"

                signature = "<filter> [filter ...]"

                action {
                    runSuspended {
                        with(parse<InfractionSearchCommandArgs>()) {
                            val author = message.author!!
                            val validateMessage = validateSearchArgs(this)

                            if (validateMessage != null) {
                                message.channel.createMessage(
                                        "${author.mention} $validateMessage"
                                )

                                return@with
                            }

                            var infractions = infQ.getAllInfractions().executeAsList()

                            val userId = getMemberId(target, targetId)
                            val moderatorId = getMemberId(moderator, moderatorId)

                            if (targetName != null) {
                                // Yup, SQLDelight doesn't understand the wildcards.
                                val users = userQ.findByUsernameContains("%$targetName%").executeAsList()

                                users.forEach { user ->
                                    infractions = infractions.filter { it.target_id == user.id }
                                }
                            }

                            if (userId != null) infractions = infractions.filter { it.target_id == userId }
                            if (moderatorId != null) infractions = infractions.filter { it.moderator_id == moderatorId }
                            if (active != null) infractions = infractions.filter { it.active == active }

                            if (type != null) {
                                infractions = infractions.filter {
                                    it.infraction_type == getInfractionType(type)
                                }
                            }

                            val pages = infractions.map { infractionToString(it) }.filterNotNull()

                            if (pages.isEmpty()) {
                                message.respond("No matching infractions found.")
                            } else {
                                val paginator = Paginator(
                                        bot, message.channel, "Infractions",
                                        pages, author, PAGINATOR_TIMEOUT, true
                                )

                                paginator.send()
                            }
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
                        InfractionTypes.NICK_LOCK,
                        "Prevent a user from changing their nickname.",
                        "nick-lock",
                        arrayOf("nicklock", "nl"),
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
                        InfractionTypes.NICK_LOCK,
                        "Pardon all nick-lock infractions for a user.",
                        "un-nick-lock",
                        arrayOf("un-nicklock", "unnick-lock", "unnicklock", "unl"),
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

        // region: Special event handlers

        event<MemberUpdateEvent> {
            check(
                    inGuild(config.getGuild()),
                    topRoleLower(config.getRole(Roles.TRAINEE_MODERATOR))  // Staff should be immune
            )

            action {
                runSuspended {
                    val oldMember = it.old
                    val newMember = it.getMember()

                    logger.debug { "Checking out nick change for user: ${newMember.tag} -> ${newMember.nickname}" }

                    val infractions = infQ.getActiveInfractionsByUser(it.memberId.longValue)
                            .executeAsList()
                            .filter { it.infraction_type == InfractionTypes.NICK_LOCK }

                    if (infractions.isEmpty()) {
                        logger.debug { "User isn't nick-locked, not doing anything." }

                        return@runSuspended  // They're not nick-locked.
                    }

                    val delta = MemberDelta.from(oldMember, newMember)

                    if (delta?.nickname != null) {  // We've got the old one if there's a delta
                        val oldNick = oldMember!!.nickname ?: oldMember.username
                        val newNick = newMember.nickname ?: newMember.username
                        val memberId = it.memberId.longValue

                        if (newNick in sanctionedNickChanges.get(memberId)) {
                            logger.debug { "This was a sanctioned nickname change, not reverting." }

                            sanctionedNickChanges.remove(memberId, newNick)
                            return@runSuspended
                        }

                        logger.debug { "Reversing nickname change." }

                        sanctionedNickChanges.put(memberId, oldNick)

                        newMember.edit {
                            nickname = oldNick
                        }

                        modLog {
                            title = "Nick-lock enforced"
                            description = "Prevented nickname change for ${newMember.mention} (${newMember.tag} / " +
                                    "`${newMember.id.longValue}`)."

                            color = Colours.POSITIVE

                            footer {
                                text = "Latest matching: ${infractions.last().id}"
                            }
                        }
                    } else {  // If there's no delta, the user wasn't in the cache.
                        logger.warn { "Can't reverse nickname change for ${newMember.tag}, user not in the cache." }

                        modLog {
                            title = "Nick-lock enforcement failed"
                            description = "Failed to enforce nick-lock because user isn't in the cache:" +
                                    " ${newMember.mention} (${newMember.tag} / `${newMember.id.longValue}`)."

                            color = Colours.NEGATIVE

                            footer {
                                text = "Latest matching: ${infractions.last().id}"
                            }
                        }
                    }
                }
            }
        }

        // endregion
    }

    private fun getMemberId(member: User?, id: Long?) =
            if (member == null && id == null) {
                null
            } else if (member != null && id != null) {
                null
            } else {
                member?.id?.longValue ?: id!!
            }
}
