package com.symmetry.ordereat.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter
    fun fromChoices(value: List<OptionChoiceJson>): String = json.encodeToString(value)

    @TypeConverter
    fun toChoices(value: String): List<OptionChoiceJson> = json.decodeFromString(value)

    @TypeConverter
    fun fromSelections(value: List<SelectedOptionJson>): String = json.encodeToString(value)

    @TypeConverter
    fun toSelections(value: String): List<SelectedOptionJson> = json.decodeFromString(value)
}
