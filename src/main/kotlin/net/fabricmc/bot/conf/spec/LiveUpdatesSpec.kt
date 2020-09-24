package net.fabricmc.bot.conf.spec

import com.uchuhimo.konf.ConfigSpec

/**
 * A class representing the `live_updates` section of the configuration.
 *
 * This is used by Konf, and will not need to be accessed externally.
 */
object LiveUpdatesSpec : ConfigSpec(prefix = "live_updates") {
    /* List of channels to send Jira version updates to */
    val jiraChannels by optional<Array<Long>>(emptyArray())
    /* List of channels to send Minecraft version updates to */
    val minecraftChannels by optional<Array<Long>>(emptyArray())
}
