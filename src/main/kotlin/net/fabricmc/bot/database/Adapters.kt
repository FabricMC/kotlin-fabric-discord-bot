package net.fabricmc.bot.database

import com.squareup.sqldelight.EnumColumnAdapter

/** Infraction type adapter, adapts between string and enum types for the DB. **/
val infractionTypeAdaptor = Infraction.Adapter(
        infraction_typeAdapter = EnumColumnAdapter()
)
