package net.fabricmc.bot.conf.spec

import com.uchuhimo.konf.ConfigSpec

/**
 * A class representing the `github` section of the configuration.
 *
 * This is used by Konf, and will not need to be accessed externally.
 */
object GitHubSpec : ConfigSpec(prefix = "github") {
    val organization by optional("", "GitHub organization")
    val token by optional("","Token with admin:org scope")
}
