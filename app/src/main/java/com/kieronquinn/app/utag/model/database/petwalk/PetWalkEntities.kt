package com.kieronquinn.app.utag.model.database.petwalk

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kieronquinn.app.utag.model.petwalk.GeoPoint
import com.kieronquinn.app.utag.model.petwalk.PetWalkEventType
import com.kieronquinn.app.utag.model.petwalk.PetWalkSampleDraft
import com.kieronquinn.app.utag.model.petwalk.TagSampleStatus

@Entity(tableName = "pet_walk")
data class PetWalkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "device_label")
    val deviceLabel: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "ended_at")
    val endedAt: Long?,
    @ColumnInfo(name = "server_pet_walking_enabled")
    val serverPetWalkingEnabled: Boolean
)

@Entity(
    tableName = "pet_walk_sample",
    indices = [Index(value = ["walk_id"])]
)
data class PetWalkSampleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "walk_id")
    val walkId: Long,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,
    @ColumnInfo(name = "phone_latitude")
    val phoneLatitude: Double?,
    @ColumnInfo(name = "phone_longitude")
    val phoneLongitude: Double?,
    @ColumnInfo(name = "phone_accuracy")
    val phoneAccuracy: Double?,
    @ColumnInfo(name = "phone_captured_at")
    val phoneCapturedAt: Long?,
    @ColumnInfo(name = "tag_latitude")
    val tagLatitude: Double?,
    @ColumnInfo(name = "tag_longitude")
    val tagLongitude: Double?,
    @ColumnInfo(name = "tag_accuracy")
    val tagAccuracy: Double?,
    @ColumnInfo(name = "tag_captured_at")
    val tagCapturedAt: Long?,
    @ColumnInfo(name = "tag_status")
    val tagStatus: String
) {
    fun toDraft(): PetWalkSampleDraft {
        return PetWalkSampleDraft(
            recordedAt = recordedAt,
            phone = point(phoneLatitude, phoneLongitude, phoneAccuracy, phoneCapturedAt),
            tag = point(tagLatitude, tagLongitude, tagAccuracy, tagCapturedAt),
            tagStatus = TagSampleStatus.entries.firstOrNull { it.name == tagStatus }
                ?: TagSampleStatus.ERROR
        )
    }
}

@Entity(
    tableName = "pet_walk_event",
    indices = [Index(value = ["walk_id"])]
)
data class PetWalkEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "walk_id")
    val walkId: Long,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "phone_latitude")
    val phoneLatitude: Double?,
    @ColumnInfo(name = "phone_longitude")
    val phoneLongitude: Double?,
    @ColumnInfo(name = "tag_latitude")
    val tagLatitude: Double?,
    @ColumnInfo(name = "tag_longitude")
    val tagLongitude: Double?,
    @ColumnInfo(name = "tag_status")
    val tagStatus: String
) {
    fun eventType(): PetWalkEventType {
        return PetWalkEventType.entries.firstOrNull { it.name == type } ?: PetWalkEventType.NOTE
    }
}

fun PetWalkSampleDraft.toEntity(walkId: Long): PetWalkSampleEntity {
    return PetWalkSampleEntity(
        walkId = walkId,
        recordedAt = recordedAt,
        phoneLatitude = phone?.latitude,
        phoneLongitude = phone?.longitude,
        phoneAccuracy = phone?.accuracyMetres,
        phoneCapturedAt = phone?.capturedAt,
        tagLatitude = tag?.latitude,
        tagLongitude = tag?.longitude,
        tagAccuracy = tag?.accuracyMetres,
        tagCapturedAt = tag?.capturedAt,
        tagStatus = tagStatus.name
    )
}

fun PetWalkSampleDraft.toEventEntity(
    walkId: Long,
    type: PetWalkEventType,
    note: String?
): PetWalkEventEntity {
    return PetWalkEventEntity(
        walkId = walkId,
        recordedAt = recordedAt,
        type = type.name,
        note = note?.takeIf { it.isNotBlank() },
        phoneLatitude = phone?.latitude,
        phoneLongitude = phone?.longitude,
        tagLatitude = tag?.latitude,
        tagLongitude = tag?.longitude,
        tagStatus = tagStatus.name
    )
}

private fun point(
    latitude: Double?,
    longitude: Double?,
    accuracy: Double?,
    capturedAt: Long?
): GeoPoint? {
    if (latitude == null || longitude == null || capturedAt == null) return null
    return GeoPoint(latitude, longitude, accuracy, capturedAt)
}
