package net.fabricmc.bot.extensions.mappings

import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.request
import kotlinx.serialization.json.Json
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.runSuspended
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

/**
 * Class in charge of downloading and querying Yarn mappings.
 */
class MappingsManager {
    // TODO: Consider in-memory caching (although note that mappings take up like 250MiB of memory or so)
    private val cacheDir = Path.of(config.mappings.directory)

    private val client = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    init { Files.createDirectories(cacheDir) }

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

        return getMappingsResults(mappings, query, ClassDef::getMethods)
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

        return getMappingsResults(mappings, query, ClassDef::getFields)
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
     * Given a Minecraft version attempt to open a set of mappings - downloading them if required.
     *
     * @param minecraftVersion Minecraft version to retrieve mappings for
     */
    suspend fun openMappings(minecraftVersion: String): TinyTree? {
        val latestVersion = getLatestYarnVersion(minecraftVersion) ?: return null

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
