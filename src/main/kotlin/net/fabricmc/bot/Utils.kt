package net.fabricmc.bot

import com.gitlab.kordlib.core.cache.data.MessageData
import com.gitlab.kordlib.core.entity.Member
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.Role
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.rest.request.RestRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.enums.Roles
import net.time4j.Duration
import net.time4j.IsoUnit
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val NEW_DAYS = 3L

/**
 * Convenience function to convert a [Role] object to a [Roles] enum value.
 *
 * @receiver The [Role] to convert.
 * @return The corresponding [Roles] enum value, or `null` if no corresponding value exists.
 */
fun Role.toEnum(): Roles? {
    for (role in Roles.values()) {
        if (this.id == config.getRoleSnowflake(role)) {
            return role
        }
    }

    return null
}

/** ID of the message author. **/
val MessageData.authorId: Long? get() = author.id

/** Is the message author a bot. **/
val MessageData.authorIsBot: Boolean? get() = author.bot

/**
 * The creation timestamp for this user.
 */
val User.createdAt: Instant get() = this.id.timeStamp

/**
 * Check whether this is a user that was created recently.
 *
 * @return Whether the user was created in the last 3 days.
 */
fun User.isNew(): Boolean = this.createdAt.isAfter(Instant.now().minus(NEW_DAYS, ChronoUnit.DAYS))

/**
 * Generate the jump URL for this message.
 *
 * @return A clickable URL to jump to this message.
 */
suspend fun Message.getUrl(): String {
    val guild = getGuildOrNull()?.id?.value ?: "@me"

    return "https://discordapp.com/channels/$guild/${channelId.value}/${id.value}"
}

/**
 * Deletes a message, catching and ignoring a HTTP 404 (Not Found) exception.
 */
suspend fun Message.deleteIgnoringNotFound() {
    try {
        this.delete()
    } catch (e: RestRequestException) {
        if (e.code != HttpStatusCode.NotFound.value) {
            throw e
        }
    }
}

/**
 * Deletes a message after a delay.
 *
 * This function **does not block**.
 *
 * @param millis The delay before deleting the message, in milliseconds.
 * @return Job spawned by the CoroutineScope.
 */
fun Message.deleteWithDelay(millis: Long, retry: Boolean = true): Job {
    val logger = KotlinLogging.logger {}

    return this.kord.launch {
        delay(millis)

        try {
            this@deleteWithDelay.deleteIgnoringNotFound()
        } catch (e: RestRequestException) {
            val message = this@deleteWithDelay

            if (retry) {
                logger.debug(e) {
                    "Failed to delete message, retrying: $message"
                }

                this@deleteWithDelay.deleteWithDelay(millis, false)
            } else {
                logger.error(e) {
                    "Failed to delete message: $message"
                }
            }
        }
    }
}

/** Check if the user has the provided [role]. **/
@Suppress("ExpressionBodySyntax")
suspend fun Member.hasRole(role: Role): Boolean {
    return this.roles.toList().contains(role)
}

/**
 * Convert a Time4J Duration object to seconds.
 *
 * @return The duration object folded into a single Long, representing total seconds.
 */
fun Duration<IsoUnit>.toSeconds(): Long {
    val amount = this.toTemporalAmount()
    var seconds = 0L

    seconds += amount.get(ChronoUnit.MILLENNIA) * ChronoUnit.MILLENNIA.duration.seconds
    seconds += amount.get(ChronoUnit.CENTURIES) * ChronoUnit.CENTURIES.duration.seconds
    seconds += amount.get(ChronoUnit.DECADES) * ChronoUnit.DECADES.duration.seconds
    seconds += amount.get(ChronoUnit.YEARS) * ChronoUnit.YEARS.duration.seconds
    seconds += amount.get(ChronoUnit.MONTHS) * ChronoUnit.MONTHS.duration.seconds
    seconds += amount.get(ChronoUnit.WEEKS) * ChronoUnit.WEEKS.duration.seconds
    seconds += amount.get(ChronoUnit.DAYS) * ChronoUnit.DAYS.duration.seconds
    seconds += amount.get(ChronoUnit.HOURS) * ChronoUnit.HOURS.duration.seconds
    seconds += amount.get(ChronoUnit.MINUTES) * ChronoUnit.MINUTES.duration.seconds
    seconds += amount.get(ChronoUnit.SECONDS)

    return seconds
}
