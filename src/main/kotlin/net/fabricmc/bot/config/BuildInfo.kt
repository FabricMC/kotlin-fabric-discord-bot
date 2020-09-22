package net.fabricmc.bot.config

import java.util.*

/**
 * Object providing information about the current build.
 */
class BuildInfo {
    private val props = Properties()

    /** Current version of the bot. **/
    val version: String by lazy {
        props.getProperty("version")
    }

    /** @suppress **/
    fun load(): BuildInfo {
        props.load(Thread.currentThread().contextClassLoader.getResourceAsStream("build.properties"))
        return this
    }
}

/** Current BuildInfo instance, since we only need one. **/
val buildInfo = BuildInfo().load()
