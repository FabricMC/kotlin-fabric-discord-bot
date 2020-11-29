package net.fabricmc.bot.extensions.mappings

import com.kotlindiscord.kord.extensions.utils.runSuspended
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.request
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.mapping.tree.*
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.text.Charsets.UTF_8

/** Namespace for official names. **/
const val NS_OFFICIAL = "official"

/** Namespace for intermediary names. **/
const val NS_INTERMEDIARY = "intermediary"

/** Namespace for yarn names. **/
const val NS_NAMED = "named"

private val CONTAINS_NON_DIGITS = """[^\d]""".toRegex()

private val logger = KotlinLogging.logger {}

/**
 * Class in charge of downloading and querying Yarn mappings.
 */
class MappingsManager {
    // TODO: Consider in-memory caching (although note that mappings take up like 250MiB of memory or so)
    private val cacheDir = Path.of(config.mappings.directory)
    private val versionCache: MutableMap<String, TinyTree?> = mutableMapOf()

    /** Maps "snapshot" and "release" to actual MC versions. **/
    val versionNames: MutableMap<String, String> = mutableMapOf()

    /** Maps cached MC versions to their current yarn releases. **/
    val yarnVersions: MutableMap<String, String> = mutableMapOf()

    private val client = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    init {
        Files.createDirectories(cacheDir)
    }

    // TODO: Modmuss says there's probably better ways to do this.
    private fun exactMatches(mapped: Mapped, query: String): Boolean =
            mapped.getName(NS_INTERMEDIARY) == query
                    || mapped.getName(NS_NAMED) == query
                    || mapped.getName(NS_OFFICIAL) == query

    private fun matches(mapped: Mapped, query: String): Boolean =
            mapped.getName(NS_INTERMEDIARY).endsWith(query)
                    || mapped.getName(NS_NAMED).endsWith(query)
                    || mapped.getName(NS_OFFICIAL) == query

    /**
     * Given a Minecraft version and query, attempt to retrieve a list of matching class mappings.
     *
     * @param minecraftVersion Minecraft version to retrieve mappings for
     * @param query Class name to match against
     * @return List of mappings results, or null if there's no matching Yarn version for the given Minecraft version
     */
    suspend fun getClassMappings(minecraftVersion: String, query: String): List<MappingsResult>? {
        val mappings = openMappings(minecraftVersion) ?: return null

        return mappings.classes
                .filter { matches(it, query) }
                .map { MappingsResult(it, null) }
                .toList()
    }

    /**
     * Given a Minecraft version and query, attempt to retrieve a list of matching method mappings.
     *
     * @param minecraftVersion Minecraft version to retrieve mappings for
     * @param query Method name to match against
     * @return List of mappings results, or null if there's no matching Yarn version for the given Minecraft version
     */
    suspend fun getMethodMappings(minecraftVersion: String, query: String): List<MappingsResult>? {
        val mappings = openMappings(minecraftVersion) ?: return null

        return getMappingsResults(mappings, preProcessMethodQuery(query), ClassDef::getMethods)
    }

    /**
     * Preprocesses a method mapping query, adapting the query value to make more ergonomic lookups possible.
     */
    private fun preProcessMethodQuery(query: String): String {
        /*
         * For allowing more ergonomic lookup of methods, we allow several parsing cases.
         * More preprocessing cases may be added in the future.
         * Methods may be specified as shown below:
         * ====================================
         * Obfuscated:
         *  aZ_
         * Intermediary:
         *  method_xxxx
         *  xxxx -> method_xxxx
         * Named:
         *  blah
        */

        // Try intermediary path first.
        // Since a method name per the Java Compiler cannot start with a number,
        // we can assume the syntax is `xxxx` -> `method_xxxx`
        if (query.isNotEmpty() && Character.isDigit(query[0])) {
            // Exit fast if the contents contain anything that is not a number
            if (query.contains(CONTAINS_NON_DIGITS)) {
                return query
            }

            // Since we know the query is just numbers, rewrite the query to include the method_ prefix
            return "method_$query"
        }

        return query
    }

    /**
     * Given a Minecraft version and query, attempt to retrieve a list of matching field mappings.
     *
     * @param minecraftVersion Minecraft version to retrieve mappings for
     * @param query Field name to match against
     * @return List of mappings results, or null if there's no matching Yarn version for the given Minecraft version
     */
    suspend fun getFieldMappings(minecraftVersion: String, query: String): List<MappingsResult>? {
        val mappings = openMappings(minecraftVersion) ?: return null

        return getMappingsResults(mappings, preProcessFieldQuery(query), ClassDef::getFields)
    }

    /**
     * Preprocesses a field mapping query, adapting the query value to make more ergonomic lookups possible.
     */
    private fun preProcessFieldQuery(query: String): String {
        /*
         * For allowing more ergonomic lookup of fields, we allow several parsing cases.
         * More preprocessing cases may be added in the future.
         * Fields may be specified as shown below:
         * ====================================
         * Obfuscated:
         *  aB
         * Intermediary:
         *  field_xxxx
         *  xxxx -> field_xxxx
         * Named:
         *  blah
        */

        // Try intermediary path first.
        // Since a field name per the Java Compiler cannot start with a number,
        // we can assume the syntax is `xxxx` -> `method_xxxx`
        if (query.isNotEmpty() && Character.isDigit(query[0])) {
            // Exit fast if the contents contain anything that is not a number
            if (query.contains(CONTAINS_NON_DIGITS)) {
                return query
            }

            // Since we know the query is just numbers, rewrite the query to include the field_ prefix
            return "field_$query"
        }

        return query
    }

    private fun getMappingsResults(
            tree: TinyTree,
            query: String,
            body: (ClassDef) -> Collection<Descriptored>
    ): List<MappingsResult> = tree.classes.flatMap { classDef ->
        body.invoke(classDef).mapNotNull { descriptor ->
            if (exactMatches(descriptor, query)) {
                MappingsResult(classDef, descriptor)
            } else {
                null
            }
        }
    }.toList()

    /**
     * Wipe the mappings cache and add mappings for two versions of MC to the cleared cache.
     *
     * @param release Release version to cache
     * @param snapshot Snapshot version to cache
     */
    suspend fun cacheMappings(release: String, snapshot: String) {
        val releaseYarnVersion = getLatestYarnVersion(release)
        val snapshotYarnVersion = getLatestYarnVersion(snapshot)

        if (yarnVersions[release] == releaseYarnVersion && yarnVersions[snapshot] == snapshotYarnVersion) {
            logger.debug { "Both keys hit the cache, not clearing." }
            return
        }

        if (versionNames.isEmpty()) {
            versionNames["release"] = release
            versionNames["snapshot"] = snapshot
        }

        if (!versionCache.containsKey(release)) {
            logger.debug { "Caching release version: $release." }

            if (releaseYarnVersion != null) {
                var releaseVersion = openMappings(release)

                if (releaseVersion == null) {
                    logger.warn { "No mappings found for release version: $release" }
                } else {
                    versionCache.remove(versionNames["release"])
                    versionCache[release] = releaseVersion

                    versionNames["release"] = release
                    releaseVersion = null

                    yarnVersions[release] = releaseYarnVersion

                    logger.info { "Cached release version: $release" }
                }
            } else {
                logger.warn { "No yarn build found for release version: $release" }
            }
        }

        if (!versionCache.containsKey(snapshot)) {
            logger.debug { "Caching snapshot version: $snapshot." }

            if (snapshotYarnVersion != null) {
                var snapshotVersion = openMappings(snapshot)

                if (snapshotVersion == null) {
                    logger.warn { "No mappings found for snapshot version: $snapshot" }
                } else {
                    versionCache.remove(versionNames["snapshot"])
                    versionCache[snapshot] = snapshotVersion

                    versionNames["snapshot"] = release
                    snapshotVersion = null

                    yarnVersions[snapshot] = snapshotYarnVersion

                    logger.info { "Cached snapshot version: $snapshot" }
                }
            } else {
                logger.warn { "No yarn build found for snapshot version: $snapshot" }
            }
        }
    }

    /**
     * Given a Minecraft version attempt to open a set of mappings - downloading them if required.
     *
     * @param minecraftVersion Minecraft version to retrieve mappings for
     */
    suspend fun openMappings(minecraftVersion: String): TinyTree? {
        val latestVersion = getLatestYarnVersion(minecraftVersion) ?: return null

        if (versionCache.containsKey(minecraftVersion)) {
            return versionCache[minecraftVersion]
        }

        val tinyPath = cacheDir.resolve("$latestVersion.tiny")
        val jarPath = cacheDir.resolve("$latestVersion.jar")

        if (!Files.exists(tinyPath)) {
            val response = client.request<ByteArray>(
                    config.mappings.mavenUrl.replace("{VERSION}", latestVersion)
            )

            @Suppress("BlockingMethodInNonBlockingContext")
            runSuspended {
                Files.copy(response.inputStream(), jarPath, StandardCopyOption.REPLACE_EXISTING)

                ZipFile(jarPath.toFile()).use { file ->
                    val entry = file.getEntry("mappings/mappings.tiny")

                    file.getInputStream(entry).use { stream ->
                        Files.copy(stream, tinyPath, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        return runSuspended {  // This will be run in an I/O thread
            Files.newBufferedReader(tinyPath, UTF_8).use {
                TinyMappingFactory.loadWithDetection(it)
            }
        }
    }

    private suspend fun getLatestYarnVersion(minecraftVersion: String): String? {
        // TODO: Modmuss says this can easily be expanded to query for specific yarn versions.
        val encodedVersion = URLEncoder.encode(minecraftVersion, UTF_8)
        val url = config.mappings.yarnUrl.replace("{VERSION}", encodedVersion)

        val response = client.get<List<YarnVersion>>(url)

        return if (response.isEmpty()) {
            null
        } else {
            response.first().version
        }
    }
}

/** @suppress This is for testing. **/
suspend fun main() {
    val manager = MappingsManager()

    outputClassMappings(manager, "1.16.4", "class_1937")
    outputClassMappings(manager, "1.16.4", "Block")
    outputClassMappings(manager, "1.16.4", "abc")
    outputMethodMappings(manager, "1.16.4", "getBlockState")
    outputFieldMappings(manager, "1.16.4", "DIRT")
}

/** @suppress This is for testing. **/
suspend fun outputClassMappings(manager: MappingsManager, mcVersion: String, query: String) {
    println("\nGetting mappings: MC $mcVersion / $query \n")

    outputMappings(
            manager.getClassMappings(mcVersion, query) ?: listOf()
    )
}

/** @suppress This is for testing. **/
suspend fun outputMethodMappings(manager: MappingsManager, mcVersion: String, query: String) {
    println("\nGetting mappings: MC $mcVersion / $query \n")

    outputMappings(
            manager.getMethodMappings(mcVersion, query) ?: listOf()
    )
}

/** @suppress This is for testing. **/
suspend fun outputFieldMappings(manager: MappingsManager, mcVersion: String, query: String) {
    println("\nGetting mappings: MC $mcVersion / $query \n")

    outputMappings(
            manager.getFieldMappings(mcVersion, query) ?: listOf()
    )
}

/** @suppress This is for testing. **/
fun outputMappings(results: List<MappingsResult>) {
    results.forEach { result ->
        println("Class name:")
        println("\t${result.classDef.getName(NS_OFFICIAL)}")
        println("\t${result.classDef.getName(NS_INTERMEDIARY)}")
        println("\t${result.classDef.getName(NS_NAMED)}")

        if (result.member == null) {
            println("Access widener: `accessible\tclass\t${result.classDef.getName(NS_NAMED)}`")
        } else {
            println("Name: ${result.member.getName(NS_OFFICIAL)}")
            println("\t${result.member.getName(NS_INTERMEDIARY)}")
            println("\t${result.member.getName(NS_NAMED)}")

            val type = if (result.member is MethodDef) "method" else "field"

            println("Access widener: `accessible\t$type\t${result.classDef.getName(NS_NAMED)}`")
        }
    }
}
