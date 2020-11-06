package net.fabricmc.bot.conf.spec

import com.uchuhimo.konf.ConfigSpec

/**
 * A class representing the `github` section of the configuration.
 *
 * This is used by Konf, and will not need to be accessed externally.
 */
object GitSpec : ConfigSpec() {
    val directory by required<String>()

    val tagsFileUrl by required<String>()
    val tagsRepoBranch by required<String>()
    val tagsRepoUrl by required<String>()
    val tagsRepoPath by required<String>()
}
