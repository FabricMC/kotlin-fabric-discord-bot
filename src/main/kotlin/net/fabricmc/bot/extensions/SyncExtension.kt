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
import kotlinx.coroutines.flow.toList
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

/**
 * Sync extension, in charge of keeping the database in sync with Discord.
 */
class SyncExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "sync"

    override suspend fun setup() {
        event<ReadyEvent> { action { initialSync() } }

        event<RoleCreateEvent> { action { roleUpdated(it.role) } }
        event<RoleUpdateEvent> { action { roleUpdated(it.role) } }
        event<RoleDeleteEvent> { action { roleDeleted(it.roleId.longValue) } }

        event<MemberJoinEvent> { action { memberJoined(it.member) } }
        event<MemberUpdateEvent> { action { memberUpdated(it.getMember()) } }
        event<MemberLeaveEvent> { action { memberLeft(it.user.id.longValue) } }
        event<UserUpdateEvent> { action { userUpdated(it.user) } }

        command {
            name = "sync"

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.ADMIN))
            )

            action {
                val (rolesUpdated, rolesRemoved) = updateRoles()
                val (usersUpdated, usersAbsent) = updateUsers()
                val (allInfractions, expiredInfractions) = infractionSync()

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

    private suspend fun initialSync() {
        val (rolesUpdated, rolesRemoved) = updateRoles()
        val (usersUpdated, usersAbsent) = updateUsers()
        val (allInfractions, expiredInfractions) = infractionSync()

        (config.getChannel(Channels.MODERATOR_LOG) as TextChannel)
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

    private suspend fun infractionSync() = runSuspended {
        val infractions = config.db.infractionQueries.getActiveExpirableInfractions().executeAsList()
        val allInfractions = config.db.infractionQueries.getInfractionCount().executeAsOne()
        var expiredInfractions = 0

        infractions.forEach {
            val memberId = it.target_id.toLong()
            val member = config.getGuild().getMemberOrNull(Snowflake(memberId))
            val expires = mysqlToInstant(it.expires)
            val delay = getDelayFromNow(expires)

            if (delay > 0) {
                if (member != null) {
                    applyInfraction(it, memberId, expires)
                }
            } else {
                scheduleUndoInfraction(memberId, it, null)  // Explicitly have no delay

                config.db.infractionQueries.setInfractionActive(false, it.id)
                expiredInfractions += 1
            }
        }

        Pair(allInfractions, expiredInfractions)
    }

    private suspend fun roleUpdated(role: Role) = runSuspended {
        val dbRole = roles.getRole(role.id.longValue).executeAsOneOrNull()

        if (dbRole == null) {
            roles.insertRole(role.id.longValue, role.color.rgb, role.name)
        } else {
            roles.updateRole(role.color.rgb, role.name, role.id.longValue)
        }
    }

    private suspend fun roleDeleted(roleId: Long) = runSuspended {
        junction.dropUserRoleByRole(roleId)
        roles.dropRole(roleId)
    }

    private suspend fun memberJoined(member: Member) = runSuspended {
        memberUpdated(member)

        val infractions = config.db.infractionQueries
                .getActiveExpirableInfractionsByUser(member.id.longValue)
                .executeAsList()

        infractions.forEach {
            applyInfraction(it, member.id.longValue, null)  // Expiry already scheduled at this point
        }
    }

    private suspend fun memberUpdated(member: Member) = runSuspended {
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

    private suspend fun memberLeft(userId: Long) = runSuspended {
        val user = bot.kord.getUser(Snowflake(userId))
        val dbUser = users.getUser(userId).executeAsOneOrNull()

        if (dbUser == null) {
            if (user != null) {
                users.insertUser(userId, user.avatar.url, user.discriminator, false, user.username)
            }
        } else {
            users.updateUser(dbUser.avatarUrl, dbUser.discriminator, false, dbUser.username, dbUser.id)
        }
    }

    private suspend fun userUpdated(user: User) = runSuspended {
        val member = config.getGuild().getMemberOrNull(user.id)
        val dbUser = users.getUser(user.id.longValue).executeAsOneOrNull()

        if (dbUser == null) {
            users.insertUser(user.id.longValue, user.avatar.url, user.discriminator, member != null, user.username)
        } else {
            users.updateUser(user.avatar.url, user.discriminator, member != null, user.username, user.id.longValue)
        }
    }

    private suspend fun updateRoles(): Pair<Int, Int> {
        val dbRoles = roles.getAllRoles().executeAsList().map { it.id to it }.toMap()
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
                roleUpdated(role)
                rolesUpdated += 1
            }
        }

        rolesToRemove.forEach {
            roleDeleted(it)
        }

        return Pair(rolesUpdated, rolesToRemove.size)
    }

    private suspend fun updateUsers(): Pair<Int, Int> = runSuspended {
        val dbUsers = users.getAllUsers().executeAsList().map { it.id to it }.toMap()
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
                memberUpdated(member)
                usersUpdated += 1
            }
        }

        usersToRemove.forEach {
            memberLeft(it)  // User isn't in discordUsers at all so we have no object
        }

        Pair(usersUpdated, usersToRemove.size)
    }
}
