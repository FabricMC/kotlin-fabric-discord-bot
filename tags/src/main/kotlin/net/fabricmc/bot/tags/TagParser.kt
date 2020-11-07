package net.fabricmc.bot.tags

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import mu.KotlinLogging
import java.io.File
import java.nio.file.Path

private val CAPS = "[A-Z]".toRegex()
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

    /** Suffix for all tag files. **/
    val suffix = ".ytag"

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

            if (path.endsWith(suffix)) {
                val tagName = path.substring(1).substringBeforeLast(".")

                val (tag, error) = loadTag(tagName)

                if (error == null) {
                    if (infoLogging) {
                        logger.info { "Tag loaded: $tagName" }
                    } else {
                        logger.debug { "Tag loaded: $tagName" }
                    }

                    tags[tag!!.name] = tag
                } else {
                    errors[tagName] = error
                }
            }
        }

        val badAliases = mutableListOf<String>()

        for (entry in tags) {
            if (entry.value.data is AliasTag) {
                val data = entry.value.data as AliasTag
                val target = getTag(data.target)

                if (target == null) {
                    badAliases.add(entry.key)

                    errors[entry.key] = "Alias '${entry.key}' points to a tag that doesn't exist: '${data.target}'"
                    logger.error { "Alias '${entry.key}' points to a tag that doesn't exist: '${data.target}'" }
                } else if (target.data is AliasTag) {
                    badAliases.add(entry.key)

                    errors[entry.key] = "Alias '${entry.key}' points to another alias: '${data.target}'"
                    logger.error { "Alias '${entry.key}' points to another alias: '${data.target}'" }
                }
            }
        }

        badAliases.forEach { tags.remove(it) }

        logger.info { "${tags.size} tags loaded." }

        logger.trace {
            "All tags:\n" + tags.map { "${it.key} -> ${it.value}" }.joinToString("\n\n")
        }

        return errors
    }

    /**
     * Load a specific tag by name.
     *
     * @return Pair of Tag object (if it loaded properly) and error string (if it failed to load).
     */
    fun loadTag(name: String): Pair<Tag?, String?> {
        val file = Path.of(rootPath).resolve("$name$suffix").toFile()

        logger.debug { "Loading tag: $name" }

        if (!file.exists()) {
            logger.error { "Tag '$name' does not exist." }
            return Pair(null, "Tag '$name' does not exist.")
        }

        if (name.contains('_')) {
            logger.warn { "Tag '$name' contains an underscore - this should be replaced with a dash." }
        }

        if (name.contains(CAPS)) {
            logger.warn { "Tag '$name' contains at least one uppercase letter - tags should be completely lowercase." }
        }

        val content = file.readText().replace("\r", "")

        var yaml: String = ""
        var markdown: String? = null

        if (!content.contains(SEPARATOR)) {
            yaml = content
        } else {
            yaml = content.substringBefore(SEPARATOR)
            markdown = content.substringAfter(SEPARATOR).trim()
        }

        @Suppress("TooGenericExceptionCaught")
        val tagData = try {
            format.decodeFromString(TagData.serializer(), yaml)
        } catch (t: Throwable) {
            logger.error(t) { "Tag '$name' does not contain a valid YAML front matter." }
            return Pair(null, "Tag '$name' does not contain a valid YAML front matter.")
        }

        if (tagData !is AliasTag && markdown == null) {
            logger.error { "Tag '$name' requires Markdown content - is it separated with '---'?" }
            return Pair(null, "Tag '$name' requires Markdown content - is it separated with '---'?")
        } else if (tagData is AliasTag && markdown != null) {
            logger.warn {
                "Tag '$name' is an alias tag, but it contains Markdown content. Please consider removing it."
            }
        }

        val tag = Tag(normalise(name), name, tagData, markdown)

        return Pair(tag, null)
    }

    /**
     * Get a loaded tag from the cache.
     *
     * @return Tag object, if it exists - null otherwise
     */
    fun getTag(name: String) = tags[normalise(name)]

    /**
     * Get a set of loaded tags from the cache, matching a given name.
     *
     * @return List of matching Tag objects
     */
    fun getTags(name: String): List<Tag> {
        val matchingTag = getTag(name)

        if (matchingTag != null) {
            return listOf(matchingTag)
        }

        return tags.filter { it.key.endsWith("/$name") }.values.toList().sortedBy { it.name }
    }

    /**
     * Remove a loaded tag from the cache.
     *
     * @return True if the tag existed (and thus was removed), False otherwise
     */
    fun removeTag(name: String): Boolean {
        tags.remove(normalise(name)) ?: return false

        return true
    }

    /**
     * Given a string tag name, normalise it.
     *
     * This currently ensures that the name is lowercase and that all underscores are replaced with dashes.
     *
     * @param name The name to normalise
     * @return Normalised tag name
     */
    fun normalise(name: String): String = name.toLowerCase().replace("_", "-")
}
