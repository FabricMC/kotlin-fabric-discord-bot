package net.fabricmc.bot.utils

import dev.kord.core.entity.User

/**
 * Given a Kord User object, return a string that contains their mention, tag and ID together.
 */
fun User.readable(): String = "$mention (`$tag` / `${id.value}`)"
