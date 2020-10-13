package net.fabricmc.bot.enums

/**
 * Enum representing the different possible infraction types.
 *
 * @param actionText Text used in the database and to describe the action taken,.
 * @param verb Verb describing the associated action, used for display only.
 * @param expires Whether this infraction type may expire.
 * @param relay Whether a user should be notified in private when they receive this infraction.
 * @param requirePresent Whether a user needs to be in the guild in order for this infraction to be applied at all.
 * @param notForTrainees Whether an infraction type requires a non-trainee moderator or not.
 */
enum class InfractionTypes(
        val actionText: String,
        val verb: String,
        val expires: Boolean = true,
        val relay: Boolean = true,
        val requirePresent: Boolean = false,
        val notForTrainees: Boolean = false
) {
    BAN("banned", "ban", notForTrainees = true),
    KICK("kicked", "kick", false, requirePresent = true),

    MUTE("muted", "mute"),
    META_MUTE("meta-muted", "meta-mute"),
    REACTION_MUTE("reaction-muted", "reaction-mute"),
    REQUESTS_MUTE("requests-muted", "requests-mute"),
    SUPPORT_MUTE("support-muted", "support-mute"),

    NICK_LOCK("nick-locked", "nick-lock", requirePresent = true),

    WARN("warned", "warn", false),
    NOTE("noted", "note", false, false);

    override fun toString() = this.actionText
}

/**
 * Get the first infraction type with action text that starts with a given string.
 *
 * @param string String to match.
 * @return Matching [InfractionTypes] entry, or `null` if nothing was matched.
 */
fun getInfractionType(string: String) =
        InfractionTypes.values()
                .firstOrNull { it.actionText.startsWith(string.toLowerCase()) }
