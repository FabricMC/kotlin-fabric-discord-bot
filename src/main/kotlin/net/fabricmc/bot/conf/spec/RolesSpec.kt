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

    /** Configured trainee role ID. **/
    val traineeMod by required<Long>()

    /** Configured muted role ID. **/
    val muted by required<Long>()

    /** Configured meta-muted role ID. **/
    val noMeta by required<Long>()

    /** Configured reactions-muted role ID. **/
    val noReactions by required<Long>()

    /** Configured requests-muted role ID. **/
    val noRequests by required<Long>()

    /** Configured support-muted role ID. **/
    val noSupport by required<Long>()

    /** Configured dev life role ID. **/
    val devLife by required<Long>()
}
