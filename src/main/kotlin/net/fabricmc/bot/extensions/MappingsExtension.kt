package net.fabricmc.bot.extensions

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.Paginator
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.converters.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.string
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.respond
import io.ktor.client.features.ClientRequestException
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.events.LatestMinecraftVersionsRetrieved
import net.fabricmc.bot.extensions.mappings.*
//import net.fabricmc.bot.utils.requireBotChannel
import net.fabricmc.mapping.tree.MethodDef

//private const val DELETE_DELAY = 1000L * 15L // 15 seconds
private const val PAGE_TIMEOUT = 1000L * 60L * 5L  // 5 minutes
private val VERSION_REGEX = "[a-z0-9.]+".toRegex(RegexOption.IGNORE_CASE)

private val logger = KotlinLogging.logger {}

/**
 * Extension that handles retrieval and querying of mappings data.
 */
class MappingsExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "mappings"

    private val mappings = MappingsManager()
    private val versionsExtension get() = bot.extensions["version check"] as VersionCheckExtension

    override suspend fun setup() {
        config.mappings.defaultVersions.forEach { version ->
            mappings.openMappings(version)
        }

        event<LatestMinecraftVersionsRetrieved> {
            action {
                logger.debug { "Caching latest versions: ${it.versions.release} / ${it.versions.snapshot}" }

                @Suppress("TooGenericExceptionCaught")
                try {
                    mappings.cacheMappings(it.versions.release, it.versions.snapshot)
                } catch (t: Throwable) {
                    logger.error(t) { "Failed to cache mappings." }
                }
            }
        }

        command {
            name = "class"
            aliases = arrayOf("yc", "yarnclass", "yarn-class")
            description = "Retrieve mappings for a given class name.\n\n" +
                    "You may specify the Minecraft version as the second parameter - omit it to default to the " +
                    "latest release. You can also provide `release` or `snapshot` for the latest release or snapshot " +
                    "version respectively."

            check(::defaultCheck)
            signature(::MappingsClassArgs)

            action {
//                if (!message.requireBotChannel(delay = DELETE_DELAY)) {
//                    return@action
//                }

                with(parse(::MappingsClassArgs)) {
                    val mcVersion = when (version?.toLowerCase()) {
                        null -> versionsExtension.latestRelease

                        "release" -> mappings.versionNames[version!!.toLowerCase()] ?: versionsExtension.latestRelease
                        "snapshot" -> mappings.versionNames[version!!.toLowerCase()] ?: versionsExtension.latestSnapshot

                        else -> version
                    }

                    if (mcVersion == null) {
                        message.respond(
                                "I'm still loading up the latest Minecraft version information - " +
                                        "try again later!"
                        )

                        return@action
                    }

                    if (!VERSION_REGEX.matches(mcVersion)) {
                        message.respond(
                                "Invalid Minecraft version specified: `$mcVersion`"
                        )

                        return@action
                    }

                    val mappingsData = try {
                        mappings.getClassMappings(mcVersion, className)
                    } catch (e: ClientRequestException) {
                        message.respond(
                                "Unable to download Yarn for Minecraft `$mcVersion` - " +
                                        "it may not yet be supported."
                        )

                        return@action
                    }

                    if (mappingsData == null) {
                        message.respond(
                                "Unable to find Yarn mappings for Minecraft `$mcVersion`."
                        )

                        return@action
                    } else if (mappingsData.isEmpty()) {
                        message.respond(
                                "Unable to find any matching class names."
                        )

                        return@action
                    }

                    paginate(this@action, mcVersion, mappingsData)
                }
            }
        }

        command {
            name = "field"
            aliases = arrayOf("yf", "yarnfield", "yarn-field")
            description = "Retrieve mappings for a given field name.\n\n" +
                    "You may specify the Minecraft version as the second parameter - omit it to default to the " +
                    "latest release. You can also provide `release` or `snapshot` for the latest release or snapshot " +
                    "version respectively."

            check(::defaultCheck)
            signature(::MappingsFieldArgs)

            action {
//                if (!message.requireBotChannel(delay = DELETE_DELAY)) {
//                    return@action
//                }

                with(parse(::MappingsFieldArgs)) {
                    val mcVersion = when (version?.toLowerCase()) {
                        null -> versionsExtension.latestRelease

                        "release" -> mappings.versionNames[version!!.toLowerCase()] ?: versionsExtension.latestRelease
                        "snapshot" -> mappings.versionNames[version!!.toLowerCase()] ?: versionsExtension.latestSnapshot

                        else -> version
                    }

                    if (mcVersion == null) {
                        message.respond(
                                "I'm still loading up the latest Minecraft version information - " +
                                        "try again later!"
                        )

                        return@action
                    }

                    if (!VERSION_REGEX.matches(mcVersion)) {
                        message.respond(
                                "Invalid Minecraft version specified: `$mcVersion`"
                        )

                        return@action
                    }

                    val mappingsData = try {
                        mappings.getFieldMappings(mcVersion, field)
                    } catch (e: ClientRequestException) {
                        message.respond(
                                "Unable to download Yarn for Minecraft `$mcVersion` - " +
                                        "it may not yet be supported."
                        )

                        return@action
                    }

                    if (mappingsData == null) {
                        message.respond(
                                "Unable to find Yarn mappings for Minecraft `$mcVersion`."
                        )

                        return@action
                    } else if (mappingsData.isEmpty()) {
                        message.respond(
                                "Unable to find any matching field names."
                        )

                        return@action
                    }

                    paginate(this@action, mcVersion, mappingsData)
                }
            }
        }

        command {
            name = "method"
            aliases = arrayOf("ym", "yarnmethod", "yarn-method")
            description = "Retrieve mappings for a given method name.\n\n" +
                    "You may specify the Minecraft version as the second parameter - omit it to default to the " +
                    "latest release. You can also provide `release` or `snapshot` for the latest release or snapshot " +
                    "version respectively."

            check(::defaultCheck)
            signature(::MappingsMethodArgs)

            action {
//                if (!message.requireBotChannel(delay = DELETE_DELAY)) {
//                    return@action
//                }

                with(parse(::MappingsMethodArgs)) {
                    val mcVersion = when (version?.toLowerCase()) {
                        null -> versionsExtension.latestRelease

                        "release" -> mappings.versionNames[version!!.toLowerCase()] ?: versionsExtension.latestRelease
                        "snapshot" -> mappings.versionNames[version!!.toLowerCase()] ?: versionsExtension.latestSnapshot

                        else -> version
                    }

                    if (mcVersion == null) {
                        message.respond(
                                "I'm still loading up the latest Minecraft version information - " +
                                        "try again later!"
                        )

                        return@action
                    }

                    if (!VERSION_REGEX.matches(mcVersion)) {
                        message.respond(
                                "Invalid Minecraft version specified: `$mcVersion`"
                        )

                        return@action
                    }

                    val mappingsData = try {
                        mappings.getMethodMappings(mcVersion, method)
                    } catch (e: ClientRequestException) {
                        message.respond(
                                "Unable to download Yarn for Minecraft `$mcVersion` - " +
                                        "it may not yet be supported."
                        )

                        return@action
                    }

                    if (mappingsData == null) {
                        message.respond(
                                "Unable to find Yarn mappings for Minecraft `$mcVersion`."
                        )

                        return@action
                    } else if (mappingsData.isEmpty()) {
                        message.respond(
                                "Unable to find any matching method names."
                        )

                        return@action
                    }

                    paginate(this@action, mcVersion, mappingsData)
                }
            }
        }
    }

    private suspend fun paginate(context: CommandContext, version: String, results: List<MappingsResult>) {
        val pages = results.map {
            var page = ""

            val classDef = it.classDef
            val member = it.member

            // The extra spacing in these strings is just to make things easier to read - it doesn't show on Discord
            page += "__**Class names**__\n\n"
            page += "Official     **»** `${classDef.getName(NS_OFFICIAL)}`\n"
            page += "Intermediary **»** `${classDef.getName(NS_INTERMEDIARY)}`\n"
            page += "Yarn         **»** `${classDef.getName(NS_NAMED)}`\n\n"

            if (member == null) {
                page += "__**Access widener**__\n\n"
                page += "```accessible\tclass\t${classDef.getName(NS_NAMED)}```"
            } else {
                page += "__**Member Names**__\n\n"
                page += "Official     **»** `${member.getName(NS_OFFICIAL)}`\n"
                page += "Intermediary **»** `${member.getName(NS_INTERMEDIARY)}`\n"
                page += "Yarn         **»** `${member.getName(NS_NAMED)}`\n\n"

                val type = if (member is MethodDef) "method" else "field"

                page += "__**Descriptor**__\n\n"
                page += "```${member.getDescriptor(NS_NAMED)}```\n\n"

                page += "__**Access Widener**__\n\n"
                page += "```" +
                        "accessible\t" +
                        "$type\t" +
                        "${classDef.getName(NS_NAMED)}\t" +
                        "${member.getName(NS_NAMED)}\t" +
                        member.getDescriptor(NS_NAMED) +
                        "```"
            }

            page
        }.toList()

        Paginator(
                bot,
                context.message.channel,
                "Minecraft $version / ${results.size} result" + if (results.size > 1) "s" else "",
                pages,
                context.message.author,
                PAGE_TIMEOUT,
                true
        ).send()
    }

    /** Class representing arguments for class-retrieval commands.
     *
     * @property `class` Class name to retrieve mappings for
     * @property version Optional Minecraft version to retrieve mappings for
     */
    @Suppress("UndocumentedPublicProperty")
    class MappingsClassArgs : Arguments() {
        val className by string("class")
        val version by optionalString("version")
    }

    /** Class representing arguments for field-retrieval commands.
     *
     * @property field Field name to retrieve mappings for
     * @property version Optional Minecraft version to retrieve mappings for
     */
    @Suppress("UndocumentedPublicProperty")
    class MappingsFieldArgs : Arguments() {
        val field by string("field")
        val version by optionalString("version")
    }

    /** Class representing arguments for method-retrieval commands.
     *
     * @property method Method name to retrieve mappings for
     * @property version Optional Minecraft version to retrieve mappings for
     */
    @Suppress("UndocumentedPublicProperty")
    class MappingsMethodArgs : Arguments() {
        val method by string("method")
        val version by optionalString("version")
    }
}
