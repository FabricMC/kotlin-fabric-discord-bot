package net.fabricmc.bot.conf.spec

import com.uchuhimo.konf.ConfigSpec

/**
 * A class representing the `bot` section of the configuration.
 *
 * This is used by Konf, and will not need to be accessed externally.
 */
object DBSpec : ConfigSpec() {
    /** MySQL URL for connecting to the database. **/
    val url by required<String>(description = "MySQL URL (without the jdbc:)")

    /** Database username for auth. **/
    val username by required<String>(description = "MySQL username")

    /** Database password for auth. **/
    val password by required<String>(description = "MySQL password")
}
