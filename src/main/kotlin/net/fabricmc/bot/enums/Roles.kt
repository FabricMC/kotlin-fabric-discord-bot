package net.fabricmc.bot.enums

/**
 * An enum representing each type of role that may be configured, for easier reference.
 *
 * @param value A human-readable representation of the given role.
 */
enum class Roles(val value: String) {
    ADMIN("Admin"),
    MODERATOR("Moderator"),
    TRAINEE_MODERATOR("Trainee Moderator"),

    MUTED("Muted"),

    NO_META("No Meta"),
    NO_REACTIONS("No Reactions"),
    NO_REQUESTS("No Requests"),
    NO_SUPPORT("No Support"),

    DEV_LIFE("Dev Life"),
}
