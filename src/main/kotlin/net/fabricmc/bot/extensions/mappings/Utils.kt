package net.fabricmc.bot.extensions.mappings

import java.util.concurrent.CompletionException

/**
 * Attempts to unpack the cause of an exception, if it's a [CompletionException].
 *
 * @return The inner cause stored by the [CompletionException] or, if it's not a [CompletionException], this throwable.
 */
fun Throwable.unpack() = if (this is CompletionException && cause != null) {
    cause
} else {
    this
}
