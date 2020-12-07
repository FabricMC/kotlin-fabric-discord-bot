package net.fabricmc.bot.extensions

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kord.core.entity.Role
import dev.kord.core.entity.User
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.event.guild.MemberUpdateEvent
import dev.kord.core.event.role.RoleCreateEvent
import dev.kord.core.event.role.RoleDeleteEvent
import dev.kord.core.event.role.RoleUpdateEvent
import dev.kord.core.event.user.UserUpdateEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.respond
import com.kotlindiscord.kord.extensions.utils.runSuspended
import io.sentry.Breadcrumb
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.extensions.infractions.applyInfraction
import net.fabricmc.bot.extensions.infractions.getDelayFromNow
import net.fabricmc.bot.extensions.infractions.mysqlToInstant
import net.fabricmc.bot.extensions.infractions.scheduleUndoInfraction
import net.fabricmc.bot.utils.actionLog
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
                logger.info { "Delaying sync for 10 seconds." }
                delay(READY_DELAY)  // To ensure things are ready
                logger.info { "Beginning sync." }

                runSuspended {
                    initialSync(breadcrumbs)
                }
            }
        }

        event<RoleCreateEvent> { action { runSuspended { roleUpdated(event.role, breadcrumbs) } } }
        event<RoleUpdateEvent> { action { runSuspended { roleUpdated(event.role, breadcrumbs) } } }
        event<RoleDeleteEvent> { action { runSuspended { roleDeleted(event.roleId, breadcrumbs) } } }

        event<MemberJoinEvent> { action { runSuspended { memberJoined(event.member, breadcrumbs) } } }
        event<MemberUpdateEvent> { action { runSuspended { memberUpdated(event.member, breadcrumbs) } } }
        event<MemberLeaveEvent> { action { runSuspended { memberLeft(event.user.id, breadcrumbs) } } }
        event<UserUpdateEvent> { action { runSuspended { userUpdated(event.user, breadcrumbs) } } }

        command {
            name = "sync"
            description = "Manually synchronise users and roles to the database."

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
                message.respond {
                    embed {
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
    }

    private suspend inline fun initialSync(breadcrumbs: MutableList<Breadcrumb>? = null) {
        logger.debug { "Starting initial sync..." }

        val (rolesUpdated, rolesRemoved) = updateRoles(breadcrumbs)
        val (usersUpdated, usersAbsent) = updateUsers(breadcrumbs)
        val (allInfractions, expiredInfractions) = infractionSync(breadcrumbs)

        logger.debug { "Initial sync done." }
        actionLog {
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
        }
    }

    private suspend inline fun infractionSync(breadcrumbs: MutableList<Breadcrumb>? = null): Pair<Long, Int> {
        logger.debug { "Updating infractions: Getting active expirable infractions from DB" }
        val infractions = config.db.infractionQueries.getActiveExpirableInfractions().executeAsList()

        logger.debug { "Updating infractions: Getting all infractions from DB" }
        val allInfractions = config.db.infractionQueries.getInfractionCount().executeAsOne()
        var expiredInfractions = 0

        breadcrumbs?.add(
            bot.sentry.createBreadcrumb(
                category = "sync.users",
                type = "debug",

                message = "Synchronising infractions",
                data = mapOf(
                    "infractions.active-expirable" to infractions.size,
                    "infractions.total" to allInfractions
                )
            )
        )

        infractions.forEach {
            val memberId = Snowflake(it.target_id)
            val member = config.getGuild().getMemberOrNull(memberId)
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

    private inline fun roleUpdated(role: Role, breadcrumbs: MutableList<Breadcrumb>? = null) {
        logger.debug { "Role updated: ${role.name} (${role.id})" }

        val dbRole = roles.getRole(role.id.value).executeAsOneOrNull()

        breadcrumbs?.add(
            bot.sentry.createBreadcrumb(
                category = "sync.roles",
                type = "debug",

                message = "Updating role",
                data = mapOf(
                    "inserting" to (dbRole == null),
                    "role.id" to role.id.asString,
                    "role.name" to role.name
                )
            )
        )

        if (dbRole == null) {
            roles.insertRole(role.id.value, role.color.rgb, role.name)
        } else {
            roles.updateRole(role.color.rgb, role.name, role.id.value)
        }
    }

    private inline fun roleDeleted(roleId: Snowflake, breadcrumbs: MutableList<Breadcrumb>? = null) {
        logger.debug { "Role deleted: ${roleId.value}" }

        breadcrumbs?.add(
            bot.sentry.createBreadcrumb(
                category = "sync.roles",
                type = "debug",

                message = "Removing role",
                data = mapOf(
                    "role.id" to roleId.asString
                )
            )
        )

        junction.dropUserRoleByRole(roleId.value)
        roles.dropRole(roleId.value)
    }

    private suspend inline fun memberJoined(member: Member, breadcrumbs: MutableList<Breadcrumb>? = null) {
        logger.debug { "Member Joined: ${member.tag} (${member.id})" }

        memberUpdated(member, breadcrumbs)

        val infractions = config.db.infractionQueries
                .getActiveInfractionsByUser(member.id.value)
                .executeAsList()
                .filter { it.infraction_type.expires }


        breadcrumbs?.add(
            bot.sentry.createBreadcrumb(
                category = "sync.members",
                type = "debug",

                message = "Reapplying member infractions",
                data = mapOf(
                    "member.id" to member.id.asString,
                    "member.tag" to member.tag,
                    "infractions" to infractions.size
                )
            )
        )

        infractions.forEach {
            applyInfraction(it, member.id, null)  // Expiry already scheduled at this point
        }
    }

    private suspend inline fun memberUpdated(member: Member, breadcrumbs: MutableList<Breadcrumb>? = null) {
        logger.debug { "Member updated: ${member.tag} (${member.id})" }

        val memberId = member.id
        val dbUser = users.getUser(memberId.value).executeAsOneOrNull()

        breadcrumbs?.add(
            bot.sentry.createBreadcrumb(
                category = "sync.members",
                type = "debug",

                message = "Updating member",
                data = mapOf(
                    "member.id" to member.id.asString,
                    "member.tag" to member.tag,

                    "insert" to (dbUser == null)
                )
            )
        )

        if (dbUser == null) {
            users.insertUser(memberId.value, member.avatar.url, member.discriminator, true, member.username)
        } else {
            users.updateUser(member.avatar.url, member.discriminator, true, member.username, memberId.value)
        }

        val currentRoles = member.roles.toList().map { it.id }
        val dbRoles = junction.getUserRoleByUser(member.id.value).executeAsList().map { it.role_id }

        val rolesToAdd = currentRoles.filter { !dbRoles.contains(it) }.map { it.value }
        val rolesToRemove = dbRoles.filter { !currentRoles.contains(it) }

        breadcrumbs?.add(
            bot.sentry.createBreadcrumb(
                category = "sync.roles",
                type = "debug",

                message = "Updating member roles",
                data = mapOf(
                    "roles.adding" to rolesToAdd.size,
                    "roles.removing" to rolesToRemove.size
                )
            )
        )

        rolesToAdd.forEach {
            junction.insertUserRole(it, memberId.value)
        }

        rolesToRemove.forEach {
            junction.dropUserRole(it, memberId.value)
        }
    }

    private inline fun memberLeft(userId: Snowflake, breadcrumbs: MutableList<Breadcrumb>? = null) {
        logger.debug { "User left: $userId" }

        val dbUser = users.getUser(userId.value).executeAsOneOrNull()

        breadcrumbs?.add(
            bot.sentry.createBreadcrumb(
                category = "sync.users",
                type = "debug",

                message = "Marking user as absent",
                data = mapOf(
                    "user.id" to userId.asString
                )
            )
        )

        if (dbUser != null) {
            users.updateUser(dbUser.avatarUrl, dbUser.discriminator, false, dbUser.username, dbUser.id)
        }
    }

    private suspend inline fun userUpdated(user: User, breadcrumbs: MutableList<Breadcrumb>? = null) {
        logger.debug { "User updated: ${user.tag} (${user.id})" }

        val member = config.getGuild().getMemberOrNull(user.id)
        val dbUser = users.getUser(user.id.value).executeAsOneOrNull()

        breadcrumbs?.add(
            bot.sentry.createBreadcrumb(
                category = "sync.users",
                type = "debug",

                message = "Updating user",
                data = mapOf(
                    "user.id" to user.id.asString,
                    "user.tag" to user.tag,

                    "insert" to (dbUser == null)
                )
            )
        )

        if (dbUser == null) {
            users.insertUser(user.id.value, user.avatar.url, user.discriminator, member != null, user.username)
        } else {
            users.updateUser(user.avatar.url, user.discriminator, member != null, user.username, user.id.value)
        }
    }

    private suspend inline fun updateRoles(breadcrumbs: MutableList<Breadcrumb>? = null): Pair<Int, Int> {
        logger.debug { "Updating roles: Getting roles from DB" }
        val dbRoles = roles.getAllRoles().executeAsList().map { it.id to it }.toMap()

        logger.debug { "Updating roles: Getting roles from Discord" }
        val discordRoles = config.getGuild().roles.toList().map { it.id.value to it }.toMap()

        logger.info { "Syncing ${discordRoles.size} roles." }

        val rolesToAdd = discordRoles.keys.filter { it !in dbRoles }
        val rolesToRemove = dbRoles.keys.filter { it !in discordRoles }
        val rolesToUpdate = dbRoles.keys.filter { it in discordRoles }

        breadcrumbs?.add(
            bot.sentry.createBreadcrumb(
                category = "sync.roles",
                type = "debug",

                message = "Synchronising roles",
                data = mapOf(
                    "roles.database" to dbRoles.size,
                    "roles.discord" to discordRoles.size,

                    "roles.adding" to rolesToAdd.size,
                    "roles.removing" to rolesToRemove.size,
                    "roles.updating" to rolesToUpdate.size
                )
            )
        )

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

            roleDeleted(Snowflake(it))
        }

        return Pair(rolesUpdated, rolesToRemove.size)
    }

    private suspend inline fun updateUsers(breadcrumbs: MutableList<Breadcrumb>? = null): Pair<Int, Int> {
        logger.debug { "Updating users: Getting users from DB" }
        val dbUsers = users.getAllUsers().executeAsList().map { it.id to it }.toMap()

        logger.debug { "Updating users: Getting users from Discord" }
        val discordUsers = config.getGuild().members.toList().map { it.id.value to it }.toMap()

        logger.info { "Syncing ${discordUsers.size} members." }

        val usersToAdd = discordUsers.keys.filter { it !in dbUsers }
        val usersToRemove = dbUsers.keys.filter { it !in discordUsers && (dbUsers[it] ?: error("???")).present }
        val usersToUpdate = dbUsers.keys.filter { it in discordUsers }

        breadcrumbs?.add(
            bot.sentry.createBreadcrumb(
                category = "sync.users",
                type = "debug",

                message = "Synchronising users",
                data = mapOf(
                    "users.database" to dbUsers.size,
                    "users.discord" to discordUsers.size,

                    "users.adding" to usersToAdd.size,
                    "users.removing" to usersToRemove.size,
                    "users.updating" to usersToUpdate.size
                )
            )
        )

        var usersUpdated = 0

        (usersToAdd + usersToUpdate).forEach {
            val member = discordUsers[it] ?: error("User suddenly disappeared from the list: $it.")
            val dbUser = dbUsers[it]

            val dbUserRoles = junction.getUserRoleByUser(it).executeAsList().map { role -> role.role_id }
            val discordUserRoles = member.roles.toList().map { role -> role.id.value }

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

            memberLeft(Snowflake(it))  // User isn't in discordUsers at all so we have no object
        }

        return Pair(usersUpdated, usersToRemove.size)
    }
}
