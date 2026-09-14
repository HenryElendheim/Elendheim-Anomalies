package com.elendheim.anomalies.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedCreatureDao {
    @Query("SELECT * FROM owned_creature ORDER BY caughtAt DESC")
    fun observeAll(): Flow<List<OwnedCreatureEntity>>

    @Query("SELECT * FROM owned_creature WHERE id = :id")
    suspend fun byId(id: Long): OwnedCreatureEntity?

    @Query("SELECT * FROM owned_creature")
    suspend fun all(): List<OwnedCreatureEntity>

    @Query("SELECT COUNT(*) FROM owned_creature")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(entity: OwnedCreatureEntity): Long

    @Update
    suspend fun update(entity: OwnedCreatureEntity)

    @Query("DELETE FROM owned_creature WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM owned_creature")
    suspend fun clear()
}

@Dao
interface StopDao {
    @Query("SELECT * FROM stop ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<StopEntity>>

    @Query("SELECT * FROM stop")
    suspend fun all(): List<StopEntity>

    @Query("SELECT * FROM stop WHERE id = :id")
    suspend fun byId(id: Long): StopEntity?

    @Insert
    suspend fun insert(entity: StopEntity): Long

    @Update
    suspend fun update(entity: StopEntity)

    @Query("DELETE FROM stop WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM stop")
    suspend fun clear()
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM item")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM item")
    suspend fun all(): List<ItemEntity>

    @Query("SELECT quantity FROM item WHERE type = :type")
    suspend fun quantityOf(type: String): Int?

    @Upsert
    suspend fun upsert(entity: ItemEntity)

    @Query("DELETE FROM item")
    suspend fun clear()
}

@Dao
interface PlayerDao {
    @Query("SELECT * FROM player WHERE id = :id")
    fun observe(id: Int = PlayerStateEntity.SINGLETON_ID): Flow<PlayerStateEntity?>

    @Query("SELECT * FROM player WHERE id = :id")
    suspend fun get(id: Int = PlayerStateEntity.SINGLETON_ID): PlayerStateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entity: PlayerStateEntity)

    @Update
    suspend fun update(entity: PlayerStateEntity)
}

@Dao
interface CatchLogDao {
    @Insert
    suspend fun insert(entity: CatchLogEntity)

    @Query("SELECT * FROM catch_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<CatchLogEntity>

    @Query("SELECT COUNT(*) FROM catch_log")
    suspend fun count(): Int

    @Query("DELETE FROM catch_log")
    suspend fun clear()
}
