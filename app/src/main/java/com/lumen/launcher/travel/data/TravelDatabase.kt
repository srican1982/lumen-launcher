package com.lumen.launcher.travel.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TripEntity::class, TripPhotoEntity::class],
    version = 2,
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
                ).addMigrations(object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE trip_photos ADD COLUMN removed INTEGER NOT NULL DEFAULT 0")
                    }
                }).build().also { instance = it }
            }
    }
}
