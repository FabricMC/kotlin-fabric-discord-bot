package net.fabricmc.bot.tags

/**
 * Class representing a parsed tag.
 *
 * @param name Tag name
 * @param data Parsed tag data
 * @param markdown Extracted markdown content
 */
data class Tag(val name: String, val data: TagData, val markdown: String? = null)
