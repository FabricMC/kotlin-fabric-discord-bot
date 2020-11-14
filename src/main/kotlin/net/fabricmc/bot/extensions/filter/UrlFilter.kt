package net.fabricmc.bot.extensions.filter

import com.gitlab.kordlib.common.entity.ChannelType
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.core.entity.channel.Channel
import com.gitlab.kordlib.core.event.message.MessageCreateEvent
import com.gitlab.kordlib.core.event.message.MessageUpdateEvent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.deleteIgnoringNotFound
import com.kotlindiscord.kord.extensions.utils.getUrl
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType

private val blacklist = setOf(
        // Adult domains - not an exhaustive list, just commonly-spammed/linked domains across Discord.
        "e621.net", "kink.com", "motherless.com", "paheal.net", "pornhub.com", "redtube.com",
        "xhamster.com", "xnxx.com", "xvideos.com", "youjizz.com", "youporn.com",

        // Piracy - not an exhaustive list, just what's popular.
        "1337x.to", "baybea.net", "beatpb.club", "blue247.pw", "demonoid.is", "eztv.io",
        "fitgirl-repacks.site", "limetorrents.info", "nyaa.eu", "nyaa.net", "nyaa.se",
        "pantsu.cat", "piratebay.life", "piratebay.live", "piratebay.tech", "piratebaylist.com",
        "pirateproxy-bay.com", "pirateproxy.space", "proxydltpb.club", "rarbg.to", "tamilrockers.ws",
        "tbp-mirror.com", "thehiddenbay.com", "thepiratebay.fail", "thepiratebay.fyi", "thepiratebay.org",
        "thepiratebay.rocks", "thepiratebay.vin", "thepiratebay.zone", "torrentz2.eu", "tpb.party",
        "tpbaysproxy.com", "tpbproxypirate.com", "yts.lt", "yts.mx",

        // Shocking content/gore/etc.
        "liveleak.com",

        // Phishing/typo-squatting - not an exhaustive list, just commonly-spammed/linked domains across Discord.
        "discord.gift", "ssteam.site", "steamwalletgift.com", "yourtube.site", "youtubeshort.watch",

        // IP loggers (Grabify) - there's plenty more but these are the most common that I've seen.
        "bmwforum.co", "canadianlumberjacks.online", "catsnthing.com", "catsnthings.fun", "crabrave.pw",
        "curiouscat.club", "discörd.com", "disçordapp.com", "fortnight.space", "fortnitechat.site",
        "freegiftcards.co", "grabify.link", "joinmy.site", "leancoding.co", "minecräft.com",
        "poweredbydialup.club", "poweredbydialup.online", "poweredbysecurity.online", "poweredbysecurity.org",
        "quickmessage.us", "spottyfly.com", "stopify.co", "särahah.eu", "särahah.pl",
        "xda-developers.us", "youshouldclick.us", "youtubeshort.pro", "yoütu.be",

        // Domains that shouldn't be used in a polite conversation.
        "lmgtfy.com"
)

private val extensionBlacklist = setOf(
        // Domain extensions intended for the adult entertainment industry
        // This isn't a full list, some of them are likely to be used for meme or vanity domains,
        // so we shouldn't necessarily block them
        "adult", "porn", "sex", "xxx"
)

private val schemeBlacklist = setOf(
        // URL schemes that should be specifically blacklisted.
        // Torrents tend to be problematic, but there are plenty of legal torrents out there.
        // That said, most of those are distributed using .torrent files, whereas most pirate
        // torrents seem to use magnet links.
        "magnet"
)

/**
 * Filter class intended for finding and removing messages, and alerting staff when problematic
 * urls are posted.
 *
 * This class is *heavily* inspired by the work done by the fine folks at Python Discord.
 * You can find their bot code here: https://github.com/python-discord/bot
 *
 * Our filter differs a little - instead of checking whether the message has _some url_ and then
 * checking that it contains _some blacklisted domain_ anywhere in the content, we extract all
 * URLs with a protocol or domains starting with www., and check whether those end in any of
 * our blacklisted domains and extensions. We also have a larger list of banned domains, and we
 * deal with specific schemes and extensions.
 */
class UrlFilter(bot: ExtensibleBot) : Filter(bot) {
    override val concerns = arrayOf(FilterConcerns.CONTENT)

    private val extractor = LinkExtractor.builder()
            .linkTypes(setOf(LinkType.URL, LinkType.WWW))
            .build()

    override suspend fun checkCreate(event: MessageCreateEvent, content: String): Boolean =
            doCheck(event.message, content)

    override suspend fun checkEdit(event: MessageUpdateEvent, content: String): Boolean =
            doCheck(event.getMessage(), content)

    private suspend fun doCheck(message: Message, content: String): Boolean {
        val domains = extractUrlInfo(content)

        if (domains.isNotEmpty()) {
            message.deleteIgnoringNotFound()

            sendAlert {
                embed {
                    title = "Domain filter triggered!"
                    description = getMessage(message.author!!, message, message.channel.asChannel())
                }
            }

            sendNotification(
                    message,
                    "Your message has been removed, by the URL filter, as it references a blacklisted scheme, " +
                            "domain or domain extension."
            )

            return false
        }

        return true
    }

    private suspend fun getMessage(user: User, message: Message, channel: Channel): String {
        val channelMessage = if (channel.type == ChannelType.GuildText) {
            "in ${channel.mention}"
        } else {
            "in a DM"
        }

        val jumpMessage = if (channel.type == ChannelType.GuildText) {
            "[the following message](${message.getUrl()})"
        } else {
            "the following message"
        }

        return "Domain filter triggered by " +
                "${user.mention} (`${user.id.value}`) $channelMessage, " +
                "with $jumpMessage:\n\n" +
                message.content
    }

    private fun extractUrlInfo(content: String): Set<Pair<String?, String>> {  // Pair(scheme, domain)
        val links = extractor.extractLinks(content)
        val badPairs: MutableList<Pair<String?, String>> = mutableListOf()
        val foundPairs: MutableList<Pair<String?, String>> = mutableListOf()

        for (link in links) {
            var domain = content.substring(link.beginIndex, link.endIndex)
            var scheme: String? = null

            if ("://" in domain) {
                val split = domain.split("://")

                scheme = split[0]
                domain = split[1]
            }

            if ("/" in domain) {
                domain = domain.split("/").first()
            }

            foundPairs += Pair(scheme, domain)
        }

        for (ending in blacklist + extensionBlacklist) {
            for ((scheme, domain) in foundPairs) {
                if (domain.endsWith(ending)) {
                    badPairs += Pair(scheme, domain)
                }
            }
        }

        for ((scheme, domain) in foundPairs) {
            if (scheme in schemeBlacklist) {
                badPairs += Pair(scheme, domain)
            }
        }

        return badPairs.toSet()
    }
}
