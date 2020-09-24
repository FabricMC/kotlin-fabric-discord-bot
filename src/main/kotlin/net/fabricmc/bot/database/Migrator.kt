package net.fabricmc.bot.database

import mu.KotlinLogging
import net.fabricmc.bot.conf.config

/** A simple object in charge of making sure the database is correctly migrated. **/
object Migrator {
    private val logger = KotlinLogging.logger {}

    /** Migrate the database to the latest version, if it isn't already. **/
    fun migrate() {
        config.dbDriver.execute(
                null,
                "CREATE TABLE IF NOT EXISTS migration_version (version INT NOT NULL)",
                0
        )

        val queries = config.db.migrationVersionQueries
        val queryList = queries.getMigrationVersion().executeAsList()

        if (queryList.isEmpty()) {
            logger.info { "Creating database from scratch." }

            FabricBotDB.Schema.create(config.dbDriver)
            queries.setMigrationVersion(FabricBotDB.Schema.version)

            return
        }

        val currentVersion = queryList.first()

        if (currentVersion == FabricBotDB.Schema.version) {
            logger.info { "Database is already at version $currentVersion, not migrating." }

            return
        }

        for (version in currentVersion until FabricBotDB.Schema.version) {
            logger.info { "Migrating from database version $version to version ${version + 1}." }

            FabricBotDB.Schema.migrate(config.dbDriver, version, version + 1)
        }

        queries.setMigrationVersion(FabricBotDB.Schema.version)
    }
}
