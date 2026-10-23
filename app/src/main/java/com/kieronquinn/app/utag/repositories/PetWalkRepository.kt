package com.kieronquinn.app.utag.repositories

import android.location.Location
import com.kieronquinn.app.utag.model.database.petwalk.PetWalkDao
import com.kieronquinn.app.utag.model.database.petwalk.PetWalkEntity
import com.kieronquinn.app.utag.model.database.petwalk.PetWalkEventEntity
import com.kieronquinn.app.utag.model.database.petwalk.PetWalkSampleEntity
import com.kieronquinn.app.utag.model.database.petwalk.toEntity
import com.kieronquinn.app.utag.model.database.petwalk.toEventEntity
import com.kieronquinn.app.utag.model.petwalk.GeoPoint
import com.kieronquinn.app.utag.model.petwalk.PetWalkEventType
import com.kieronquinn.app.utag.model.petwalk.PetWalkGeometry
import com.kieronquinn.app.utag.model.petwalk.PetWalkSampleDraft
import com.kieronquinn.app.utag.model.petwalk.PetWalkSummary
import com.kieronquinn.app.utag.model.petwalk.TagSampleStatus
import com.kieronquinn.app.utag.repositories.ApiRepository.GetLocationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withTimeoutOrNull

interface PetWalkRepository {

    fun observeActive(): Flow<PetWalkSnapshot?>

    fun observeHistory(): Flow<List<PetWalkHistoryRow>>

    fun observeWalk(id: Long): Flow<PetWalkSnapshot?>

    suspend fun activeWalk(): PetWalkEntity?

    suspend fun snapshot(id: Long): PetWalkSnapshot?

    suspend fun startWalk(
        deviceId: String,
        deviceLabel: String,
        serverPetWalkingEnabled: Boolean
    ): Long?

    suspend fun stopWalk(id: Long)

    suspend fun deleteWalk(id: Long)

    suspend fun captureDraft(deviceId: String): PetWalkSampleDraft

    suspend fun recordSample(walkId: Long, draft: PetWalkSampleDraft): Boolean

    suspend fun recordEvent(
        walkId: Long,
        type: PetWalkEventType,
        note: String?,
        draft: PetWalkSampleDraft
    )

    data class PetWalkSnapshot(
        val walk: PetWalkEntity,
        val samples: List<PetWalkSampleEntity>,
        val events: List<PetWalkEventEntity>,
        val summary: PetWalkSummary
    )

    data class PetWalkHistoryRow(
        val walk: PetWalkEntity,
        val summary: PetWalkSummary
    )

}

class PetWalkRepositoryImpl(
    private val dao: PetWalkDao,
    private val apiRepository: ApiRepository,
    private val contentCreatorRepository: ContentCreatorRepository
): PetWalkRepository {

    companion object {
        private const val PHONE_LOCATION_TIMEOUT_MS = 8_000L
    }

    override fun observeActive(): Flow<PetWalkRepository.PetWalkSnapshot?> {
        return dao.observeActive().flatMapLatest { walk ->
            if (walk == null) flowOf(null) else observeWalk(walk.id)
        }
    }

    override fun observeHistory(): Flow<List<PetWalkRepository.PetWalkHistoryRow>> {
        return dao.observeWalks().mapLatest { walks ->
            walks.map { walk ->
                val samples = dao.getSamples(walk.id)
                val events = dao.getEvents(walk.id)
                PetWalkRepository.PetWalkHistoryRow(
                    walk = walk,
                    summary = summary(walk, samples, events.size)
                )
            }
        }
    }

    override fun observeWalk(id: Long): Flow<PetWalkRepository.PetWalkSnapshot?> {
        return combine(
            dao.observeWalk(id),
            dao.observeSamples(id),
            dao.observeEvents(id)
        ) { walk, samples, events ->
            walk?.let {
                PetWalkRepository.PetWalkSnapshot(
                    walk = it,
                    samples = samples,
                    events = events,
                    summary = summary(it, samples, events.size)
                )
            }
        }
    }

    override suspend fun activeWalk(): PetWalkEntity? = dao.getActive()

    override suspend fun snapshot(id: Long): PetWalkRepository.PetWalkSnapshot? {
        val walk = dao.getWalk(id) ?: return null
        val samples = dao.getSamples(id)
        val events = dao.getEvents(id)
        return PetWalkRepository.PetWalkSnapshot(
            walk = walk,
            samples = samples,
            events = events,
            summary = summary(walk, samples, events.size)
        )
    }

    override suspend fun startWalk(
        deviceId: String,
        deviceLabel: String,
        serverPetWalkingEnabled: Boolean
    ): Long? {
        if (dao.getActive() != null) return null
        return dao.insertWalk(
            PetWalkEntity(
                deviceId = deviceId,
                deviceLabel = deviceLabel,
                startedAt = System.currentTimeMillis(),
                endedAt = null,
                serverPetWalkingEnabled = serverPetWalkingEnabled
            )
        )
    }

    override suspend fun stopWalk(id: Long) {
        dao.endWalk(id, System.currentTimeMillis())
    }

    override suspend fun deleteWalk(id: Long) {
        dao.deleteEvents(id)
        dao.deleteSamples(id)
        dao.deleteWalk(id)
    }

    override suspend fun captureDraft(deviceId: String): PetWalkSampleDraft {
        val recordedAt = System.currentTimeMillis()
        return PetWalkSampleDraft(
            recordedAt = recordedAt,
            phone = capturePhone(),
            tag = null,
            tagStatus = TagSampleStatus.ERROR
        ).let { phoneOnly ->
            val tag = captureTag(deviceId)
            phoneOnly.copy(tag = tag.first, tagStatus = tag.second)
        }
    }

    override suspend fun recordSample(walkId: Long, draft: PetWalkSampleDraft): Boolean {
        val previous = dao.latestSample(walkId)?.toDraft()
        if (!PetWalkGeometry.shouldKeepSample(previous, draft)) return false
        dao.insertSample(draft.toEntity(walkId))
        return true
    }

    override suspend fun recordEvent(
        walkId: Long,
        type: PetWalkEventType,
        note: String?,
        draft: PetWalkSampleDraft
    ) {
        dao.insertEvent(draft.toEventEntity(walkId, type, note))
    }

    private suspend fun capturePhone(): GeoPoint? {
        val location = try {
            withTimeoutOrNull(PHONE_LOCATION_TIMEOUT_MS) {
                contentCreatorRepository.wrapLocationAsFlow(force = false).filterNotNull().first()
            }
        } catch (e: Exception) {
            null
        }
        return location?.toGeoPoint()
    }

    private suspend fun captureTag(deviceId: String): Pair<GeoPoint?, TagSampleStatus> {
        val result = try {
            contentCreatorRepository.getLocation(deviceId) ?: apiRepository.getLocation(deviceId)
        } catch (e: Exception) {
            return Pair(null, TagSampleStatus.ERROR)
        }
        return when (result) {
            is GetLocationResult.Location -> {
                val location = result.location
                Pair(
                    GeoPoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMetres = location.accuracy,
                        capturedAt = location.time
                    ),
                    TagSampleStatus.LOCATED
                )
            }
            is GetLocationResult.NoLocation -> Pair(null, TagSampleStatus.NO_LOCATION)
            is GetLocationResult.PINRequired,
            is GetLocationResult.NoKeys -> Pair(null, TagSampleStatus.PIN_REQUIRED)
            is GetLocationResult.NotAllowed -> Pair(null, TagSampleStatus.NOT_ALLOWED)
            is GetLocationResult.Error -> Pair(null, TagSampleStatus.ERROR)
        }
    }

    private fun summary(
        walk: PetWalkEntity,
        samples: List<PetWalkSampleEntity>,
        eventCount: Int
    ): PetWalkSummary {
        return PetWalkGeometry.summarize(
            startedAt = walk.startedAt,
            endedAt = walk.endedAt,
            samples = samples.map { it.toDraft() },
            eventCount = eventCount,
            now = System.currentTimeMillis()
        )
    }

    private fun Location.toGeoPoint(): GeoPoint {
        return GeoPoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMetres = if (hasAccuracy()) accuracy.toDouble() else null,
            capturedAt = time.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

}
