package net.fabricmc.bot.extensions

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.runSuspended

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

        event<MemberJoinEvent> { action { memberUpdated(it.member) } }
        event<MemberUpdateEvent> { action { memberUpdated(it.getMember()) } }
        event<MemberLeaveEvent> { action { memberLeft(it.user) } }
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
                }
            }
        }
    }

    private suspend fun initialSync() {
        val (rolesUpdated, rolesRemoved) = updateRoles()
        val (usersUpdated, usersAbsent) = updateUsers()

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
                }
    }

    private suspend fun roleUpdated(role: Role) = runSuspended(Dispatchers.IO) {
        val dbRole = roles.getRole(role.id.longValue).executeAsOneOrNull()

        if (dbRole == null) {
            roles.insertRole(role.id.longValue, role.color.rgb, role.name)
        } else {
            roles.updateRole(role.color.rgb, role.name, role.id.longValue)
        }
    }


    private suspend fun roleDeleted(roleId: Long) = runSuspended(Dispatchers.IO) {
        junction.dropUserRoleByRole(roleId)
        roles.dropRole(roleId)
    }

    private suspend fun memberUpdated(member: Member) = runSuspended(Dispatchers.IO) {
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

    private suspend fun memberLeft(user: User) = runSuspended(Dispatchers.IO) {
        val dbUser = users.getUser(user.id.longValue).executeAsOneOrNull()

        if (dbUser != null) {
            users.insertUser(user.id.longValue, user.avatar.url, user.discriminator, false, user.username)
        } else {
            users.updateUser(user.avatar.url, user.discriminator, false, user.username, user.id.longValue)
        }
    }

    private suspend fun userUpdated(user: User) = runSuspended(Dispatchers.IO) {
        val member = config.getGuild().getMemberOrNull(user.id)
        val dbUser = users.getUser(user.id.longValue).executeAsOneOrNull()

        if (dbUser != null) {
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

        rolesToAdd.forEach {
            val role = discordRoles[it] ?: error("Role suddenly disappeared from the list.")

            roleUpdated(role)
        }

        rolesToRemove.forEach {
            roleDeleted(it)
        }

        rolesToUpdate.forEach {
            val role = discordRoles[it] ?: error("Role suddenly disappeared from the list.")

            roleUpdated(role)
        }

        return Pair(rolesToAdd.size + rolesToUpdate.size, rolesToRemove.size)
    }

    private suspend fun updateUsers(): Pair<Int, Int> {
        val dbUsers = users.getAllUsers().executeAsList().map { it.id to it }.toMap()
        val discordUsers = config.getGuild().members.toList().map { it.id.longValue to it }.toMap()

        val usersToAdd = discordUsers.keys.filter { it !in dbUsers }
        val usersToRemove = dbUsers.keys.filter { it !in discordUsers }
        val usersToUpdate = dbUsers.keys.filter { it in discordUsers }

        usersToAdd.forEach {
            val member = discordUsers[it] ?: error("User suddenly disappeared from the list.")

            memberUpdated(member)
        }

        usersToRemove.forEach {
            val member = discordUsers[it] ?: error("User suddenly disappeared from the list.")

            memberLeft(member)
        }

        usersToUpdate.forEach {
            val member = discordUsers[it] ?: error("User suddenly disappeared from the list.")

            memberUpdated(member)
        }

        return Pair(usersToAdd.size + usersToUpdate.size, usersToRemove.size)
    }
}
