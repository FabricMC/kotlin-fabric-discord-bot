package net.fabricmc.bot.enums

/**
 * Enum representing the different possible infraction types.
 *
 * @param actionText Text used in the database and to describe the action taken,.
 * @param expires Whether this infraction type may expire.
 * @param relay Whether a user should be notified in private when they receive this infraction.
 * @param requirePresent Whether a user needs to be in the guild in order for this infraction to be applied at all.
 */
enum class InfractionTypes(
        val actionText: String,
        val expires: Boolean = true,
        val relay: Boolean = true,
        val requirePresent: Boolean = false) {
    BAN("banned"),
    KICK("kicked", false, requirePresent = true),

    MUTE("muted"),
    META_MUTE("meta-muted"),
    REACTION_MUTE("reaction-muted"),
    REQUESTS_MUTE("requests-muted"),
    SUPPORT_MUTE("support-muted"),

    WARN("warned", false),
    NOTE("noted", false, false);

    override fun toString() = this.actionText
}
