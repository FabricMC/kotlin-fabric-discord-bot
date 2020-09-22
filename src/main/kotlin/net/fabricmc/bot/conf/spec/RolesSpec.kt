package net.fabricmc.bot.conf.spec

import com.uchuhimo.konf.ConfigSpec

/**
 * A class representing the `roles` section of the configuration.
 *
 * This is used by Konf, and will not need to be accessed externally.
 */
object RolesSpec : ConfigSpec() {
    /** Configured admin role ID. **/
    val admin by required<Long>()

    /** Configured mod role ID. **/
    val mod by required<Long>()

    /** Configured muted role ID. **/
    val muted by required<Long>()
}
