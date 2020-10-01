package net.fabricmc.bot.enums

/**
 * Enum representing the different possible infraction types.
 *
 * @param actionText Text used in the database and to describe the action taken,.
 * @param verb Verb describing the associated action, used for display only.
 * @param expires Whether this infraction type may expire.
 * @param relay Whether a user should be notified in private when they receive this infraction.
 * @param requirePresent Whether a user needs to be in the guild in order for this infraction to be applied at all.
 */
enum class InfractionTypes(
        val actionText: String,
        val verb: String,
        val expires: Boolean = true,
        val relay: Boolean = true,
        val requirePresent: Boolean = false) {
    BAN("banned", "ban"),
    KICK("kicked", "kick", false, requirePresent = true),

    MUTE("muted", "mute"),
    META_MUTE("meta-muted", "meta-mute"),
    REACTION_MUTE("reaction-muted", "reaction-mute"),
    REQUESTS_MUTE("requests-muted", "requests-mute"),
    SUPPORT_MUTE("support-muted", "support-mute"),

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
                .map { it.actionText }
                .firstOrNull { it.startsWith(string.toLowerCase()) }
