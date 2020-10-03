package net.fabricmc.bot.extensions.filter

/**
 * Filter concerns define what types of data a [Filter] cares about.
 *
 * Define these in [Filter.concerns]. If an event is missing the data a [Filter] cares
 * about, the filter will be skipped.
 */
enum class FilterConcerns {
    CONTENT, EMBEDS, ATTACHMENTS
}
