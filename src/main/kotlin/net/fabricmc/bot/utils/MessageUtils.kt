package net.fabricmc.bot.utils

import com.gitlab.kordlib.core.entity.Message
import com.kotlindiscord.kord.extensions.utils.requireGuildChannel
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.enums.Roles

/** Like [requireGuildChannel], but defaulting to the configured guild and taking a [Roles] enum value. **/
suspend fun Message.requireMainGuild(role: Roles? = null) =
        this.requireGuildChannel(if (role != null) config.getRole(role) else null, config.getGuild())
