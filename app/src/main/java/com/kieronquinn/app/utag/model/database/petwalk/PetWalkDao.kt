package com.kieronquinn.app.utag.model.database.petwalk

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PetWalkDao {

    @Query("select * from pet_walk where ended_at is null order by started_at desc limit 1")
    fun observeActive(): Flow<PetWalkEntity?>

    @Query("select * from pet_walk where ended_at is null order by started_at desc limit 1")
    suspend fun getActive(): PetWalkEntity?

    @Query("select * from pet_walk order by started_at desc")
    fun observeWalks(): Flow<List<PetWalkEntity>>

    @Query("select * from pet_walk where id = :id")
    fun observeWalk(id: Long): Flow<PetWalkEntity?>

    @Query("select * from pet_walk where id = :id")
    suspend fun getWalk(id: Long): PetWalkEntity?

    @Insert
    suspend fun insertWalk(walk: PetWalkEntity): Long

    @Query("update pet_walk set ended_at = :endedAt where id = :id and ended_at is null")
    suspend fun endWalk(id: Long, endedAt: Long)

    @Query("select * from pet_walk_sample where walk_id = :walkId order by recorded_at asc")
    fun observeSamples(walkId: Long): Flow<List<PetWalkSampleEntity>>

    @Query("select * from pet_walk_sample where walk_id = :walkId order by recorded_at asc")
    suspend fun getSamples(walkId: Long): List<PetWalkSampleEntity>

    @Query("select * from pet_walk_sample where walk_id = :walkId order by recorded_at desc limit 1")
    suspend fun latestSample(walkId: Long): PetWalkSampleEntity?

    @Insert
    suspend fun insertSample(sample: PetWalkSampleEntity)

    @Query("select * from pet_walk_event where walk_id = :walkId order by recorded_at asc")
    fun observeEvents(walkId: Long): Flow<List<PetWalkEventEntity>>

    @Query("select * from pet_walk_event where walk_id = :walkId order by recorded_at asc")
    suspend fun getEvents(walkId: Long): List<PetWalkEventEntity>

    @Insert
    suspend fun insertEvent(event: PetWalkEventEntity)

    @Query("delete from pet_walk where id = :id")
    suspend fun deleteWalk(id: Long)

    @Query("delete from pet_walk_sample where walk_id = :id")
    suspend fun deleteSamples(id: Long)

    @Query("delete from pet_walk_event where walk_id = :id")
    suspend fun deleteEvents(id: Long)

}
