package com.allen.pokemon.data.local

import androidx.room.TypeConverter
import com.allen.pokemon.data.local.entity.SyncPhaseStatus

class RoomConverters {
    @TypeConverter
    fun syncPhaseStatusToString(value: SyncPhaseStatus): String = value.name

    @TypeConverter
    fun stringToSyncPhaseStatus(value: String): SyncPhaseStatus = SyncPhaseStatus.valueOf(value)
}
