package net.fabricmc.bot.extensions.mappings

import net.fabricmc.mapping.tree.ClassDef
import net.fabricmc.mapping.tree.Descriptored

/**
 * Data class representing a mappings result.
 *
 * @param classDef // TODO
 * @param member // TODO
 */
data class MappingsResult(
        val classDef: ClassDef,
        val member: Descriptored?
)
