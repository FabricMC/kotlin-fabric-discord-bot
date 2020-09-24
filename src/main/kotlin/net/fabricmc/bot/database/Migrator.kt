package net.fabricmc.bot.database

import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import java.io.File
import java.util.*

/** A simple object in charge of making sure the database is correctly migrated. **/
object Migrator {
    private val logger = KotlinLogging.logger {}

    /** Migrate the database to the latest version, if it isn't already. **/
    fun migrate() {
        logger.info { "Applying migrations." }

        val versionFile = File("dbVersion.txt")

        if (!versionFile.exists()) {
            logger.info { "Creating database from scratch." }

            FabricBotDB.Schema.create(config.dbDriver)
            versionFile.writeText(FabricBotDB.Schema.version.toString())

            return
        }

        val currentVersion = Scanner(versionFile).nextInt()

        if (currentVersion == FabricBotDB.Schema.version) {
            logger.info { "Database is already at version $currentVersion, not migrating." }

            return
        }

        for (version in currentVersion until FabricBotDB.Schema.version) {
            logger.info { "Migrating from database version $version to version ${version + 1}." }

            FabricBotDB.Schema.migrate(config.dbDriver, version, version + 1)
        }

        versionFile.writeText(FabricBotDB.Schema.version.toString())
    }
}
