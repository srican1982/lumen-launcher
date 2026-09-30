package com.lumen.launcher.travel.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TripEntity::class, TripPhotoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TravelDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile private var instance: TravelDatabase? = null

        fun get(context: Context): TravelDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TravelDatabase::class.java,
                    "lumen_travel.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
