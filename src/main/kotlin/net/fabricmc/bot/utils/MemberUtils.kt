package net.fabricmc.bot.utils

import com.gitlab.kordlib.common.entity.Status
import com.gitlab.kordlib.core.entity.Member
import net.fabricmc.bot.enums.Emojis
import net.fabricmc.bot.extensions.EmojiExtension

/**
 * Retrieve the relevant status emoji mention for a given [Member].
 */
suspend fun Member.getStatusEmoji() = when(this.getPresenceOrNull()?.status) {
    Status.DnD -> EmojiExtension.getEmoji(Emojis.STATUS_DND)
    Status.Idle -> EmojiExtension.getEmoji(Emojis.STATUS_AWAY)
    Status.Online -> EmojiExtension.getEmoji(Emojis.STATUS_ONLINE)

    else -> EmojiExtension.getEmoji(Emojis.STATUS_OFFLINE)
}
