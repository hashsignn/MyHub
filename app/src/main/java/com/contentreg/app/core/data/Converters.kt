package com.contentreg.app.core.data

import androidx.room.TypeConverter
import com.contentreg.app.feature2_url.registry.BlockEntrySource
import com.contentreg.app.feature2_url.registry.BlockEntryType

/**
 * M2.2 — Room type converters. Enums are stored as their stable `name` strings (the migration's
 * columns are TEXT), so adding new enum constants later is backward-compatible.
 */
class Converters {

    @TypeConverter
    fun blockEntryTypeToString(value: BlockEntryType): String = value.name

    @TypeConverter
    fun stringToBlockEntryType(value: String): BlockEntryType = BlockEntryType.valueOf(value)

    @TypeConverter
    fun blockEntrySourceToString(value: BlockEntrySource): String = value.name

    @TypeConverter
    fun stringToBlockEntrySource(value: String): BlockEntrySource = BlockEntrySource.valueOf(value)
}
