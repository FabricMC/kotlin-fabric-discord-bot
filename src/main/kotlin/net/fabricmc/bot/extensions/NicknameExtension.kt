package net.fabricmc.bot.extensions

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Member
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.guild.MemberUpdateEvent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import net.fabricmc.bot.conf.config

private val NAME_REGEX = Regex("^[!.\"+(-]")

/**
 * Nickname extension, manages nickname and unhoist guild members.
 */
class NicknameExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name: String = "nickname"

    override suspend fun setup() {
        event<ReadyEvent> {
            action {
                val guild = event.getGuilds().firstOrNull { guild -> guild == config.getGuild() }

                guild?.members?.filter { member -> NAME_REGEX.matches(member.displayName) }?.collect(::unhoistUser)
            }
        }

        event<MemberJoinEvent> {
            check(inGuild(config.getGuild()))
            check { NAME_REGEX.matches(it.member.displayName) }

            action { unhoistUser(event.member) }
        }

        event<MemberUpdateEvent> {
            check(inGuild(config.getGuild()))
            check { if (it.old != null) NAME_REGEX.matches(it.old!!.displayName) else false }

            action { unhoistUser(event.member) }
        }
    }

    /**
     * Renames the member to "Z ; dumb hoist name".
     * The "Z ;" is to put them at the end of the alphanumerical member list.
     * @param member The member to rename.
     */
    suspend fun unhoistUser(member: Member) {
        member.edit { nickname = "Z ; dumb hoist name" }
    }
}
