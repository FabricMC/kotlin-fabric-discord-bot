package net.fabricmc.bot.tags

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import mu.KotlinLogging
import java.io.File
import java.nio.file.Path
import java.util.*

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
    /** Mapping of all loaded tags. Names are not normalised.**/
    val tags: MutableMap<String, Tag> = mutableMapOf()

    /** Suffix for all tag files. **/
    val suffix = ".ytag"

    /**
     * Load all tags up from the root path. Overwrites any already-loaded tags.
     *
     * @return A map of tag name to errors, if any errors happened while tags were being loaded.
     */
    fun loadAll(infoLogging: Boolean = false): MutableMap<String, out List<String>> {
        tags.clear()

        val root = File(rootPath)
        val rootPathNormalised = root.toString()
        val errors: MutableMap<String, MutableList<String>> = mutableMapOf()

        logger.debug { "Loading tags from $rootPathNormalised" }

        for (file in root.walkBottomUp()) {
            val path = file.withoutPrefix(rootPathNormalised)

            if (path.endsWith(suffix)) {
                val tagName = path.substring(1).substringBeforeLast(".")

                val (tag, currentErrors) = loadTag(tagName)

                if (currentErrors.isEmpty()) {
                    if (infoLogging) {
                        logger.info { "Tag loaded: $tagName" }
                    } else {
                        logger.debug { "Tag loaded: $tagName" }
                    }

                    tags[tagName] = tag!!
                } else {
                    currentErrors.forEach {
                        logger.error {
                            "Tag $tagName $it"
                        }
                    }

                    errors[tagName] = currentErrors
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

                    errors[entry.key]?.add("Alias '${entry.key}' points to a tag that doesn't exist: '${data.target}'")
                    logger.error { "Alias '${entry.key}' points to a tag that doesn't exist: '${data.target}'" }
                } else if (target.data is AliasTag) {
                    badAliases.add(entry.key)

                    errors[entry.key]?.add("Alias '${entry.key}' points to another alias: '${data.target}'")
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
     * Create a tag. Expects
     *
     * @return Pair of Tag object (if it parsed properly) and list of error strings.
     */
    fun createTag(content: String): Pair<Tag?, MutableList<String>> {
        var yaml: String = ""
        var markdown: String? = null

        if (!content.contains(SEPARATOR)) {
            yaml = content
        } else {
            yaml = content.substringBefore(SEPARATOR)
            markdown = content.substringAfter(SEPARATOR).trim()
        }

        val errors = mutableListOf<String>()

        @Suppress("TooGenericExceptionCaught")
        val tagData = try {
            format.decodeFromString(TagData.serializer(), yaml)
        } catch (t: Throwable) {
            errors.add("does not contain a valid YAML front matter.")
            return Pair(null, errors)
        }

        if (tagData !is AliasTag && markdown == null) {
            errors.add("does not contain Markdown content - is it separated with '---'?")
            return Pair(null, errors)
        } else if (tagData is AliasTag && markdown != null) {
            errors.add(
                "contains Markdown content, despite being an alias. Please consider removing it."
            )
        } else if (tagData is EmbedTag) {
            if (tagData.embed.color != null) {
                errors.add(
                    "has the colour property set in the embed object. Set it at root level instead."
                )
                return Pair(
                    null,
                    errors
                )
            }

            if (tagData.embed.description != null) {
                errors.add(
                    "has the description property set in the embed object. " +
                            "Markdown content should instead be provided after the separator, '---'."
                )
                return Pair(
                    null,
                    errors
                )
            }
        }

        val tag = Tag(tagData, markdown)

        return Pair(tag, errors)

    }

    /**
     * Load a specific tag by name.
     *
     * @return Pair of Tag object (if it loaded properly) and error string (if it failed to load).
     */
    fun loadTag(name: String): Pair<Tag?, MutableList<String>> {
        logger.debug { "Loading tag: $name" }

        val file = Path.of(rootPath).resolve("$name$suffix").toFile()
        val content = file.readText().replace("\r", "")

        if (!file.exists()) {
            return Pair(null, mutableListOf("does not exist."))
        }

        val (tag, errors) = createTag(content)

        if (name.contains('_')) {
            errors.add("contains an underscore - this should be replaced with a dash.")
        }

        if (name.contains(CAPS)) {
            errors.add("contains at least one uppercase letter - tags should be completely lowercase.")
        }

        return Pair(tag, errors)

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
    fun getTags(name: String): SortedMap<String, Tag> {
        val fixedName = normalise(name)

        val matchingTag = getTag(fixedName)

        if (matchingTag != null) {
            return sortedMapOf(Pair(fixedName, matchingTag))
        }

        return tags.filter { it.key.endsWith("/$fixedName") }.toSortedMap()
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
