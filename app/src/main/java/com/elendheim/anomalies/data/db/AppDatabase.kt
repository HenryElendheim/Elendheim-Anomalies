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
    version = 2,
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
         * Version two renamed the companion booster to Empower Powder and dropped the
         * second name on a stop. Both tables are rebuilt and copied across rather than
         * recreated empty, which means an existing collection survives the update.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE owned_creature_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        creatureId TEXT NOT NULL,
                        isShiny INTEGER NOT NULL,
                        statPower INTEGER NOT NULL,
                        statGrace INTEGER NOT NULL,
                        statWard INTEGER NOT NULL,
                        xp INTEGER NOT NULL,
                        stage INTEGER NOT NULL,
                        powderSpent INTEGER NOT NULL,
                        caughtAt INTEGER NOT NULL,
                        caughtLat REAL NOT NULL,
                        caughtLng REAL NOT NULL,
                        caughtPlace TEXT NOT NULL,
                        nickname TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO owned_creature_new
                    SELECT id, creatureId, isShiny, statPower, statGrace, statWard, xp, stage,
                           ljosSpent, caughtAt, caughtLat, caughtLng, caughtPlace, nickname
                    FROM owned_creature
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE owned_creature")
                db.execSQL("ALTER TABLE owned_creature_new RENAME TO owned_creature")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_owned_creature_creatureId ON owned_creature (creatureId)")

                db.execSQL(
                    """
                    CREATE TABLE stop_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        lat REAL NOT NULL,
                        lng REAL NOT NULL,
                        radiusMeters INTEGER NOT NULL,
                        cooldownMinutes INTEGER NOT NULL,
                        lastSpunAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        spins INTEGER NOT NULL,
                        itemsEarned INTEGER NOT NULL,
                        bonusSpawns INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO stop_new
                    SELECT id, name, lat, lng, radiusMeters, cooldownMinutes, lastSpunAt,
                           createdAt, spins, itemsEarned, bonusSpawns
                    FROM stop
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE stop")
                db.execSQL("ALTER TABLE stop_new RENAME TO stop")

                db.execSQL(
                    """
                    CREATE TABLE player_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        xp INTEGER NOT NULL,
                        companionId INTEGER,
                        totalCatches INTEGER NOT NULL,
                        distanceMeters REAL NOT NULL,
                        shinies INTEGER NOT NULL,
                        stopSpins INTEGER NOT NULL,
                        metamorphoses INTEGER NOT NULL,
                        powder INTEGER NOT NULL,
                        perfectThrows INTEGER NOT NULL,
                        placesCsv TEXT NOT NULL,
                        pityCounter INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO player_new
                    SELECT id, xp, companionId, totalCatches, distanceMeters, shinies, stopSpins,
                           metamorphoses, ljos, perfectThrows, placesCsv, pityCounter, createdAt
                    FROM player
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE player")
                db.execSQL("ALTER TABLE player_new RENAME TO player")
            }
        }

        /**
         * Every schema change ships with the migration that carries the data across, so
         * an update can never quietly wipe a collection.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

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
