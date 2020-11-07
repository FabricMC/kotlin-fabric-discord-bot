package net.fabricmc.bot.tags

import kotlin.system.exitProcess

/** @suppress **/
fun main(args: Array<String>) {
    val dir = if (args.isNotEmpty()) {
        args.first()
    } else {
        "."
    }

    val parser = TagParser(dir)

    if (parser.loadAll().isNotEmpty()) exitProcess(1)
}
