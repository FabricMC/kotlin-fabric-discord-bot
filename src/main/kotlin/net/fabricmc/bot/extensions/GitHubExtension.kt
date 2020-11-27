package net.fabricmc.bot.extensions

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.commands.converters.string
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.respond
import io.ktor.client.HttpClient
import io.ktor.client.features.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPath
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colors
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.utils.requireMainGuild

/**
 * GitHub extension, containing commands for moderating users on GitHub, from discord.
 */
class GitHubExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "github"

    /**
     * Arguments for the !github (un)block commands.
     */
    class GithubUserArgs : Arguments() {
        /** The GitHub username to act on. **/
        val user by string("user")
    }

    override suspend fun setup() {
        group {
            name = "github"
            description = "Commands for working with GitHub."

            check(
                    ::defaultCheck,
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))
            )

            command {
                name = "block"
                aliases = arrayOf("ban")
                description = "Block a user from the ${config.githubOrganization} GitHub organization."
                signature(::GithubUserArgs)

                action {
                    if (!message.requireMainGuild(Roles.ADMIN)) {
                        return@action
                    }

                    with(parse(::GithubUserArgs)) {
                        if (isBlocked(user)) {
                            message.respond {
                                embed {
                                    title = "$user is already blocked."
                                    color = Colors.NEGATIVE
                                }
                            }
                        }

                        val resp = client.put<HttpResponse> {
                            url("/orgs/${config.githubOrganization}/blocks/$user".encodeURLPath())
                        }

                        if (resp.status == HttpStatusCode.NoContent) {
                            message.respond {
                                embed {
                                    title = "$user has been blocked from the ${config.githubOrganization} organization."
                                    color = Colors.POSITIVE
                                }
                            }
                        }
                    }
                }
            }

            command {
                name = "unblock"
                aliases = arrayOf("unban")
                description = "Unblock a user from the ${config.githubOrganization} GitHub organization."
                signature(::GithubUserArgs)

                action {
                    if (!message.requireMainGuild(Roles.ADMIN)) {
                        return@action
                    }

                    with(parse(::GithubUserArgs)) {
                        if (!isBlocked(user)) {
                            message.respond {
                                embed {
                                    title = "$user is not blocked."
                                    color = Colors.NEGATIVE
                                }
                            }
                        }

                        val resp = client.delete<HttpResponse> {
                            url("/orgs/${config.githubOrganization}/blocks/$user".encodeURLPath())
                        }

                        if (resp.status == HttpStatusCode.NoContent) {
                            message.respond {
                                embed {
                                    title = "$user has been unblocked from the ${config.githubOrganization} " +
                                            "organization."
                                    color = Colors.POSITIVE
                                }
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
        val resp = client.get<HttpResponse> {
            url("/orgs/${config.githubOrganization}/blocks/$user".encodeURLPath())
        }

        return when (resp.status) {
            HttpStatusCode.NoContent -> true
            HttpStatusCode.NotFound -> false

            else -> error("User is neither blocked nor unblocked")
        }
    }
}
