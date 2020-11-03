package net.fabricmc.bot.tags

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import mu.KotlinLogging
import java.io.File
import java.nio.file.Path

private const val SEPARATOR = "\n---\n"
private val format = Yaml(configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property))
private val logger = KotlinLogging.logger {}

/**
 * Tags parser and cache. In charge of loading and keeping track of tags.
 *
 * Tags are Markdown files, containing a YAML front matter and Markdown description separated by three dashes (---).
 *
 * @param rootPath Root path containing all of the tags.
 */
class TagParser(private val rootPath: String) {
    /** Mapping of all loaded tags. **/
    val tags: MutableMap<String, Tag> = mutableMapOf()

    /**
     * Load all tags up from the root path. Overwrites any already-loaded tags.
     *
     * @return A map of tag name to error, if any errors happened while tags were being loaded.
     */
    fun loadAll(infoLogging: Boolean = false): MutableMap<String, String> {
        tags.clear()

        val root = File(rootPath)
        val rootPathNormalised = root.toString()
        val errors: MutableMap<String, String> = mutableMapOf()

        logger.debug { "Loading tags from $rootPathNormalised" }

        for (file in root.walkBottomUp()) {
            val path = file.withoutPrefix(rootPathNormalised)

            if (path.endsWith(".md")) {
                val tagName = path.substring(1).substringBeforeLast(".")

                val (tag, error) = loadTag(tagName)

                if (error == null) {
                    if (infoLogging) {
                        logger.info { "Tag loaded: $tagName" }
                    } else {
                        logger.debug { "Tag loaded: $tagName" }
                    }

                    tags[tagName.toLowerCase()] = tag!!
                } else {
                    errors[tagName] = error
                }
            }
        }

        logger.info { "${tags.size} tags loaded." }

        logger.debug {
            tags.map { "${it.key} -> ${it.value}" }.joinToString("\n\n")
        }

        return errors
    }

    /**
     * Load a specific tag by name.
     *
     * @return Pair of Tag object (if it loaded properly) and error string (if it failed to load).
     */
    fun loadTag(name: String): Pair<Tag?, String?> {
        val file = Path.of(rootPath).resolve("$name.md").toFile()

        logger.debug { "Loading tag: $name" }

        if (!file.exists()) {
            logger.error { "Tag '$name' does not exist." }
            return Pair(null, "Tag '$name' does not exist.")
        }

        val content = file.readText().replace("\r", "")

        if (!content.contains(SEPARATOR)) {
            logger.error { "Tag '$name' does not contain the section separator, '---'." }
            return Pair(null, "Tag '$name' does not contain the section separator, '---'.")
        }

        val yaml = content.substringBefore(SEPARATOR)
        val markdown = content.substringAfter(SEPARATOR).trim()

        @Suppress("TooGenericExceptionCaught")
        val tagData = try {
            format.decodeFromString(TagData.serializer(), yaml)
        } catch (t: Throwable) {
            logger.error(t) { "Tag '$name' does not contain a valid YAML front matter." }
            return Pair(null, "Tag '$name' does not contain a valid YAML front matter.")
        }

        val tag = Tag(name.toLowerCase(), tagData, markdown)

        return Pair(tag, null)
    }

    /**
     * Get a loaded tag from the cache.
     *
     * @return Tag object, if it exists - null otherwise
     */
    fun getTag(name: String) = tags[name.toLowerCase()]

    /**
     * Remove a loaded tag from the cache.
     *
     * @return True if the tag existed (and thus was removed), False otherwise
     */
    fun removeTag(name: String): Boolean {
        tags.remove(name.toLowerCase()) ?: return false

        return true
    }
}
