package net.fabricmc.bot.tags

import dev.kord.core.cache.data.EmbedData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Sealed class representing the root of a tag data structure. **/
@Suppress("EmptyClassBlock")  // ..it has to be
@Serializable
sealed class TagData {
    /** @suppress **/
    @Transient open val type: String = "unknown"
}

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
    @Transient override val type: String = "alias"

    override fun toString(): String = "Alias [target: $target]"
}

/**
 * Class representing an embed tag - a tag containing a Discord embed.
 *
 * This tag makes use of Kord's [EmbedData] class.
 *
 * @param color Optional embed color
 * @param embed Embed definition, excluding color and description
 */
@Serializable
@SerialName("embed")
class EmbedTag(
        @SerialName("colour")  // TODO: Remove this later
        val color: String? = null,

        val embed: EmbedData
) : TagData() {
    @Transient override val type: String = "embed"

    override fun toString(): String = "Embed [color: $color, embed: $embed]"
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
    @Transient override val type: String = "text"

    override fun toString(): String = "Text [N/A]"
}
