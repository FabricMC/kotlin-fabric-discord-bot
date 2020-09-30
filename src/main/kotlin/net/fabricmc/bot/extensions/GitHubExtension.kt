package net.fabricmc.bot.extensions

import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import io.ktor.client.HttpClient
import io.ktor.client.features.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.client.request.put
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPath
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles

/**
 * GitHub extension, containing commands for moderating users on GitHub, from discord.
 */
class GitHubExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "github"

    /**
     * Arguments for the !github (un)block commands.
     *
     * @param user the GitHub username to act on.
     */
    data class BlockArgs(
        val user: String,
    )

    override suspend fun setup() {
        group {
            name = "github"
            check(
                ::defaultCheck,
                topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
            )

            command {
                name = "block"
                aliases = arrayOf("ban")
                description = """
                    Block a user from the ${config.githubOrganization} organization.
                """.trimIndent()
                action {
                    with(parse<BlockArgs>()) {
                        if (isBlocked(user)) {
                            message.channel.createEmbed {
                                title = "$user is already blocked."
                                color = Colours.NEGATIVE
                            }
                        }
                        val resp = client.put<HttpResponse> {
                            url("/orgs/${config.githubOrganization}/blocks/$user".encodeURLPath())
                        }
                        if (resp.status == HttpStatusCode.NoContent) {
                            message.channel.createEmbed {
                                title = "$user has been blocked from the ${config.githubOrganization} organization."
                                color = Colours.POSITIVE
                            }
                        }
                    }
                }

            }
            command {
                name = "unblock"
                aliases = arrayOf("unban")
                description = """
                    Unblock a user from the ${config.githubOrganization} organization.
                """.trimIndent()
                action {
                    with(parse<BlockArgs>()) {
                        if (!isBlocked(user)) {
                            message.channel.createEmbed {
                                title = "$user is not blocked."
                                color = Colours.NEGATIVE
                            }
                        }
                        val resp = client.delete<HttpResponse> {
                            url("/orgs/${config.githubOrganization}/blocks/$user".encodeURLPath())
                        }
                        if (resp.status == HttpStatusCode.NoContent) {
                            message.channel.createEmbed {
                                title = "$user has been unblocked from the ${config.githubOrganization} organization."
                                color = Colours.POSITIVE
                            }
                        }
                    }
                }

            }

        }
    }

    private val client = HttpClient {
        defaultRequest {
            accept(ContentType("application", "vnd.github.v3+json"))
            header("Authorization", "token ${config.githubToken}")
            host = "api.github.com"
        }
    }

    private suspend fun isBlocked(user: String): Boolean {
        val resp =
            client.get<HttpResponse> {
                url("/orgs/${config.githubOrganization}/blocks/$user".encodeURLPath())
            }
        return when (resp.status) {
            HttpStatusCode.NoContent -> true
            HttpStatusCode.NotFound -> false
            else -> error("User is neither blocked nor unblocked")
        }
    }
}
