package net.fabricmc.bot.tags

/**
 * Class representing a parsed tag.
 *
 * @param name Tag name
 * @param nonLoweredName Tag name, but not lowered for users
 * @param data Parsed tag data
 * @param markdown Extracted markdown content
 */
data class Tag(
        val name: String,
        val nonLoweredName: String,
        val data: TagData,
        val markdown: String? = null
)
