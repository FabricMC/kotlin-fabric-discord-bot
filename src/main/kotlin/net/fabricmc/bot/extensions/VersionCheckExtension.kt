package net.fabricmc.bot.extensions

import com.gitlab.kordlib.common.entity.ChannelType
import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.event.gateway.ReadyEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles

private const val UPDATE_CHECK_DELAY = 1000L * 30L  // 30 seconds, consider kotlin.time when it's not experimental
private const val SETUP_DELAY = 1000L * 10L  // 10 seconds

private var JIRA_URL = "https://bugs.mojang.com/rest/api/latest/project/MC/versions"
private var MINECRAFT_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json"

private val logger = KotlinLogging.logger {}

/** @suppress **/
@Suppress("UndocumentedPublicProperty")
data class UrlCommand(
        val url: String
)

/**
 * Automatic updates on new Minecraft versions, in Jira and launchermeta.
 */
class VersionCheckExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "version check"

    private val client = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    private var minecraftVersions = listOf<MinecraftVersion>()
    private var jiraVersions = listOf<JiraVersion>()
    private var checkJob: Job? = null

    override suspend fun setup() {
        val environment = System.getenv().getOrDefault("ENVIRONMENT", "production")

        event<ReadyEvent> {
            action {
                logger.info { "Delaying setup to ensure everything is cached." }
                delay(SETUP_DELAY)

                if (config.getMinecraftUpdateChannels().isEmpty() && config.getJiraUpdateChannels().isEmpty()) {
                    logger.warn { "No channels are configured, not enabling version checks." }

                    return@action // No point if we don't have anywhere to post.
                }

                logger.info { "Fetching initial data." }

                minecraftVersions = getMinecraftVersions()
                jiraVersions = getJiraVersions()

                logger.debug { "Scheduling check job." }

                checkJob = bot.kord.launch {
                    while (true) {
                        delay(UPDATE_CHECK_DELAY)

                        logger.debug { "Running scheduled check." }

                        updateCheck()
                    }
                }

                logger.info { "Ready to go!" }
            }
        }

        command {
            name = "versioncheck"
            description = "Force running a version check for Jira and Minecraft, for when you can't wait 30 seconds."

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.ADMIN))
            )

            action {
                message.channel.createMessage(
                        "${message.author!!.mention} Manually executing a version check."
                )

                logger.debug { "Version check requested by command." }

                @Suppress("TooGenericExceptionCaught")
                try {
                    updateCheck()

                    message.channel.createEmbed {
                        title = "Version check success"
                        color = Colours.POSITIVE

                        description = "Successfully checked for new Minecraft versions and JIRA releases."

                        field {
                            name = "Latest (JIRA)"
                            value = jiraVersions.last().name

                            inline = true
                        }

                        field {
                            name = "Latest (Minecraft)"
                            value = minecraftVersions.first().id

                            inline = true
                        }
                    }
                } catch (e: Exception) {
                    message.channel.createEmbed {
                        title = "Version check error"
                        color = Colours.NEGATIVE

                        description = "```" +
                                "$e: ${e.stackTraceToString()}" +
                                "```"
                    }
                }
            }
        }

        if (environment != "production") {
            logger.debug { "Registering debugging commands for admins: jira-url and mc-url" }

            command {
                name = "jira-url"
                description = "Change the JIRA update URL, for debugging."

                aliases = arrayOf("jiraurl")

                signature<UrlCommand>()

                check(
                        ::defaultCheck,
                        topRoleHigherOrEqual(config.getRole(Roles.ADMIN))
                )

                action {
                    with(parse<UrlCommand>()) {
                        JIRA_URL = url

                        message.channel.createMessage(
                                "${message.author!!.mention} JIRA URL updated to `$url`."
                        )
                    }
                }
            }

            command {
                name = "mc-url"
                description = "Change the MC update URL, for debugging."

                aliases = arrayOf("mcurl")

                signature<UrlCommand>()

                check(
                        ::defaultCheck,
                        topRoleHigherOrEqual(config.getRole(Roles.ADMIN))
                )

                action {
                    with(parse<UrlCommand>()) {
                        MINECRAFT_URL = url

                        message.channel.createMessage(
                                "${message.author!!.mention} MC URL updated to `$url`."
                        )
                    }
                }
            }
        }
    }

    override suspend fun unload() {
        logger.debug { "Extension unloaded, cancelling job." }

        checkJob?.cancel()
    }

    private suspend fun updateCheck() {
        val mc = checkForMinecraftUpdates()

        if (mc != null) {
            config.getMinecraftUpdateChannels().forEach {
                val message = it.createMessage(
                        "A new Minecraft ${mc.type} is out: ${mc.id}"
                )

                if (it.type == ChannelType.GuildNews) {
                    message.publish()
                }
            }
        }

        val jira = checkForJiraUpdates()

        if (jira != null) {
            config.getJiraUpdateChannels().forEach {
                val message = it.createMessage(
                        "A new version (${jira.name}) has been added to the Minecraft issue tracker!"
                )

                if (it.type == ChannelType.GuildNews) {
                    message.publish()
                }
            }
        }
    }

    private suspend fun checkForMinecraftUpdates(): MinecraftVersion? {
        logger.debug { "Checking for Minecraft updates." }

        val versions = getMinecraftVersions()
        val new = versions.find { it !in minecraftVersions }

        logger.debug { "Minecraft | New version: ${new ?: "N/A"}" }
        logger.debug { "Minecraft | Total versions: " + versions.size }

        minecraftVersions = versions

        return new
    }

    private suspend fun checkForJiraUpdates(): JiraVersion? {
        logger.debug { "Checking for JIRA updates." }

        val versions = getJiraVersions()
        val new = versions.find { it !in jiraVersions && "future version" !in it.name.toLowerCase() }

        logger.debug { "     JIRA | New release: ${new ?: "N/A"}" }

        jiraVersions = versions

        return new
    }

    private suspend fun getJiraVersions(): List<JiraVersion> {
        val response = client.get<List<JiraVersion>>(JIRA_URL)

        logger.debug { "     JIRA | Latest release: " + response.last().name }
        logger.debug { "     JIRA | Total releases: " + response.size }

        return response
    }

    private suspend fun getMinecraftVersions(): List<MinecraftVersion> {
        val response = client.get<LauncherMetaResponse>(MINECRAFT_URL)

        logger.debug { "Minecraft | Latest release: " + response.latest.release }
        logger.debug { "Minecraft | Latest snapshot: " + response.latest.snapshot }

        return response.versions
    }

}

@Serializable
private data class MinecraftVersion(
        val id: String,
        val type: String,
)

@Serializable
private data class MinecraftLatest(
        val release: String,
        val snapshot: String,
)

@Serializable
private data class LauncherMetaResponse(
        val versions: List<MinecraftVersion>,
        val latest: MinecraftLatest
)

@Serializable
private data class JiraVersion(
        val id: String,
        val name: String,
)
