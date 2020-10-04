package net.fabricmc.bot.extensions

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.entity.Member
import com.gitlab.kordlib.core.entity.Role
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.core.entity.channel.TextChannel
import com.gitlab.kordlib.core.event.UserUpdateEvent
import com.gitlab.kordlib.core.event.gateway.ReadyEvent
import com.gitlab.kordlib.core.event.guild.MemberJoinEvent
import com.gitlab.kordlib.core.event.guild.MemberLeaveEvent
import com.gitlab.kordlib.core.event.guild.MemberUpdateEvent
import com.gitlab.kordlib.core.event.role.RoleCreateEvent
import com.gitlab.kordlib.core.event.role.RoleDeleteEvent
import com.gitlab.kordlib.core.event.role.RoleUpdateEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.extensions.infractions.applyInfraction
import net.fabricmc.bot.extensions.infractions.getDelayFromNow
import net.fabricmc.bot.extensions.infractions.mysqlToInstant
import net.fabricmc.bot.extensions.infractions.scheduleUndoInfraction
import net.fabricmc.bot.runSuspended
import java.time.Instant

private val roles = config.db.roleQueries
private val users = config.db.userQueries
private val junction = config.db.userRoleQueries

private val logger = KotlinLogging.logger {}

private const val READY_DELAY = 10_000L

/**
 * Sync extension, in charge of keeping the database in sync with Discord.
 */
class SyncExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "sync"

    override suspend fun setup() {
        event<ReadyEvent> {
            action {
                logger.debug { "Delaying sync for 10 seconds." }
                delay(READY_DELAY)  // To ensure things are ready

                runSuspended {
                    initialSync()
                }
            }
        }

        event<RoleCreateEvent> { action { runSuspended { roleUpdated(it.role) } } }
        event<RoleUpdateEvent> { action { runSuspended { roleUpdated(it.role) } } }
        event<RoleDeleteEvent> { action { runSuspended { roleDeleted(it.roleId.longValue) } } }

        event<MemberJoinEvent> { action { runSuspended { memberJoined(it.member) } } }
        event<MemberUpdateEvent> { action { runSuspended { memberUpdated(it.getMember()) } } }
        event<MemberLeaveEvent> { action { runSuspended { memberLeft(it.user.id.longValue) } } }
        event<UserUpdateEvent> { action { runSuspended { userUpdated(it.user) } } }

        command {
            name = "sync"

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.ADMIN))
            )

            action {
                logger.debug { "Starting manual sync..." }
                val (rolesUpdated, rolesRemoved) = updateRoles()
                val (usersUpdated, usersAbsent) = updateUsers()
                val (allInfractions, expiredInfractions) = infractionSync()

                logger.debug { "Manual sync done." }
                message.channel.createEmbed {
                    title = "Sync statistics"

                    field {
                        inline = false

                        name = "Roles"
                        value = "**Updated:** $rolesUpdated | **Removed:** $rolesRemoved"
                    }

                    field {
                        inline = false

                        name = "Users"
                        value = "**Updated:** $usersUpdated | **Absent:** $usersAbsent"
                    }

                    field {
                        inline = false

                        name = "Infractions"
                        value = "**All:** $allInfractions | **Expired now:** $expiredInfractions"
                    }

                    timestamp = Instant.now()
                }
            }
        }
    }

    private suspend inline fun initialSync() {
        logger.debug { "Starting initial sync..." }

        val (rolesUpdated, rolesRemoved) = updateRoles()
        val (usersUpdated, usersAbsent) = updateUsers()
        val (allInfractions, expiredInfractions) = infractionSync()

        logger.debug { "Initial sync done." }
        (config.getChannel(Channels.ACTION_LOG) as TextChannel)
                .createEmbed {
                    title = "Sync statistics"

                    field {
                        inline = false

                        name = "Roles"
                        value = "**Updated:** $rolesUpdated | **Removed:** $rolesRemoved"
                    }

                    field {
                        inline = false

                        name = "Users"
                        value = "**Updated:** $usersUpdated | **Absent:** $usersAbsent"
                    }

                    field {
                        inline = false

                        name = "Infractions"
                        value = "**All:** $allInfractions | **Expired now:** $expiredInfractions"
                    }

                    timestamp = Instant.now()
                }
    }

    private suspend inline fun infractionSync(): Pair<Long, Int> {
        logger.debug { "Updating infractions: Getting active expirable infractions from DB" }
        val infractions = config.db.infractionQueries.getActiveExpirableInfractions().executeAsList()

        logger.debug { "Updating infractions: Getting all infractions from DB" }
        val allInfractions = config.db.infractionQueries.getInfractionCount().executeAsOne()
        var expiredInfractions = 0

        infractions.forEach {
            val memberId = it.target_id.toLong()
            val member = config.getGuild().getMemberOrNull(Snowflake(memberId))
            val expires = mysqlToInstant(it.expires)
            val delay = getDelayFromNow(expires)

            if (delay > 0) {
                if (member != null) {
                    logger.debug { "Reapplying infraction: ${it.id}" }

                    applyInfraction(it, memberId, expires)
                }
            } else {
                logger.debug { "Scheduling infraction expiry: ${it.id}" }

                scheduleUndoInfraction(memberId, it, null)  // Explicitly have no delay

                config.db.infractionQueries.setInfractionActive(false, it.id)
                expiredInfractions += 1
            }
        }

        return Pair(allInfractions, expiredInfractions)
    }

    private inline fun roleUpdated(role: Role) {
        logger.debug { "Role updated: ${role.name} (${role.id})" }

        val dbRole = roles.getRole(role.id.longValue).executeAsOneOrNull()

        if (dbRole == null) {
            roles.insertRole(role.id.longValue, role.color.rgb, role.name)
        } else {
            roles.updateRole(role.color.rgb, role.name, role.id.longValue)
        }
    }

    private inline fun roleDeleted(roleId: Long) {
        logger.debug { "Role deleted: $roleId" }

        junction.dropUserRoleByRole(roleId)
        roles.dropRole(roleId)
    }

    private suspend inline fun memberJoined(member: Member) {
        logger.debug { "Member Joined: ${member.username}#${member.discriminator} (${member.id.longValue})" }

        memberUpdated(member)

        val infractions = config.db.infractionQueries
                .getActiveInfractionsByUser(member.id.longValue)
                .executeAsList()
                .filter { it.infraction_type.expires }

        infractions.forEach {
            applyInfraction(it, member.id.longValue, null)  // Expiry already scheduled at this point
        }
    }

    private suspend inline fun memberUpdated(member: Member) {
        logger.debug { "Member updated: ${member.username}#${member.discriminator} (${member.id.longValue})" }

        val memberId = member.id.longValue
        val dbUser = users.getUser(memberId).executeAsOneOrNull()

        if (dbUser == null) {
            users.insertUser(memberId, member.avatar.url, member.discriminator, true, member.username)
        } else {
            users.updateUser(member.avatar.url, member.discriminator, true, member.username, memberId)
        }

        val currentRoles = member.roles.toList().map { it.id.longValue }
        val dbRoles = junction.getUserRoleByUser(member.id.longValue).executeAsList().map { it.role_id }

        val rolesToAdd = currentRoles.filter { !dbRoles.contains(it) }
        val rolesToRemove = dbRoles.filter { !currentRoles.contains(it) }

        rolesToAdd.forEach {
            junction.insertUserRole(it, memberId)
        }

        rolesToRemove.forEach {
            junction.dropUserRole(it, memberId)
        }
    }

    private inline fun memberLeft(userId: Long) {
        logger.debug { "User left: $userId" }

        val dbUser = users.getUser(userId).executeAsOneOrNull()

        if (dbUser != null) {
            users.updateUser(dbUser.avatarUrl, dbUser.discriminator, false, dbUser.username, dbUser.id)
        }
    }

    private suspend inline fun userUpdated(user: User) {
        logger.debug { "User updated: ${user.username}#${user.discriminator} (${user.id.longValue})" }

        val member = config.getGuild().getMemberOrNull(user.id)
        val dbUser = users.getUser(user.id.longValue).executeAsOneOrNull()

        if (dbUser == null) {
            users.insertUser(user.id.longValue, user.avatar.url, user.discriminator, member != null, user.username)
        } else {
            users.updateUser(user.avatar.url, user.discriminator, member != null, user.username, user.id.longValue)
        }
    }

    private suspend inline fun updateRoles(): Pair<Int, Int> {
        logger.debug { "Updating roles: Getting roles from DB" }
        val dbRoles = roles.getAllRoles().executeAsList().map { it.id to it }.toMap()

        logger.debug { "Updating roles: Getting roles from Discord" }
        val discordRoles = config.getGuild().roles.toList().map { it.id.longValue to it }.toMap()

        val rolesToAdd = discordRoles.keys.filter { it !in dbRoles }
        val rolesToRemove = dbRoles.keys.filter { it !in discordRoles }
        val rolesToUpdate = dbRoles.keys.filter { it in discordRoles }

        var rolesUpdated = 0

        (rolesToAdd + rolesToUpdate).forEach {
            val role = discordRoles[it] ?: error("Role suddenly disappeared from the list: $it.")
            val dbRole = dbRoles[it]

            if (
                    dbRole == null
                    || dbRole.colour != role.color.rgb
                    || dbRole.name != role.name
            ) {
                logger.debug { "Updating role: ${role.name} ($it)" }

                roleUpdated(role)
                rolesUpdated += 1
            }
        }

        rolesToRemove.forEach {
            logger.debug { "Removing role with ID: $it" }

            roleDeleted(it)
        }

        return Pair(rolesUpdated, rolesToRemove.size)
    }

    private suspend inline fun updateUsers(): Pair<Int, Int> {
        logger.debug { "Updating users: Getting users from DB" }
        val dbUsers = users.getAllUsers().executeAsList().map { it.id to it }.toMap()

        logger.debug { "Updating users: Getting users from Discord" }
        val discordUsers = config.getGuild().members.toList().map { it.id.longValue to it }.toMap()

        val usersToAdd = discordUsers.keys.filter { it !in dbUsers }
        val usersToRemove = dbUsers.keys.filter { it !in discordUsers && (dbUsers[it] ?: error("???")).present }
        val usersToUpdate = dbUsers.keys.filter { it in discordUsers }

        var usersUpdated = 0

        (usersToAdd + usersToUpdate).forEach {
            val member = discordUsers[it] ?: error("User suddenly disappeared from the list: $it.")
            val dbUser = dbUsers[it]

            val dbUserRoles = junction.getUserRoleByUser(it).executeAsList().map { role -> role.role_id }
            val discordUserRoles = member.roles.toList().map { role -> role.id.longValue }

            val rolesUpToDate = dbUserRoles.containsAll(discordUserRoles)

            if (
                    dbUser == null

                    || dbUser.avatarUrl != member.avatar.url
                    || dbUser.discriminator != member.discriminator
                    || dbUser.username != member.username

                    || !dbUser.present
                    || !rolesUpToDate
            ) {
                logger.debug { "Updating user: ${member.username}#${member.discriminator} ($it)" }

                memberUpdated(member)
                usersUpdated += 1
            }
        }

        usersToRemove.forEach {
            logger.debug { "Marking user with ID as not present: ($it)" }

            memberLeft(it)  // User isn't in discordUsers at all so we have no object
        }

        return Pair(usersUpdated, usersToRemove.size)
    }
}
