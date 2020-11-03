package net.fabricmc.bot.tags

import kotlin.system.exitProcess

/** @suppress **/
fun main() {
    val parser = TagParser(".")

    if (parser.loadAll().isNotEmpty()) exitProcess(1)
}
