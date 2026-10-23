package com.kieronquinn.app.utag.repositories

import com.kieronquinn.app.utag.model.find.FindCatalogEntry
import com.kieronquinn.app.utag.model.find.FindDeviceClassifier
import com.kieronquinn.app.utag.repositories.ApiRepository.GetLocationResult

interface FindCatalogRepository {

    /**
     * Reads the FMM `GET /devices` list this app already uses for favourites.
     * Returns null when that call fails.
     */
    suspend fun load(): List<FindCatalogEntry>?

    /**
     * Last known SmartTag location. Only valid for a catalog row whose
     * [FindCatalogEntry.tagDeviceId] is set. Phones, buds, watches and people
     * are not passed here: this client has no geolocation call for them.
     */
    suspend fun locateTag(tagDeviceId: String): TagLocateResult

    sealed class TagLocateResult {
        data class Located(
            val latitude: Double,
            val longitude: Double,
            val time: Long,
            val cached: Boolean
        ): TagLocateResult()

        data class Unavailable(val reason: Reason): TagLocateResult()

        enum class Reason {
            NO_LOCATION,
            PIN_REQUIRED,
            NOT_ALLOWED,
            ERROR
        }
    }

}

class FindCatalogRepositoryImpl(
    private val apiRepository: ApiRepository,
    private val contentCreatorRepository: ContentCreatorRepository
): FindCatalogRepository {

    override suspend fun load(): List<FindCatalogEntry>? {
        val response = apiRepository.getDevices() ?: return null
        return FindDeviceClassifier.classify(response)
    }

    override suspend fun locateTag(tagDeviceId: String): FindCatalogRepository.TagLocateResult {
        val result = try {
            contentCreatorRepository.getLocation(tagDeviceId)
                ?: apiRepository.getLocation(tagDeviceId)
        } catch (e: Exception) {
            return FindCatalogRepository.TagLocateResult.Unavailable(
                FindCatalogRepository.TagLocateResult.Reason.ERROR
            )
        }
        return when (result) {
            is GetLocationResult.Location -> {
                FindCatalogRepository.TagLocateResult.Located(
                    latitude = result.location.latitude,
                    longitude = result.location.longitude,
                    time = result.location.time,
                    cached = result.cached
                )
            }
            is GetLocationResult.NoLocation -> unavailable(FindCatalogRepository.TagLocateResult.Reason.NO_LOCATION)
            is GetLocationResult.PINRequired,
            is GetLocationResult.NoKeys -> unavailable(FindCatalogRepository.TagLocateResult.Reason.PIN_REQUIRED)
            is GetLocationResult.NotAllowed -> unavailable(FindCatalogRepository.TagLocateResult.Reason.NOT_ALLOWED)
            is GetLocationResult.Error -> unavailable(FindCatalogRepository.TagLocateResult.Reason.ERROR)
        }
    }

    private fun unavailable(
        reason: FindCatalogRepository.TagLocateResult.Reason
    ): FindCatalogRepository.TagLocateResult {
        return FindCatalogRepository.TagLocateResult.Unavailable(reason)
    }

}
