package net.fabricmc.bot.conf

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

    /** Commit hash for the current build of the bot. **/
    val commit: String by lazy {
        props.getProperty("commit")
    }

    /** Version (without the -SNAPSHOT) and commit hash combined. **/
    val sentryVersion by lazy {
        val v = if ("-" in version) {
            version.split('-').first()
        } else {
            version
        }

        "$v-$commit"
    }

    /** @suppress **/
    fun load(): BuildInfo {
        props.load(Thread.currentThread().contextClassLoader.getResourceAsStream("build.properties"))
        return this
    }
}

/** Current BuildInfo instance, since we only need one. **/
val buildInfo = BuildInfo().load()
