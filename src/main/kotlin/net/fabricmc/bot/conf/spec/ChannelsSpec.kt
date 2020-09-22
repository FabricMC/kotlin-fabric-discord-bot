package net.fabricmc.bot.conf.spec

import com.uchuhimo.konf.ConfigSpec

/**
 * A class representing the `channels` section of the configuration.
 *
 * This is used by Konf, and will not need to be accessed externally.
 */
object ChannelsSpec : ConfigSpec() {
    /** Configured bot-commands channel ID. **/
    val botCommands by required<Long>()

    /** Configured action-log channel ID. **/
    val actionLog by required<Long>()

    /** Configured moderator-log channel ID. **/
    val moderatorLog by required<Long>()
}
