package net.fabricmc.bot.extensions.mappings

import kotlinx.serialization.Serializable

/**
 * Data class representing a Yarn version from Fabric Meta.
 *
 * @param gameVersion Matching version of Minecraft
 * @param separator Build separator string
 * @param build Build number
 * @param maven Maven coordinate
 * @param version Yarn version
 * @param stable Whether this version is marked stable
 */
@Serializable
data class YarnVersion(
        val gameVersion: String,
        val separator: String,
        val build: Int,
        val maven: String,
        val version: String,
        val stable: Boolean
)
