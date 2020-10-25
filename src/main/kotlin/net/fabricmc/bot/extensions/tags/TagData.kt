package net.fabricmc.bot.extensions.tags

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.gitlab.kordlib.core.cache.data.EmbedData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class TagData {}

@Serializable
@SerialName("alias")
class AliasTag(
        val target: String
) : TagData()

@Serializable
@SerialName("embed")
class EmbedTag(
        val attachments: List<String> = listOf(),
        val colour: String? = null,
        val embed: EmbedData
) : TagData()

@Serializable
@SerialName("message")
class MessageTag(
        val attachments: List<String> = listOf()
) : TagData()


fun main() {
    val format = Yaml(configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property))

    val input = """
type: alias

target: other
    """

    val result = format.decodeFromString(TagData.serializer(), input)

    println(result)
}
