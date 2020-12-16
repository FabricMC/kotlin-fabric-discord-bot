package net.fabricmc.bot.extensions

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.createdAt
import com.kotlindiscord.kord.extensions.utils.deltas.MemberDelta
import com.kotlindiscord.kord.extensions.utils.deltas.UserDelta
import com.kotlindiscord.kord.extensions.utils.getUrl
import dev.kord.core.event.*
import dev.kord.core.event.channel.*
import dev.kord.core.event.gateway.*
import dev.kord.core.event.guild.*
import dev.kord.core.event.message.*
import dev.kord.core.event.role.RoleCreateEvent
import dev.kord.core.event.role.RoleDeleteEvent
import dev.kord.core.event.role.RoleUpdateEvent
import dev.kord.core.event.user.PresenceUpdateEvent
import dev.kord.core.event.user.UserUpdateEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import kotlinx.coroutines.flow.toSet
import mu.KotlinLogging
import net.fabricmc.bot.*
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colors
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.extensions.infractions.instantToDisplay
import net.fabricmc.bot.utils.actionLog
import net.fabricmc.bot.utils.isNew
import net.fabricmc.bot.utils.modLog
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

private val timeFormatter = DateTimeFormatter
    .ofPattern("dd/MM/yyyy HH:mm:ss '(UTC)'")
    .withLocale(Locale.UK)
    .withZone(ZoneId.of("UTC"))

private val logger = KotlinLogging.logger {}

/**
 * Event logging extension.
 *
 * This extension is in charge of relaying events to the action log and moderator log
 * channels. No advanced filtering or alerting is done here, we're just logging
 * events.
 */
class LoggingExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name: String = "logging"

    override suspend fun setup() {
        event<Event> {
            check(
                inGuild(config.getGuild()),
                ::isNotBot,
                ::isNotIgnoredChannel
            )

            action {
                when (event) {
                    is BanAddEvent -> modLog {
                        val event = event as BanAddEvent

                        color = Colors.NEGATIVE
                        title = "User banned"

                        field { name = "Username"; value = event.user.username; inline = true }
                        field { name = "Discrim"; value = event.user.discriminator; inline = true }

                        footer { text = event.user.id.asString }
                        thumbnail { url = event.user.avatar.url }
                    }

                    is BanRemoveEvent -> modLog {
                        val event = event as BanRemoveEvent

                        color = Colors.POSITIVE
                        title = "User unbanned"

                        field { name = "Username"; value = event.user.username; inline = true }
                        field { name = "Discrim"; value = event.user.discriminator; inline = true }

                        footer { text = event.user.id.asString }
                        thumbnail { url = event.user.avatar.url }
                    }

                    is CategoryCreateEvent -> modLog {
                        val event = event as CategoryCreateEvent

                        color = Colors.POSITIVE
                        title = "Category created"

                        field { name = "Name"; value = event.channel.name; inline = true }
                        field { name = "Mention"; value = event.channel.mention; inline = true }

                        footer { text = event.channel.id.asString }
                    }

                    is CategoryDeleteEvent -> modLog {
                        val event = event as CategoryDeleteEvent

                        color = Colors.NEGATIVE
                        title = "Category deleted"

                        field { name = "Name"; value = event.channel.name; inline = true }
                        field { name = "Mention"; value = event.channel.mention; inline = true }

                        footer { text = event.channel.id.asString }
                    }

                    is InviteCreateEvent -> actionLog {
                        val event = event as InviteCreateEvent

                        color = Colors.POSITIVE
                        title = "Invite created"

                        field { name = "Channel"; value = event.channel.mention; inline = true }
                        field { name = "Code"; value = "`${event.code}`"; inline = true }
                        field { name = "Inviter"; value = event.inviter?.mention ?: "Unknown"; inline = true }
                    }

                    is InviteDeleteEvent -> modLog {
                        val event = event as InviteDeleteEvent

                        color = Colors.NEGATIVE
                        title = "Invite deleted"

                        field { name = "Channel"; value = event.channel.mention; inline = true }
                        field { name = "Code"; value = "`${event.code}`"; inline = true }
                    }

                    is MemberJoinEvent -> actionLog {
                        val event = event as MemberJoinEvent

                        color = Colors.POSITIVE
                        title = "Member joined"

                        field { name = "Username"; value = event.member.username; inline = true }
                        field { name = "Discrim"; value = event.member.discriminator; inline = true }

                        val createdAt = timeFormatter.format(event.member.createdAt)

                        field {
                            name = "Created"

                            value = if (event.member.isNew()) {
                                ":new: $createdAt"
                            } else {
                                createdAt
                            }
                        }

                        footer { text = event.member.id.asString }
                        thumbnail { url = event.member.avatar.url }
                    }

                    is MemberLeaveEvent -> actionLog {
                        val event = event as MemberLeaveEvent

                        color = Colors.NEGATIVE
                        title = "Member left"

                        field { name = "Username"; value = event.user.username; inline = true }
                        field { name = "Discrim"; value = event.user.discriminator; inline = true }

                        footer { text = event.user.id.asString }
                        thumbnail { url = event.user.avatar.url }
                    }

                    is MemberUpdateEvent -> {
                        val event = event as MemberUpdateEvent

                        val new = event.member
                        val delta = MemberDelta.from(event.old, new)

                        if (delta?.changes?.isEmpty() == true) {
                            logger.debug { "No changes found." }
                        } else {
                            actionLog {
                                color = Colors.BLURPLE
                                title = "Member updated"

                                field {
                                    name = "Username"

                                    value = if (delta?.username != null) {
                                        "**${new.username}**"
                                    } else {
                                        new.username
                                    }

                                    inline = true
                                }

                                field {
                                    name = "Discrim"

                                    value = if (delta?.discriminator != null) {
                                        "**${new.discriminator}**"
                                    } else {
                                        new.discriminator
                                    }

                                    inline = true
                                }

                                if (delta?.avatar != null) {
                                    field {
                                        name = "Avatar"
                                        inline = true

                                        value = "[New avatar](${delta.avatar})"
                                    }
                                }

                                if (delta?.nickname != null) {
                                    field {
                                        name = "Nickname"
                                        inline = true

                                        value = if (new.nickname == null) {
                                            "**Removed**"
                                        } else {
                                            "**Updated:** ${new.nickname}"
                                        }
                                    }
                                }

                                if (delta?.boosting != null) {
                                    field {
                                        name = "Boost status"
                                        inline = true

                                        value = if (new.premiumSince == null) {
                                            "**No longer boosting**"
                                        } else {
                                            "**Boosting since**: " + timeFormatter.format(new.premiumSince)
                                        }
                                    }
                                }

                                if (delta?.owner != null) {
                                    field {
                                        name = "Server owner"
                                        inline = true

                                        value = if (delta.owner == true) {
                                            "**Gained server ownership**"
                                        } else {
                                            "**Lost server ownership**"
                                        }
                                    }
                                }

                                if (delta?.roles != null) {
                                    val oldRoles = event.old?.roles?.toSet() ?: setOf()
                                    val newRoles = new.roles.toSet()

                                    if (oldRoles != newRoles) {
                                        val added = newRoles - oldRoles
                                        val removed = oldRoles - newRoles

                                        if (added.isNotEmpty()) {
                                            field {
                                                name = "Roles added"

                                                value = added.joinToString(" ") { role -> role.mention }
                                            }
                                        }

                                        if (removed.isNotEmpty()) {
                                            field {
                                                name = "Roles removed"

                                                value = removed.joinToString(" ") { role -> role.mention }
                                            }
                                        }
                                    }
                                }

                                footer {
                                    text = if (delta == null) {
                                        "Not cached: ${new.id.asString}"
                                    } else {
                                        new.id.asString
                                    }
                                }

                                thumbnail { url = new.avatar.url }
                            }
                        }
                    }

                    is MessageBulkDeleteEvent -> modLog {
                        // TODO: There's a Flow<Message> we could use for something.
                        // I don't think outputting all the messages to the channel is a good idea, though.
                        val event = event as MessageBulkDeleteEvent

                        color = Colors.NEGATIVE
                        title = "Bulk message delete"

                        field { name = "Channel"; value = event.channel.mention; inline = true }
                        field { name = "Count"; value = event.messageIds.size.toString(); inline = true }
                    }

                    is MessageDeleteEvent -> actionLog {
                        val event = event as MessageDeleteEvent

                        color = Colors.NEGATIVE
                        title = "Message deleted"

                        val message = event.message

                        if (message != null) {
                            description = message.content

                            if (message.author != null) {
                                field { name = "Author"; value = message.author!!.mention; inline = true }
                            } else {
                                field { name = "Author"; value = "Unknown Author"; inline = true }
                            }

                            field { name = "Channel"; value = event.channel.mention; inline = true }
                            field { name = "Created"; value = instantToDisplay(event.messageId.timeStamp)!! }

                            field { name = "Attachments"; value = message.attachments.size.toString(); inline = true }
                            field { name = "Embeds"; value = message.embeds.size.toString(); inline = true }

                            field {
                                inline = true

                                name = "Reactions"
                                value = message.reactions.sumBy { reaction -> reaction.count }.toString()
                            }
                        } else {
                            description = "_Message was not cached, so information about it is unavailable._"

                            field { name = "Channel"; value = event.channel.mention }
                            field { name = "Created"; value = instantToDisplay(event.messageId.timeStamp)!! }
                        }

                        footer { text = event.messageId.toString() }
                    }

                    is MessageUpdateEvent -> if ((event as MessageUpdateEvent).getMessage().author != null) {
                        val event = event as MessageUpdateEvent

                        actionLog {
                            color = Colors.BLURPLE
                            title = "Message edited"

                            val old = event.old
                            val new = event.getMessage()

                            field { name = "Author"; value = new.author!!.mention; inline = true }
                            field { name = "Channel"; value = new.channel.mention; inline = true }

                            if (new.editedTimestamp != null) {
                                field {
                                    inline = true

                                    name = "Edited at"
                                    value = timeFormatter.format(new.editedTimestamp!!)
                                }
                            }

                            field { name = "Attachments"; value = new.attachments.size.toString(); inline = true }
                            field { name = "Embeds"; value = new.embeds.size.toString(); inline = true }

                            field {
                                inline = true

                                name = "Reactions"
                                value = new.reactions.sumBy { reaction -> reaction.count }.toString()
                            }

                            field { name = "URL"; value = new.getUrl() }

                            description = when {
                                old == null -> """
                                _Message was not cached, so some information about it is unavailable._
                                
                                **__New message content__**

                                ${new.content}
                            """.trimIndent()

                                old.content != new.content -> """
                                **__Old message content__**

                                ${old.content}
                            """.trimIndent()

                                else -> "**__Message content not edited__**"
                            }

                            footer { text = "Author: ${new.author?.id ?: "Unknown"} Message Id: ${event.messageId}" }
                        }
                    }

                    is NewsChannelCreateEvent -> modLog {
                        val event = event as NewsChannelCreateEvent

                        color = Colors.POSITIVE
                        title = "News channel created"

                        val category = event.channel.category

                        if (category != null) {
                            field { name = "Category"; value = category.asChannel().name; inline = true }
                        }

                        field { name = "Mention"; value = event.channel.mention; inline = true }
                        field { name = "Name"; value = "#${event.channel.name}"; inline = true }

                        footer { text = event.channel.id.asString }
                    }

                    is NewsChannelDeleteEvent -> modLog {
                        val event = event as NewsChannelDeleteEvent

                        color = Colors.NEGATIVE
                        title = "News channel deleted"

                        val category = event.channel.category

                        if (category != null) {
                            field { name = "Category"; value = category.asChannel().name; inline = true }
                        }

                        field { name = "Channel"; value = "#${event.channel.name}"; inline = true }

                        footer { text = event.channel.id.asString }
                    }

                    is ReactionRemoveAllEvent -> if ((event as ReactionRemoveAllEvent).getMessage().author != null) {
                        val event = event as ReactionRemoveAllEvent

                        modLog {
                            color = Colors.NEGATIVE
                            title = "All reactions removed"

                            val message = event.getMessage()

                            field { name = "Author"; value = message.author!!.mention; inline = true }
                            field { name = "Channel"; value = message.channel.mention; inline = true }

                            field { name = "Message"; value = message.getUrl() }

                            footer {
                                text = "Author: ${message.author?.id ?: "Unknown"} Message Id: ${event.messageId}"
                            }
                        }
                    }

                    is ReactionRemoveEmojiEvent -> if (
                        (event as ReactionRemoveEmojiEvent).getMessage().author != null
                    ) {
                        val event = event as ReactionRemoveEmojiEvent

                        modLog {
                            color = Colors.NEGATIVE
                            title = "All reactions removed"

                            val message = event.getMessage()

                            field { name = "Author"; value = message.author!!.mention; inline = true }
                            field { name = "Channel"; value = message.channel.mention; inline = true }
                            field { name = "Emoji"; value = event.emoji.mention; inline = true }

                            field { name = "Message"; value = message.getUrl() }

                            footer {
                                text = "Author: ${message.author?.id ?: "Unknown"} Message Id: ${event.messageId}"
                            }
                        }
                    }

                    is RoleCreateEvent -> modLog {
                        val event = event as RoleCreateEvent

                        color = Colors.POSITIVE
                        title = "Role created"

                        field { name = "Name"; value = event.role.name; inline = true }

                        footer { text = event.role.id.asString }
                    }

                    is RoleDeleteEvent -> modLog {
                        val event = event as RoleDeleteEvent

                        color = Colors.NEGATIVE
                        title = "Role deleted"

                        val role = event.role

                        if (role == null) {
                            description = "_Role was not cached, so information about it is unavailable._"
                        } else {
                            field { name = "Name"; value = role.name; inline = true }
                        }

                        footer { text = event.roleId.toString() }
                    }

                    is StoreChannelCreateEvent -> modLog {
                        val event = event as StoreChannelCreateEvent

                        color = Colors.POSITIVE
                        title = "Store channel created"

                        val category = event.channel.category

                        if (category != null) {
                            field { name = "Category"; value = category.asChannel().name; inline = true }
                        }

                        field { name = "Mention"; value = event.channel.mention; inline = true }
                        field { name = "Name"; value = "#${event.channel.name}"; inline = true }

                        footer { text = event.channel.id.asString }
                    }

                    is StoreChannelDeleteEvent -> modLog {
                        val event = event as StoreChannelDeleteEvent

                        color = Colors.NEGATIVE
                        title = "Store channel deleted"

                        val category = event.channel.category

                        if (category != null) {
                            field { name = "Category"; value = category.asChannel().name; inline = true }
                        }

                        field { name = "Channel"; value = "#${event.channel.name}"; inline = true }

                        footer { text = event.channel.id.asString }
                    }

                    is TextChannelCreateEvent -> {
                        val event = event as TextChannelCreateEvent

                        val category = event.channel.category

                        if (
                            category == null ||
                            category.id != config.getChannel(Channels.ACTION_LOG_CATEGORY).id
                        ) {
                            modLog {
                                color = Colors.POSITIVE
                                title = "Text channel created"

                                if (category != null) {
                                    field { name = "Category"; value = category.asChannel().name; inline = true }
                                }

                                field { name = "Mention"; value = event.channel.mention; inline = true }
                                field { name = "Name"; value = "#${event.channel.name}"; inline = true }

                                footer { text = event.channel.id.asString }
                            }
                        }
                    }

                    is TextChannelDeleteEvent -> {
                        val event = event as TextChannelDeleteEvent

                        val category = event.channel.category

                        if (
                            category == null ||
                            category.id != config.getChannel(Channels.ACTION_LOG_CATEGORY).id
                        ) {
                            modLog {
                                color = Colors.NEGATIVE
                                title = "Text channel deleted"

                                if (category != null) {
                                    field { name = "Category"; value = category.asChannel().name; inline = true }
                                }

                                field { name = "Channel"; value = "#${event.channel.name}"; inline = true }

                                footer { text = event.channel.id.asString }
                            }
                        }
                    }

                    is VoiceChannelCreateEvent -> modLog {
                        val event = event as VoiceChannelCreateEvent

                        color = Colors.POSITIVE
                        title = "Voice channel created"

                        val category = event.channel.category

                        if (category != null) {
                            field { name = "Category"; value = category.asChannel().name; inline = true }
                        }

                        field { name = "Mention"; value = event.channel.mention; inline = true }
                        field { name = "Name"; value = ""; inline = true }

                        footer { text = event.channel.id.asString }
                    }

                    is VoiceChannelDeleteEvent -> modLog {
                        val event = event as VoiceChannelDeleteEvent

                        color = Colors.NEGATIVE
                        title = "Voice channel deleted"

                        val category = event.channel.category

                        if (category != null) {
                            field { name = "Category"; value = category.asChannel().name; inline = true }
                        }

                        field { name = "Channel"; value = "#${event.channel.name}"; inline = true }

                        footer { text = event.channel.id.asString }
                    }

                    // We're not logging these events, they're either irrelevant or don't
                    // concern a guild. This is an explicit silencing, so we don't trigger
                    // an issue on Sentry.
                    is CategoryUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is ChannelPinsUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is ConnectEvent -> logger.debug { "Ignoring event: $event" }
                    is DMChannelCreateEvent -> logger.debug { "Ignoring event: $event" }
                    is DMChannelDeleteEvent -> logger.debug { "Ignoring event: $event" }
                    is DMChannelUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is DisconnectEvent -> logger.debug { "Ignoring event: $event" }
                    is EmojisUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is GatewayEvent -> logger.debug { "Ignoring event: $event" }
                    is GuildCreateEvent -> logger.debug { "Ignoring event: $event" }
                    is GuildDeleteEvent -> logger.debug { "Ignoring event: $event" }
                    is GuildUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is IntegrationsUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is MembersChunkEvent -> logger.debug { "Ignoring event: $event" }
                    is MessageCreateEvent -> logger.debug { "Ignoring event: $event" }
                    is NewsChannelUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is PresenceUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is ReactionAddEvent -> logger.debug { "Ignoring event: $event" }
                    is ReactionRemoveEvent -> logger.debug { "Ignoring event: $event" }
                    is ReadyEvent -> logger.debug { "Ignoring event: $event" }
                    is ResumedEvent -> logger.debug { "Ignoring event: $event" }
                    is RoleUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is StoreChannelUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is TextChannelUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is TypingStartEvent -> logger.debug { "Ignoring event: $event" }
                    is UserUpdateEvent -> { /* We have more specific handling for this event below. */
                    }
                    is VoiceChannelUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is VoiceServerUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is VoiceStateUpdateEvent -> logger.debug { "Ignoring event: $event" }
                    is WebhookUpdateEvent -> logger.debug { "Ignoring event: $event" }

                    // This is an event we haven't accounted for that we may or
                    // may not want to log.
                    else -> logger.warn { "Unknown event: $event" }
                }
            }
        }

        event<UserUpdateEvent> {
            check(::isNotBot)

            action {
                with(event) {
                    val guild = config.getGuild()

                    if (guild.getMemberOrNull(user.id) == null) {
                        return@action
                    }

                    val delta = UserDelta.from(old, user)

                    if (delta?.changes?.isEmpty() != true) {
                        actionLog {
                            title = "User updated"

                            if (delta?.avatar != null) {
                                field {
                                    name = "Avatar"
                                    inline = true

                                    value = "[New avatar](${delta.avatar})"
                                }
                            }

                            field {
                                name = "Username"

                                value = if (delta?.username != null) {
                                    "**${user.username}**"
                                } else {
                                    user.username
                                }

                                inline = true
                            }

                            field {
                                name = "Discrim"

                                value = if (delta?.discriminator != null) {
                                    "**${user.discriminator}**"
                                } else {
                                    user.discriminator
                                }

                                inline = true
                            }

                            footer {
                                text = if (delta == null) {
                                    "Not cached: ${user.id.asString}"
                                } else {
                                    user.id.asString
                                }
                            }

                            thumbnail { url = user.avatar.url }
                        }
                    }
                }
            }
        }
    }
}
