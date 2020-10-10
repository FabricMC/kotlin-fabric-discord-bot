package net.fabricmc.bot.utils

import com.google.common.base.Optional

/**
 * Check whether this [Optional] is present.
 *
 * @return `true` if the optional is not present (AKA absent), `false` otherwise.
 */
fun Optional<*>.isAbsent() = this.isPresent.not()

/**
 * Get the value from an [Optional] if it's present, otherwise return `null`.
 */
fun <T> Optional<T>.getOrNull(): T? {
    if (this.isAbsent()) return null

    return this.get()
}
