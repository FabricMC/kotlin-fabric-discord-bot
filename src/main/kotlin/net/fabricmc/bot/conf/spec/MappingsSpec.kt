package net.fabricmc.bot.conf.spec

import com.uchuhimo.konf.ConfigSpec

/**
 * A class representing the `mappings` section of the configuration.
 *
 * This is used by Konf, and will not need to be accessed externally.
 */
object MappingsSpec : ConfigSpec() {
    val directory by required<String>()

    val mavenUrl by required<String>()
    val yarnUrl by required<String>()

    val defaultVersions by optional<List<String>>(listOf())
}
