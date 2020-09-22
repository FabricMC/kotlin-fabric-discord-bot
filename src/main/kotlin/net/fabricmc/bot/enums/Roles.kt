package net.fabricmc.bot.enums

/**
 * An enum representing each type of role that may be configured, for easier reference.
 *
 * @param value A human-readable representation of the given role.
 */
enum class Roles(val value: String) {
    OWNER("Owner"),
    ADMIN("Admin"),
    MODERATOR("Moderator"),

    MUTED("Muted"),
}
