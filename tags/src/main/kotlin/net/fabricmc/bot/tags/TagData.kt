package net.fabricmc.bot.tags

import com.gitlab.kordlib.core.cache.data.EmbedData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sealed class representing the root of a tag data structure. **/
@Suppress("EmptyClassBlock")  // ..it has to be
@Serializable
sealed class TagData {}

/**
 * Class representing an alias tag - a tag that points at another tag.
 *
 * @param target The tag this alias is pointing at
 */
@Serializable
@SerialName("alias")
class AliasTag(
        val target: String
) : TagData() {
    override fun toString(): String = "Alias [target: $target]"
}

/**
 * Class representing an embed tag - a tag containing a Discord embed.
 *
 * This tag makes use of Kord's [EmbedData] class.
 *
 * @param colour Optional embed colour
 * @param embed Embed definition, excluding colour and description
 */
@Serializable
@SerialName("embed")
class EmbedTag(
        val colour: String? = null,
        val embed: EmbedData
) : TagData() {
    override fun toString(): String = "Embed [color: $colour, embed: $embed]"
}

/**
 * Class representing a message tag - a tag containing a standard Discord message.
 *
 * @param webhook Whether to send this tag as a webhook.
 */
@Serializable
@SerialName("text")
class TextTag(
        val webhook: Boolean = true  // Not used right now
): TagData() {
    override fun toString(): String = "Text [N/A]"
}
