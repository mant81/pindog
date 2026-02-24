package com.sschoi.pindog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PinEntity::class], version = 1)
abstract class PinDatabase : RoomDatabase() {
    abstract fun pinDao(): PinDao

    companion object {
        @Volatile
        private var INSTANCE: PinDatabase? = null

        fun getDatabase(context: Context): PinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PinDatabase::class.java,
                    "pin_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
