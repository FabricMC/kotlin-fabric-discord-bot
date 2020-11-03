package net.fabricmc.bot.tags

import java.io.File

/** Given a file and path prefix, return a String representing the file path without the prefix. **/
fun File.withoutPrefix(prefix: String) = this.toString().removePrefix(prefix).replace(File.separator, "/")
