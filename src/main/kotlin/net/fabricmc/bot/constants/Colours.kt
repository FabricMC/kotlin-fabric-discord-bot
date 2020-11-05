package net.fabricmc.bot.constants

import java.awt.Color

/**
 * Constant values for colours used around the bot.
 */
object Colours {
    /** @suppress **/
    val BLURPLE = Color.decode("#7289DA")

    /** @suppress **/
    val NEGATIVE = Color.decode("#e74c3c")

    /** @suppress **/
    val POSITIVE = Color.decode("#2ecc71")

    /**
     * Given a string name, return the corresponding colour.
     *
     * @return A Color object, or null if the name doesn't match anything.
     */
    fun fromName(name: String) = when(name) {
        "blurple" -> BLURPLE
        "negative" -> NEGATIVE
        "positive" -> POSITIVE

        else -> null
    }
}
