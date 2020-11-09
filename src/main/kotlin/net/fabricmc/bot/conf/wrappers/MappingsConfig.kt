package net.fabricmc.bot.conf.wrappers

import com.uchuhimo.konf.Config
import net.fabricmc.bot.conf.spec.MappingsSpec

/**
 * Wrapper object representing the mappings configuration.
 *
 * @param config Loaded Konf Config object.
 */
data class MappingsConfig(private val config: Config) {
    /** The directory to store downloaded mappings files. **/
    val directory get() = config[MappingsSpec.directory]

    /** URL template used to retrieve Yarn JARs from Maven. **/
    val mavenUrl get() = config[MappingsSpec.mavenUrl]

    /** URL template used to retrieve Yarn versions from Fabric Meta. **/
    val yarnUrl get() = config[MappingsSpec.yarnUrl]

    /** List of MC versions to download mappings for on startup. **/
    val defaultVersions get() = config[MappingsSpec.defaultVersions]
}
