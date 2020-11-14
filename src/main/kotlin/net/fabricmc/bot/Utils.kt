package net.fabricmc.bot

import com.gitlab.kordlib.core.entity.*
import kotlinx.coroutines.*
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.enums.Roles
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

/**
 * Check whether this is a user that was created recently.
 *
 * @return Whether the user was created in the last 3 days.
 */
fun User.isNew(): Boolean = this.createdAt.isAfter(Instant.now().minus(NEW_DAYS, ChronoUnit.DAYS))
