package net.fabricmc.bot.constants

import com.gitlab.kordlib.common.kColor
import java.awt.Color

/**
 * Constant values for colours used around the bot.
 */
object Colours {
    /** @suppress **/
    val BLURPLE = Color.decode("#7289DA").kColor

    /** @suppress **/
    val FABRIC = Color.decode("#DBD0B4").kColor

    /** @suppress **/
    val NEGATIVE = Color.decode("#e74c3c").kColor

    /** @suppress **/
    val POSITIVE = Color.decode("#2ecc71").kColor

    /**
     * Given a string name, return the corresponding colour.
     *
     * @return A [Color] object, or null if the name doesn't match anything.
     */
    fun fromName(name: String) = when (name.toLowerCase()) {
        "blurple" -> BLURPLE
        "fabric" -> FABRIC
        "negative" -> NEGATIVE
        "positive" -> POSITIVE

        else -> null
    }
}
