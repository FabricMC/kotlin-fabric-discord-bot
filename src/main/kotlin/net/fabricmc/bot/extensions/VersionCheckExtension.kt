package net.fabricmc.bot.extensions

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
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles

private const val UPDATE_CHECK_DELAY = 1000L * 60L * 5L

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
        event<ReadyEvent> {
            action {
                minecraftVersions = minecraftVersions()
                jiraVersions = jiraVersions()
                if (minecraftVersions.isEmpty() && jiraVersions.isEmpty()) {
                    return@action // No point if we don't have anywhere to post.
                }
                checkJob = bot.kord.launch {
                    while (true) {
                        delay(UPDATE_CHECK_DELAY)
                        updateCheck()
                    }
                }
            }
        }
        command {
            name = "versioncheck"
            description = """
                Force running a version check for Jira and Minecraft, for when you can't wait 5 minutes.
            """.trimIndent()

            check(
                ::defaultCheck,
                topRoleHigherOrEqual(config.getRole(Roles.ADMIN))
            )
            action {
                updateCheck()
            }
        }
    }

    override suspend fun unload() {
        checkJob?.cancel()
    }

    private suspend fun updateCheck() {
        checkForMinecraftUpdates()?.run {
            config.getMinecraftUpdateChannels().forEach {
                it.createMessage(this@run.toString())
            }
        }
        checkForJiraUpdates()?.run {
            config.getJiraUpdateChannels().forEach {
                it.createMessage(this@run.toString())
            }
        }

    }

    private suspend fun checkForMinecraftUpdates(): MinecraftVersion? {
        val versions = minecraftVersions()
        val new = versions.find { it !in minecraftVersions }
        minecraftVersions = versions
        return new
    }

    private suspend fun checkForJiraUpdates(): JiraVersion? {
        val versions = jiraVersions()
        val new = versions.find { it !in jiraVersions && "Future Version" !in it.name }
        jiraVersions = versions
        return new
    }

    private suspend fun jiraVersions(): List<JiraVersion> =
        client.get("https://bugs.mojang.com/rest/api/latest/project/MC/versions")

    private suspend fun minecraftVersions(): List<MinecraftVersion> =
        client.get<LauncherMetaResponse>("https://launchermeta.mojang.com/mc/game/version_manifest.json").versions

}

@Serializable
private data class MinecraftVersion(
    val id: String,
    val type: String,
) {
    override fun toString(): String = "A new $type version of Minecraft was just released! : $id"
}

@Serializable
private data class LauncherMetaResponse(
    val versions: List<MinecraftVersion>,
)

@Serializable
private data class JiraVersion(
    val id: String,
    val name: String,
) {
    override fun toString(): String = "A new version ($name) has been added to the Minecraft issue tracker!"
}
