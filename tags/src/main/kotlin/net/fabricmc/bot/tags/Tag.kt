package net.fabricmc.bot.tags

/**
 * Class representing a parsed tag.
 *
 * @param data Parsed tag data
 * @param markdown Extracted markdown content
 */
data class Tag(
        val data: TagData,
        val markdown: String? = null
)
