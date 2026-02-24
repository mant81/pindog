package com.sschoi.pindog.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPin(pin: PinEntity)

    @Query("SELECT * FROM pins ORDER BY timestamp DESC")
    fun getAllPins(): Flow<List<PinEntity>>

    @Delete
    suspend fun deletePin(pin: PinEntity)
}
