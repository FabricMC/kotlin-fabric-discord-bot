package net.fabricmc.bot.utils

import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.channel.GuildMessageChannel
import com.kotlindiscord.kord.extensions.utils.requireChannel
import com.kotlindiscord.kord.extensions.utils.requireGuildChannel
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.enums.Roles

private const val DELETE_DELAY = 1000L * 30L  // 30 seconds

/** Like [requireGuildChannel], but defaulting to the configured guild and taking a [Roles] enum value. **/
suspend fun Message.requireMainGuild(role: Roles? = null) =
        this.requireGuildChannel(if (role != null) config.getRole(role) else null, config.getGuild())

/** Like [requireBotChannel], but defaulting to the configured bot channel and defaulting to the trainee mod role. **/
suspend fun Message.requireBotChannel(
        role: Roles? = Roles.TRAINEE_MODERATOR,
        delay: Long = DELETE_DELAY,
        allowDm: Boolean = true,
        deleteOriginal: Boolean = true,
        deleteResponse: Boolean = true
) =
        this.requireChannel(
                config.getChannel(Channels.BOT_COMMANDS) as GuildMessageChannel,
                if (role != null) config.getRole(role) else null,
                delay,
                allowDm,
                deleteOriginal,
                deleteResponse
        )
