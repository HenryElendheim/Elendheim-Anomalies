package com.elendheim.anomalies.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        OwnedCreatureEntity::class,
        StopEntity::class,
        ItemEntity::class,
        PlayerStateEntity::class,
        CatchLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ownedCreatures(): OwnedCreatureDao
    abstract fun stops(): StopDao
    abstract fun items(): ItemDao
    abstract fun player(): PlayerDao
    abstract fun catchLog(): CatchLogDao

    companion object {
        /**
         * Migrations are wired in from the very first version. Nothing goes here yet,
         * but the hook existing from day one means a later schema change can never be
         * shipped as a destructive rebuild that wipes a real collection.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "elendheim-anomalies.db",
            ).addMigrations(*MIGRATIONS).build().also { instance = it }
        }
    }
}
