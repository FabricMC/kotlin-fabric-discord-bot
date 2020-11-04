package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.entity.Embed
import com.gitlab.kordlib.core.entity.channel.GuildMessageChannel
import com.gitlab.kordlib.core.event.gateway.ReadyEvent
import com.gitlab.kordlib.core.event.message.MessageCreateEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.runSuspended
import net.fabricmc.bot.tags.*
import net.fabricmc.bot.utils.ensureRepo
import net.fabricmc.bot.utils.respond
import org.eclipse.jgit.api.MergeResult
import java.awt.Color
import java.nio.file.Path

private const val MAX_ERRORS = 5
private val logger = KotlinLogging.logger {}
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
                        description += "\n\n**...plus ${errors.size - MAX_ERRORS} more."
                    }

                    description += "\n\n${parser.tags.size} tags loaded successfully."

                    (config.getChannel(Channels.ALERTS) as GuildMessageChannel).createEmbed {
                        color = Colours.NEGATIVE
                        title = "Failed to load tags"
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
                val tagName = it.message.content.removePrefix(config.tagPrefix).trim()

                val tags = parser.getTags(tagName)

                if (tags.isEmpty()) {
                    it.message.respond("No such tag: $tagName")
                    return@action
                }

                if (tags.size > 1) {
                    it.message.respond(
                            "Multiple tags have been found with that name. " +
                                    "Please pick one of the following:\n\n" +

                                    tags.joinToString(", ") { "`${it.name}`" }
                    )

                    return@action
                }

                var tag: Tag? = tags.first()

                if (tag!!.data is AliasTag) {
                    val data = tag.data as AliasTag

                    tag = parser.getTag(data.target)

                    if (tag == null) {
                        it.message.respond("No such alias target: $tagName -> ${data.target}")
                        return@action
                    }
                }

                if (tag.data is TextTag) {
                    val data = tag.data as TextTag

                    it.message.channel.createMessage(tag.markdown!!)  // If it's a text tag, the markdown is not null
                } else if (tag.data is EmbedTag) {
                    val data = tag.data as EmbedTag

                    it.message.channel.createEmbed {
                        Embed(data.embed, bot.kord).apply(this)

                        description = tag.markdown ?: data.embed.description

                        if (data.colour != null) {
                            val colourString = data.colour!!.toLowerCase()

                            color = if (colourString == "blurple") {
                                Colours.BLURPLE
                            } else if (colourString == "positive") {
                                Colours.POSITIVE
                            } else if (colourString == "negative") {
                                Colours.NEGATIVE
                            } else if (colourString.startsWith("#") || colourString.toIntOrNull() != null) {
                                Color.decode(colourString)
                            } else {
                                null
                            }
                        }
                    }
                }
            }
        }
    }
}
