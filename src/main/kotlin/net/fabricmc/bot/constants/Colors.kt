package net.fabricmc.bot.constants

import com.gitlab.kordlib.common.kColor
import java.awt.Color

/**
 * Constant values for colors used around the bot.
 */
object Colors {
    /** @suppress **/
    val BLURPLE = Color.decode("#7289DA").kColor

    /** @suppress **/
    val FABRIC = Color.decode("#DAD1B4").kColor

    /** @suppress **/
    val NEGATIVE = Color.decode("#E74C3C").kColor

    /** @suppress **/
    val POSITIVE = Color.decode("#2ECC71").kColor

    /**
     * Given a string name, return the corresponding color.
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
