package net.fabricmc.bot.extensions.infractions

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.entity.User
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*

private val timeFormatter = DateTimeFormatter.ofPattern("LLL d, uuuu 'at' HH:mm '(UTC)'", Locale.ENGLISH)
private val mySqlTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ENGLISH)

private val mySqlTimeParser = DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd HH:mm:ss['.'n]")
        .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
        .toFormatter()
        .withZone(ZoneId.of("UTC"))

/**
 * Format an Instant for display on Discord.
 *
 * @param ts [Instant] to format.
 * @return String representation of the given [Instant].
 */
fun instantToDisplay(ts: Instant?): String? {
    ts ?: return null

    return timeFormatter.format(ts.atZone(ZoneId.of("UTC")))
}

/**
 * Given a MySQL-formatted datetime string, return an Instant.
 *
 * @param ts String representation of a MySQL datetime.
 * @return [Instant] representing the given datetime.
 */
fun mysqlToInstant(ts: String?): Instant? {
    ts ?: return null

    return mySqlTimeParser.parse(ts) { accessor -> Instant.from(accessor) }
}

/**
 * Given an [Instant], return a MySQL-formatted datetime string.
 *
 * @param ts: [Instant] to format to a MySQL string.
 * @return MySQL-formatted string representing the given [Instant].
 */
fun instantToMysql(ts: Instant): String =
        mySqlTimeFormatter.format(
                ts.atZone(
                        ZoneId.of("UTC")
                )
        )

/**
 * Given a nullable user object and nullable snowflake, attempt to get a member ID.
 *
 * This is used to validate command argument.
 *
 * @param member User object, or null.
 * @param id User snowflake, or null.
 *
 * @return A Pair containing the result and an optional message to return.
 */
fun getMemberId(member: User?, id: Snowflake?): Pair<Snowflake?, String?> {
    return if (member == null && id == null) {
        Pair(null, "Please specify a user argument.")
    } else if (member != null && id != null) {
        Pair(null, "Please specify exactly one user argument, not two.")
    } else {
        Pair(member?.id ?: id!!, null)
    }
}
