package com.beemind.personal.data.local

import androidx.room.TypeConverter
import com.beemind.personal.domain.ExpressiveTrait

class TraitConverters {
    @TypeConverter fun encode(value: ExpressiveTrait): String = value.name
    @TypeConverter fun decode(value: String): ExpressiveTrait = ExpressiveTrait.valueOf(value)
}
