package net.fabricmc.bot.extensions.infractions

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.behavior.ban
import kotlinx.coroutines.launch
import net.fabricmc.bot.bot
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.database.Infraction
import net.fabricmc.bot.enums.InfractionTypes
import net.fabricmc.bot.enums.Roles
import java.time.Instant

/**
 * Apply an infraction action based on the type of infraction passed to the function.
 *
 * This will do whatever is needed to ensure the infraction is correctly applied on the server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
fun applyInfraction(infraction: Infraction, id: Snowflake,
                    expires: Instant?, manual: Boolean = false) = bot.kord.launch {

//    So, there a problem in the Kotlin compiler. If we make this suspending (and
//    thus make InfractionSetCommand.infrAction suspending), we run into a compiler bug that results
//    in the build failing due to the wrong bytecode being generated.
//
//    We're using the `bot.kord.launch` workaround for the time being, until JetBrains gets around to their
//    compiler rewrite.

    when (infraction.infraction_type) {
        InfractionTypes.BAN -> ban(infraction, id, expires, manual)
        InfractionTypes.KICK -> kick(infraction, id, expires, manual)
        InfractionTypes.META_MUTE -> metaMute(infraction, id, expires, manual)
        InfractionTypes.MUTE -> mute(infraction, id, expires, manual)
        InfractionTypes.NICK_LOCK -> nickLock(infraction, id, expires, manual)
        InfractionTypes.NOTE -> doNothing(infraction, id, expires, manual)
        InfractionTypes.REACTION_MUTE -> reactionMute(infraction, id, expires, manual)
        InfractionTypes.REQUESTS_MUTE -> requestsMute(infraction, id, expires, manual)
        InfractionTypes.SUPPORT_MUTE -> supportMute(infraction, id, expires, manual)
        InfractionTypes.WARN -> doNothing(infraction, id, expires, manual)
    }
}

/**
 * Pardon an infraction based on the type of infraction passed to the function.
 *
 * This will do whatever is needed to ensure the infraction is correctly applied on the server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
@Suppress("UnusedPrivateMember")
fun pardonInfraction(infraction: Infraction, id: Snowflake,
                     expires: Instant?, manual: Boolean = true) = bot.kord.launch {
    scheduleUndoInfraction(id, infraction, null, manual)
}

/**
 * Apply an ban infraction on the Discord server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param reason The reason given for this infraction.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
@Suppress("UnusedPrivateMember")
suspend fun ban(infraction: Infraction, id: Snowflake, expires: Instant?, manual: Boolean = false) {
    // Upstream issue means we can't safely pass in the ban reason
//    config.getGuild().ban(id) { this.reason = infraction.reason }
    config.getGuild().ban(id) { this.reason = "Infraction: ${infraction.id}" }

    unbanAt(id, infraction, expires ?: return, manual)
}

/**
 * Apply a mute infraction on the Discord server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param reason The reason given for this infraction.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
@Suppress("UnusedPrivateMember")
suspend fun mute(infraction: Infraction, id: Snowflake, expires: Instant?, manual: Boolean = false) {
    config.getGuild()
            .getMemberOrNull(id)
            ?.addRole(config.getRoleSnowflake(Roles.MUTED))

    unMuteAt(id, infraction, expires ?: return, manual)
}

/**
 * Apply a meta-mute infraction on the Discord server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param reason The reason given for this infraction.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
@Suppress("UnusedPrivateMember")
suspend fun metaMute(infraction: Infraction, id: Snowflake, expires: Instant?, manual: Boolean = false) {
    config.getGuild()
            .getMemberOrNull(id)
            ?.addRole(config.getRoleSnowflake(Roles.NO_META))

    unMetaMuteAt(id, infraction, expires ?: return, manual)
}

/**
 * Apply a reaction-mute infraction on the Discord server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param reason The reason given for this infraction.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
@Suppress("UnusedPrivateMember")
suspend fun reactionMute(infraction: Infraction, id: Snowflake, expires: Instant?, manual: Boolean = false) {
    config.getGuild()
            .getMemberOrNull(id)
            ?.addRole(config.getRoleSnowflake(Roles.NO_REACTIONS))

    unReactionMuteAt(id, infraction, expires ?: return, manual)
}

/**
 * Apply a requests-mute infraction on the Discord server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param reason The reason given for this infraction.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
@Suppress("UnusedPrivateMember")
suspend fun requestsMute(infraction: Infraction, id: Snowflake, expires: Instant?, manual: Boolean = false) {
    config.getGuild()
            .getMemberOrNull(id)
            ?.addRole(config.getRoleSnowflake(Roles.NO_REQUESTS))

    unRequestsMuteAt(id, infraction, expires ?: return, manual)
}

/**
 * Apply a support-mute infraction on the Discord server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param reason The reason given for this infraction.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
@Suppress("UnusedPrivateMember")
suspend fun supportMute(infraction: Infraction, id: Snowflake, expires: Instant?, manual: Boolean = false) {
    config.getGuild()
            .getMemberOrNull(id)
            ?.addRole(config.getRoleSnowflake(Roles.NO_SUPPORT))

    unSupportMuteAt(id, infraction, expires ?: return, manual)
}

/**
 * Apply a support-mute infraction on the Discord server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param reason The reason given for this infraction.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
@Suppress("UnusedPrivateMember")
fun nickLock(infraction: Infraction, id: Snowflake, expires: Instant?, manual: Boolean = false) {
    unNickLockAt(id, infraction, expires ?: return, manual)
}

/**
 * Apply a kick infraction on the Discord server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param reason The reason given for this infraction.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
@Suppress("UnusedPrivateMember")
suspend fun kick(infraction: Infraction, id: Snowflake, expires: Instant?, manual: Boolean = false) {
    config.getGuild().kick(id, "Infraction: ${infraction.id}")
}

/**
 * Do nothing. This is for infractions that require no action to be taken on the server.
 *
 * @param id The ID of the user this infraction applies to.
 * @param expires An [Instant] representing when this infraction should expire, if it does. Null otherwise.
 * @param infraction The [Infraction] object from the database.
 */
@Suppress("UnusedPrivateMember")
fun doNothing(infraction: Infraction, id: Snowflake, expires: Instant?, manual: Boolean = false) {
    // Literally do nothing. Not every infraction requires an action on Discord.
}
