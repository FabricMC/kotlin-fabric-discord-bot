package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.behavior.channel.edit
import com.gitlab.kordlib.core.behavior.createTextChannel
import com.gitlab.kordlib.core.entity.channel.Category
import com.gitlab.kordlib.core.entity.channel.GuildMessageChannel
import com.gitlab.kordlib.core.entity.channel.TextChannel
import com.gitlab.kordlib.core.event.gateway.ReadyEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.utils.modLog
import net.fabricmc.bot.utils.respond
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoField
import java.util.*
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

private const val WEEK_DIFFERENCE = 5L  // 5 weeksA year of weeks, plus the difference
private const val CHECK_DELAY = 1000L * 60L * 30L  // 30 minutes

private val NAME_REGEX = Regex("action-log-(\\d{4})-(\\d{2})")

/** @suppress **/
@Suppress("UndocumentedPublicProperty")
data class ActionLogDebugArgs(
        val weeks: Long = 1L
)

/**
 * Extension for rotating action log channels.
 *
 * This extension maintains 5 action log channels in a given category, rotating them
 * weekly.
 */
class ActionLogExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "action log"

    private var channels = listOf<GuildMessageChannel>()
    private var checkJob: Job? = null
    private var debugOffset = 0L

    private var currentlyPopulating = false

    /** Current action log channel; the last in the rotation. **/
    val channel: GuildMessageChannel get() = channels.last()

    /** Boolean representing whether there's an action log channel to send to yet. **/
    val hasChannel: Boolean get() = channels.isNotEmpty()

    override suspend fun unload() {
        checkJob?.cancel()
        checkJob = null
    }

    override suspend fun setup() {
        event<ReadyEvent> {
            action {
                populateChannels()

                checkJob = bot.kord.launch {
                    while (true) {
                        delay(CHECK_DELAY)

                        logger.debug { "Running scheduled channel population." }

                        if (!currentlyPopulating) {
                            populateChannels()
                        } else {
                            logger.warn { "Channels are currently populating!" }
                        }
                    }
                }
            }
        }

        command {
            name = "action-log-sync"
            aliases = arrayOf("actionlog-sync", "action-logsync", "actionlogsync", "als")

            description = "Force an action logs rotation check."

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
            )

            action {
                if (currentlyPopulating) {
                    message.respond("A rotation check is currently running, try again later.")
                    return@action
                }

                populateChannels()

                message.respond(
                        "Rotation check done. Channels will have been rotated " +
                                "if it was appropriate."
                )
            }
        }

        val environment = System.getenv().getOrDefault("ENVIRONMENT", "production")

        if (environment != "production") {
            // This is for debugging, don't load it otherwise.

            command {
                name = "alert-debug-offset"
                description = "Change the current week offset for debugging."
                aliases = arrayOf("actiondebug-offset", "action-debugoffset", "actiondebugoffset", "ado")

                signature<ActionLogDebugArgs>()

                check(
                        ::defaultCheck,
                        topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
                )

                action {
                    with(parse<ActionLogDebugArgs>()) {
                        debugOffset = weeks

                        message.respond(
                                "Debug offset set to $debugOffset weeks."
                        )
                    }
                }
            }
        }
    }

    @Suppress("MagicNumber")  // It's the days in december, c'mon
    private fun getTotalWeeks(year: Int): Int {
        val cal = Calendar.getInstance()

        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, Calendar.DECEMBER)
        cal.set(Calendar.DAY_OF_MONTH, 31)

        return cal.getActualMaximum(Calendar.WEEK_OF_YEAR)
    }

    private suspend fun populateChannels() {
        currentlyPopulating = true

        @Suppress("TooGenericExceptionCaught")  // Anything could happen, you know how it is
        try {
            val category = config.getChannel(Channels.ACTION_LOG_CATEGORY) as Category

            val now = OffsetDateTime.now(ZoneOffset.UTC)
            var thisWeek = now.getLong(ChronoField.ALIGNED_WEEK_OF_YEAR)
            var thisYear = now.getLong(ChronoField.YEAR)

            val thisYearWeeks = getTotalWeeks(thisYear.toInt())

            if (debugOffset > 0) {
                thisWeek += debugOffset

                @Suppress("MagicNumber")
                if (thisWeek > thisYearWeeks) {
                    thisWeek -= thisYearWeeks
                    thisYear += 1
                }

                logger.debug { "Applied debug offset of $debugOffset weeks - it is now week $thisWeek of $thisYear." }
            }

            var currentChannelExists = false
            val allChannels = mutableListOf<GuildMessageChannel>()

            category.channels.toList().forEach {
                if (it is GuildMessageChannel) {
                    logger.debug { "Checking existing channel: ${it.name}" }

                    val match = NAME_REGEX.matchEntire(it.name)

                    if (match != null) {
                        val year = match.groups[1]!!.value.toLong()
                        val week = match.groups[2]!!.value.toLong()
                        val yearWeeks = getTotalWeeks(year.toInt())

                        val weekDifference = abs(thisWeek - week)
                        val yearDifference = abs(thisYear - year)

                        if (year == thisYear && week == thisWeek) {
                            logger.debug { "Passing: This is the latest channel." }

                            currentChannelExists = true
                            allChannels.add(it)
                        } else if (year > thisYear) {
                            // It's in the future, so this isn't valid!
                            logger.debug { "Deleting: This is next year's channel." }

                            it.delete()
                            logDeletion(it)
                        } else if (year == thisYear && week > thisWeek) {
                            // It's in the future, so this isn't valid!
                            logger.debug { "Deleting: This is a future week's channel." }

                            it.delete()
                            logDeletion(it)
                        } else if (yearDifference > 1L || yearDifference != 1L && weekDifference > WEEK_DIFFERENCE) {
                            // This one is _definitely_ too old.
                            logger.debug { "Deleting: This is an old channel." }

                            it.delete()
                            logDeletion(it)
                        } else if (yearDifference == 1L && yearWeeks - week + thisWeek > WEEK_DIFFERENCE) {
                            // This is from last year, but more than 5 weeks ago.
                            logger.debug { "Deleting: This is an old channel from last year." }

                            it.delete()
                            logDeletion(it)
                        } else {
                            allChannels.add(it)
                        }
                    }
                }
            }

            @Suppress("MagicNumber")
            if (!currentChannelExists) {
                logger.debug { "Creating this week's channel." }

                val c = config.getGuild().createTextChannel {
                    val yearPadded = thisYear.toString().padStart(4, '0')
                    val weekPadded = thisWeek.toString().padStart(2, '0')

                    name = "action-log-$yearPadded-$weekPadded"
                    parentId = category.id
                }

                currentChannelExists = true

                logCreation(c)
                allChannels.add(c)
            }

            allChannels.sortBy { it.name }

            while (allChannels.size > WEEK_DIFFERENCE) {
                val c = allChannels.removeFirst()

                logger.debug { "Deleting extra channel: ${c.name}" }

                c.delete()
                logDeletion(c)
            }

            channels = allChannels

            logger.debug { "Sorting channels." }

            allChannels.forEachIndexed { i, c ->
                val curPos = c.rawPosition

                if (curPos != i) {
                    logger.debug { "Updating channel position for ${c.name}: $curPos -> $i" }

                    (allChannels[i] as TextChannel).edit {
                        position = i
                    }
                }
            }

            logger.debug { "Done." }
        } catch (t: Throwable) {
            logger.error(t) { "Error thrown during action log channel rotation." }
        }

        currentlyPopulating = false
    }

    private suspend fun logCreation(channel: GuildMessageChannel) = modLog {
        title = "Action log rotation"
        color = Colours.POSITIVE

        description = "Channel created: **#${channel.name} (`${channel.id.longValue}`)**"
    }

    private suspend fun logDeletion(channel: GuildMessageChannel) = modLog {
        title = "Action log rotation"
        color = Colours.NEGATIVE

        description = "Channel removed: **#${channel.name} (`${channel.id.longValue}`)**"
    }
}
