package net.fabricmc.bot.tags

/**
 * Class representing a parsed tag.
 *
 * @param name Tag name, normalised (lowered and underscores converted to dashes)
 * @param suppliedName Tag name, but without normalisation
 * @param data Parsed tag data
 * @param markdown Extracted markdown content
 */
data class Tag(
        val name: String,
        val suppliedName: String,
        val data: TagData,
        val markdown: String? = null
)
