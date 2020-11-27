package net.fabricmc.bot.extensions

import com.gitlab.kordlib.common.kColor
import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.behavior.channel.createMessage
import com.gitlab.kordlib.core.entity.Embed
import com.gitlab.kordlib.core.entity.channel.GuildMessageChannel
import com.gitlab.kordlib.core.event.gateway.ReadyEvent
import com.gitlab.kordlib.core.event.message.MessageCreateEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.Paginator
import com.kotlindiscord.kord.extensions.commands.converters.coalescedString
import com.kotlindiscord.kord.extensions.commands.converters.string
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.deleteWithDelay
import com.kotlindiscord.kord.extensions.utils.parse
import com.kotlindiscord.kord.extensions.utils.respond
import com.kotlindiscord.kord.extensions.utils.runSuspended
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.fabricmc.bot.TagMissingArgumentException
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colors
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.extensions.infractions.instantToDisplay
import net.fabricmc.bot.tags.*
import net.fabricmc.bot.utils.ensureRepo
import net.fabricmc.bot.utils.requireBotChannel
import org.eclipse.jgit.api.MergeResult
import java.awt.Color
import java.lang.Integer.max
import java.nio.file.Path
import java.time.Instant

private val logger = KotlinLogging.logger {}

private const val CHUNK_SIZE = 10
private const val DELETE_DELAY = 1000L * 15L  // 15 seconds
private const val MAX_ERRORS = 5
private const val PAGE_TIMEOUT = 60_000L  // 60 seconds
private val SUB_REGEX = "\\{\\{(?<name>.*?)}}".toRegex()
private const val UPDATE_CHECK_DELAY = 1000L * 30L  // 30 seconds, consider kotlin.time when it's not experimental

/**
 * Extension in charge of keeping track of and exposing tags.
 *
 * This extension is Git-powered, all the tags are stored in a git repository.
 */
class TagsExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "tags"

    private val git = ensureRepo(name, config.git.tagsRepoUrl, config.git.tagsRepoBranch)
    private val parser = TagParser(Path.of(config.git.directory, name, config.git.tagsRepoPath).toString())
    private var checkJob: Job? = null

    override suspend fun setup() {
        event<ReadyEvent> {
            action {
                logger.debug { "Current branch: ${git.repository.branch} (${git.repository.fullBranch})" }

                git.pull().call()

                val errors = parser.loadAll()

                if (errors.isNotEmpty()) {
                    var description = "The following errors were encountered while loading tags.\n\n"

                    description += errors.toList()
                            .sortedBy { it.first }
                            .take(MAX_ERRORS)
                            .joinToString("\n\n") { "**${it.first} »** ${it.second}" }

                    if (errors.size > MAX_ERRORS) {
                        description += "\n\n**...plus ${errors.size - MAX_ERRORS} more.**"
                    }

                    description += "\n\n${parser.tags.size} tags loaded successfully."

                    (config.getChannel(Channels.ALERTS) as GuildMessageChannel).createEmbed {
                        color = Colors.NEGATIVE
                        title = "Tag-loading errors"

                        this.description = description
                    }
                }

                logger.info { "Loaded ${parser.tags.size} tags." }

                checkJob = bot.kord.launch {
                    while (true) {
                        delay(UPDATE_CHECK_DELAY)

                        runSuspended {
                            logger.debug { "Pulling tags repo." }
                            @Suppress("TooGenericExceptionCaught")
                            try {
                                // TODO: There's a bunch of stuff here we can use if we decide we want to calculate
                                // TODO: tag changes and reload only the changed tags, but it's out of scope for now
//                                val oldHead = git.repository.resolve("HEAD^{tree}")

//                                val result = git.pull().call()

//                                val newHead = git.repository.resolve("HEAD^{tree}")
//
//                                if (result.mergeResult.mergeStatus == MergeResult.MergeStatus.ALREADY_UP_TO_DATE) {
//                                    return@runSuspended
//                                }
//
//                                val objectReader = git.repository.newObjectReader()
//                                val oldTree = CanonicalTreeParser()
//                                val newTree = CanonicalTreeParser()
//
//                                oldTree.reset(objectReader, oldHead)
//                                newTree.reset(objectReader, newHead)
//
//                                val changes = git.diff()
//                                        .setOldTree(oldTree)
//                                        .setNewTree(newTree)
//                                        .call()
//
//                                changes.first().changeType
//
//                                if (changes.isNotEmpty()) {
//                                    val errors = parser.loadAll()
//                                }
//
                                val result = git.pull().call()

                                if (result.mergeResult.mergeStatus == MergeResult.MergeStatus.ALREADY_UP_TO_DATE) {
                                    return@runSuspended
                                }

                                parser.loadAll()
                            } catch (e: Throwable) {
                                logger.catching(e)
                            }
                        }
                    }
                }
            }
        }

        event<MessageCreateEvent> {
            check(::defaultCheck)
            check { it.message.content.startsWith(config.tagPrefix) }

            action {
                val givenArgs = it.message.content.removePrefix(config.tagPrefix)

                if (givenArgs.isEmpty() || givenArgs.startsWith(' ')) {
                    return@action
                }

                val splitArgs = it.message.parse().toMutableList()

                splitArgs[0] = splitArgs[0].removePrefix(config.tagPrefix)

                val (tagName, args) = parseArgs(splitArgs)

                val tags = parser.getTags(tagName)

                if (tags.isEmpty()) {
                    if (tagName.replace("?", "").isNotEmpty()) {
                        it.message.respond("No such tag: `$tagName`").deleteWithDelay(DELETE_DELAY)
                        it.message.deleteWithDelay(DELETE_DELAY)
                    }

                    return@action
                }

                if (tags.size > 1) {
                    it.message.respond(
                            "Multiple tags have been found with that name. " +
                                    "Please pick one of the following:\n\n" +

                                    tags.joinToString(", ") { t -> "`${t.name}`" }
                    ).deleteWithDelay(DELETE_DELAY)
                    it.message.deleteWithDelay(DELETE_DELAY)

                    return@action
                }

                var tag: Tag? = tags.first()

                if (tag!!.data is AliasTag) {
                    val data = tag.data as AliasTag

                    tag = parser.getTag(data.target)

                    if (tag == null) {
                        it.message.respond(
                                "Invalid alias - no such alias target: " +
                                        "`$tagName` -> `${data.target}`"
                        )
                        return@action
                    }

                    if (tag.data is AliasTag) {
                        it.message.respond(
                                "Invalid alias - this alias points to another alias: " +
                                        "`$tagName` -> `${data.target}`"
                        )
                        return@action
                    }
                }

                val markdown = try {
                    substitute(tag.markdown, args)
                } catch (e: TagMissingArgumentException) {
                    it.message.respond(e.toString())
                    return@action
                }

                if (tag.data is TextTag) {
                    it.message.channel.createMessage {
                        content = markdown!!  // If it's a text tag, the markdown is not null

                        allowedMentions { }  // Nope
                    }
                } else if (tag.data is EmbedTag) {
                    val data = tag.data as EmbedTag

                    it.message.channel.createEmbed {
                        Embed(data.embed, bot.kord).apply(this)

                        description = markdown ?: data.embed.description.value

                        if (data.color != null) {
                            val colorString = data.color!!.toLowerCase()

                            color = Colors.fromName(colorString) ?: Color.decode(colorString).kColor
                        }
                    }
                }
            }
        }

        group {
            name = "tags"
            aliases = arrayOf("tag", "tricks", "trick", "t")
            description = "Commands for querying the loaded tags.\n\n" +
                    "To get the content of a tag, use `${config.tagPrefix}<tagname>`. Some tags support " +
                    "substitutions, which can be supplied as further arguments. If your substitution contains " +
                    "a space, \"surround it with quotes\"."

            check(::defaultCheck)

            command {
                name = "show"
                aliases = arrayOf("get", "s", "g")
                description = "Get basic information about a specific tag."

                signature(::TagArgs)

                action {
                    if (!message.requireBotChannel(delay = DELETE_DELAY)) {
                        return@action
                    }

                    val parser = this@TagsExtension.parser

                    with(parse(::TagArgs)) {
                        val tag = parser.getTag(tagName)

                        if (tag == null) {
                            message.respond("No such tag: `$tagName`").deleteWithDelay(DELETE_DELAY)
                            message.deleteWithDelay(DELETE_DELAY)
                            return@action
                        }

                        val url = config.git.tagsFileUrl.replace("{NAME}", tag.suppliedName)
                        val path = "${config.git.tagsRepoPath.removePrefix("/")}/${tag.suppliedName}${parser.suffix}"
                        val log = git.log().addPath(path).setMaxCount(1).call()
                        val rev = log.firstOrNull()

                        message.respond {
                            embed {
                                title = "Tag: $tagName"
                                color = Colors.BLURPLE

                                description = when {
                                    tag.data is AliasTag -> {
                                        val data = tag.data as AliasTag

                                        "This **alias tag** targets the following tag: `${data.target}`"
                                    }
                                    tag.markdown != null -> "This **${tag.data.type} tag** contains " +
                                            "**${tag.markdown!!.length} characters** of Markdown in its body."

                                    else -> "This **${tag.data.type} tag** contains no Markdown body."
                                }

                                description += "\n\n:link: [Open tag file in browser]($url)"

                                if (rev != null) {
                                    val author = rev.authorIdent

                                    if (author != null) {
                                        field {
                                            name = "Last author"
                                            value = author.name

                                            inline = true
                                        }
                                    }

                                    val committer = rev.committerIdent

                                    if (committer != null && committer.name != author.name) {
                                        field {
                                            name = "Last committer"
                                            value = committer.name

                                            inline = true
                                        }
                                    }

                                    val committed = instantToDisplay(Instant.ofEpochSecond(rev.commitTime.toLong()))!!

                                    field {
                                        name = "Last edit"
                                        value = committed

                                        inline = true
                                    }

                                    @Suppress("MagicNumber")
                                    field {
                                        name = "Current SHA"
                                        value = "`${rev.name.substring(0, 8)}`"

                                        inline = true
                                    }
                                }
                            }
                        }
                    }
                }
            }

            command {
                name = "search"
                aliases = arrayOf("find", "f", "s")
                description = "Search through the tag names and content for a piece of text."

                signature(::TagSearchArgs)

                action {
                    if (!message.requireBotChannel(delay = DELETE_DELAY)) {
                        return@action
                    }

                    val parser = this@TagsExtension.parser

                    with(parse(::TagSearchArgs)) {
                        val aliasTargetMatches = mutableSetOf<Pair<String, String>>()
                        val embedFieldMatches = mutableSetOf<String>()
                        val nameMatches = mutableSetOf<String>()
                        val markdownMatches = mutableSetOf<String>()

                        parser.tags.forEach { (name, tag) ->
                            if (name.contains(query)) {
                                nameMatches.add(name)
                            }

                            if (tag.markdown?.contains(query) == true) {
                                markdownMatches.add(name)
                            }

                            if (tag.data is AliasTag) {
                                val data = tag.data as AliasTag

                                if (data.target.contains(query)) {
                                    aliasTargetMatches.add(Pair(name, data.target))
                                }
                            } else if (tag.data is EmbedTag) {
                                val data = tag.data as EmbedTag

                                data.embed.fields.value?.forEach { field ->
                                    if (field.name.contains(query)) {
                                        embedFieldMatches.add(name)
                                    } else if (field.value.contains(query)) {
                                        embedFieldMatches.add(name)
                                    }
                                }
                            }
                        }

                        val totalMatches = aliasTargetMatches.size +
                                embedFieldMatches.size +
                                nameMatches.size +
                                markdownMatches.size

                        if (totalMatches < 1) {
                            message.respond {
                                embed {
                                    title = "Search: No matches"
                                    description = "We tried our best, but we can't find a tag containing your query. " +
                                            "Please try again with a different query!"
                                }
                            }
                        } else {
                            val pages = mutableListOf<String>()

                            if (nameMatches.isNotEmpty()) {
                                nameMatches.chunked(CHUNK_SIZE).forEach {
                                    var page = "__**Name matches**__\n\n"

                                    it.forEach { match ->
                                        page += "**»** `${match}`\n"
                                    }

                                    pages.add(page)
                                }
                            }

                            if (markdownMatches.isNotEmpty()) {
                                markdownMatches.chunked(CHUNK_SIZE).forEach {
                                    var page = "__**Markdown content matches**__\n\n"

                                    it.forEach { match ->
                                        page += "**»** `${match}`\n"
                                    }

                                    pages.add(page)
                                }
                            }

                            if (embedFieldMatches.isNotEmpty()) {
                                embedFieldMatches.chunked(CHUNK_SIZE).forEach {
                                    var page = "__**Embed field matches**__\n\n"

                                    it.forEach { match ->
                                        page += "**»** `${match}`\n"
                                    }

                                    pages.add(page)
                                }
                            }

                            if (aliasTargetMatches.isNotEmpty()) {
                                aliasTargetMatches.chunked(CHUNK_SIZE).forEach {
                                    var page = "__**Alias matches**__\n\n"

                                    it.forEach { pair ->
                                        page += "`${pair.first}` **»** `${pair.second}`\n"
                                    }

                                    pages.add(page)
                                }
                            }

                            val paginator = Paginator(
                                    bot,
                                    message.channel,
                                    "Search: $totalMatches match" + if (totalMatches > 1) "es" else "",
                                    pages,
                                    message.author,
                                    PAGE_TIMEOUT,
                                    true
                            )

                            paginator.send()
                        }
                    }
                }
            }

            command {
                name = "list"
                aliases = arrayOf("l")
                description = "Get a list of all of the available tags."

                action {
                    if (!message.requireBotChannel(delay = DELETE_DELAY)) {
                        return@action
                    }

                    val parser = this@TagsExtension.parser

                    val aliases = mutableListOf<Tag>()
                    val otherTags = mutableListOf<Tag>()

                    parser.tags.values.forEach {
                        if (it.data is AliasTag) {
                            aliases.add(it)
                        } else {
                            otherTags.add(it)
                        }
                    }

                    val pages = mutableListOf<String>()

                    if (otherTags.isNotEmpty()) {
                        otherTags.sortBy { it.name }
                        otherTags.chunked(CHUNK_SIZE).forEach {
                            var page = "**__Tags__ (${otherTags.size})**\n\n"

                            it.forEach { tag ->
                                page += "**»** `${tag.name}`\n"
                            }

                            pages.add(page)
                        }
                    }

                    if (aliases.isNotEmpty()) {
                        aliases.sortBy { it.name }
                        aliases.chunked(CHUNK_SIZE).forEach {
                            var page = "**__Aliases__ (${aliases.size})**\n\n"

                            it.forEach { alias ->
                                val data = alias.data as AliasTag

                                page += "`${alias.name}` **»** `${data.target}`\n"
                            }

                            pages.add(page)
                        }
                    }

                    val paginator = Paginator(
                            bot,
                            message.channel,
                            "All tags (${parser.tags.size})",
                            pages,
                            message.author,
                            PAGE_TIMEOUT,
                            true
                    )

                    paginator.send()
                }
            }
        }
    }

    /**
     * Given a string, return the tag name and a list of arguments.
     *
     * @param args String of argument to parse.
     */
    private fun parseArgs(args: List<String>): Pair<String, List<String>> {
        val tag = args.first()
        val arguments = args.drop(1)

        return Pair(tag, arguments)
    }

    /**
     * Attempt to parse some markdown and replace substitution strings within it.
     *
     * @param markdown Markdown to parse - if null, this function will also return null
     * @param args List of string arguments to use for substitutions
     *
     * @return Markdown, but with substitutions replaced
     * @throws TagMissingArgumentException Thrown if there aren't enough arguments to fulfil the substitutions.
     */
    @Throws(TagMissingArgumentException::class)
    fun substitute(markdown: String?, args: List<String>): String? {
        markdown ?: return null

        val matches = SUB_REGEX.findAll(markdown)
        val substitutions = mutableMapOf<String, String>()

        var totalArgs = 0
        val providedArgs = args.size

        for (match in matches) {
            val key = match.groups["name"]!!.value.toIntOrNull()

            if (key == null) {
                logger.warn { "Invalid substitution, '${match.value}' isn't an integer substitution." }
            } else {
                totalArgs = max(totalArgs, key + 1)

                if (key > args.size - 1) {
                    continue
                }

                substitutions[match.value] = args[key]
            }
        }

        if (providedArgs < totalArgs) {
            throw TagMissingArgumentException(providedArgs, totalArgs)
        }

        var result: String = markdown

        substitutions.forEach { (key, value) -> result = result.replace(key, value) }

        return result
    }

    /**
     * Arguments for commands that just want a tag name.
     *
     * @property tagName Name of the tag
     */
    @Suppress("UndocumentedPublicProperty")
    class TagArgs : Arguments() {
        val tagName by string("tag")
    }

    /**
     * Arguments for tag commands that just want a search query.
     *
     * @property query Search query
     */
    @Suppress("UndocumentedPublicProperty")
    class TagSearchArgs : Arguments() {
        val query by coalescedString("query")
    }
}
