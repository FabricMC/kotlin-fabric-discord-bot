package net.fabricmc.bot.utils

import dev.kord.common.entity.PresenceStatus
import dev.kord.core.entity.Member
import net.fabricmc.bot.enums.Emojis
import net.fabricmc.bot.extensions.EmojiExtension

/**
 * Retrieve the relevant status emoji mention for a given [Member].
 */
suspend fun Member.getStatusEmoji() = when (this.getPresenceOrNull()?.status) {
    PresenceStatus.DoNotDisturb -> EmojiExtension.getEmoji(Emojis.STATUS_DND)
    PresenceStatus.Idle -> EmojiExtension.getEmoji(Emojis.STATUS_AWAY)
    PresenceStatus.Online -> EmojiExtension.getEmoji(Emojis.STATUS_ONLINE)

    else -> EmojiExtension.getEmoji(Emojis.STATUS_OFFLINE)
}
