package com.kieronquinn.app.utag.ui.screens.settings.petwalk

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.utag.components.navigation.SettingsNavigation
import com.kieronquinn.app.utag.model.petwalk.PetWalkEventType
import com.kieronquinn.app.utag.model.petwalk.PetWalkSummary
import com.kieronquinn.app.utag.model.petwalk.TagSampleStatus
import com.kieronquinn.app.utag.repositories.DeviceRepository
import com.kieronquinn.app.utag.repositories.PetWalkRepository
import com.kieronquinn.app.utag.repositories.SettingsRepository
import com.kieronquinn.app.utag.service.UTagPetWalkService
import com.kieronquinn.app.utag.utils.extensions.hasLocationPermissions
import com.kieronquinn.app.utag.utils.extensions.isServiceRunning
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class PetWalkViewModel: ViewModel() {

    abstract val state: StateFlow<State>
    abstract val events: MutableSharedFlow<Event>

    abstract fun onResume()
    abstract fun onExperimentalChanged(enabled: Boolean)
    abstract fun onTagSelected(deviceId: String)
    abstract fun onStartOrResume()
    abstract fun onStop()
    abstract fun onMarkEvent(type: PetWalkEventType, note: String?)
    abstract fun onHistoryClicked()

    data class TagOption(
        val id: String,
        val label: String,
        val serverPetWalkingEnabled: Boolean
    )

    sealed class State {
        data object Loading: State()
        data class Loaded(
            val enabled: Boolean,
            val tags: List<TagOption>,
            val selectedDeviceId: String?,
            val hasLocationPermission: Boolean,
            val serviceRunning: Boolean,
            val active: ActiveWalk?
        ): State() {
            val selectedTag: TagOption?
                get() = tags.firstOrNull { it.id == selectedDeviceId }
        }
    }

    data class ActiveWalk(
        val id: Long,
        val label: String,
        val startedAt: Long,
        val serverPetWalkingEnabled: Boolean,
        val summary: PetWalkSummary,
        val lastTagStatus: TagSampleStatus?
    )

    sealed class Event {
        data class StartService(val walkId: Long): Event()
        data object StopService: Event()
        data class Toast(val messageRes: Int): Event()
    }

}

class PetWalkViewModelImpl(
    private val navigation: SettingsNavigation,
    private val settingsRepository: SettingsRepository,
    private val deviceRepository: DeviceRepository,
    private val petWalkRepository: PetWalkRepository,
    private val context: Context
): PetWalkViewModel() {

    private val resumeBus = MutableStateFlow(System.currentTimeMillis())
    private val selectedDeviceId = MutableStateFlow<String?>(null)

    override val events = MutableSharedFlow<Event>()

    private val tags = resumeBus.mapLatest {
        deviceRepository.getDevices().orEmpty().map { device ->
            TagOption(
                id = device.deviceId,
                label = device.label.ifBlank { device.name },
                serverPetWalkingEnabled = device.petWalkingEnabled
            )
        }
    }

    override val state = combine(
        settingsRepository.petWalkingExperimentalEnabled.asFlow(),
        tags,
        selectedDeviceId,
        petWalkRepository.observeActive(),
        resumeBus
    ) { enabled, tagList, selected, active, _ ->
        val selectedId = when {
            active != null -> active.walk.deviceId
            selected != null && tagList.any { it.id == selected } -> selected
            else -> tagList.firstOrNull()?.id
        }
        State.Loaded(
            enabled = enabled,
            tags = tagList,
            selectedDeviceId = selectedId,
            hasLocationPermission = context.hasLocationPermissions(),
            serviceRunning = context.isServiceRunning(UTagPetWalkService::class.java),
            active = active?.toActive()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override fun onResume() {
        viewModelScope.launch {
            resumeBus.emit(System.currentTimeMillis())
        }
    }

    override fun onExperimentalChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.petWalkingExperimentalEnabled.set(enabled)
            if (!enabled) {
                petWalkRepository.activeWalk()?.let { petWalkRepository.stopWalk(it.id) }
                events.emit(Event.StopService)
            }
        }
    }

    override fun onTagSelected(deviceId: String) {
        viewModelScope.launch {
            selectedDeviceId.emit(deviceId)
        }
    }

    override fun onStartOrResume() {
        viewModelScope.launch {
            val loaded = state.value as? State.Loaded ?: return@launch
            if (!loaded.enabled) return@launch
            if (!loaded.hasLocationPermission) {
                events.emit(Event.Toast(com.kieronquinn.app.utag.R.string.pet_walk_permission))
                return@launch
            }
            val existing = petWalkRepository.activeWalk()
            val walkId = existing?.id ?: run {
                val tag = loaded.selectedTag ?: run {
                    events.emit(Event.Toast(com.kieronquinn.app.utag.R.string.pet_walk_no_tags))
                    return@launch
                }
                petWalkRepository.startWalk(
                    tag.id,
                    tag.label,
                    tag.serverPetWalkingEnabled
                )
            }
            if (walkId == null) {
                events.emit(Event.Toast(com.kieronquinn.app.utag.R.string.pet_walk_already_recording))
                return@launch
            }
            events.emit(Event.StartService(walkId))
        }
    }

    override fun onStop() {
        viewModelScope.launch {
            petWalkRepository.activeWalk()?.let { petWalkRepository.stopWalk(it.id) }
            events.emit(Event.StopService)
        }
    }

    override fun onMarkEvent(type: PetWalkEventType, note: String?) {
        viewModelScope.launch {
            val walk = petWalkRepository.activeWalk() ?: return@launch
            val draft = petWalkRepository.captureDraft(walk.deviceId)
            petWalkRepository.recordEvent(walk.id, type, note, draft)
            if (draft.phone == null && draft.tag == null) {
                events.emit(Event.Toast(com.kieronquinn.app.utag.R.string.pet_walk_event_saved_without_location))
            }
        }
    }

    override fun onHistoryClicked() {
        viewModelScope.launch {
            navigation.navigate(
                PetWalkFragmentDirections.actionPetWalkFragmentToPetWalkHistoryFragment()
            )
        }
    }

    private fun PetWalkRepository.PetWalkSnapshot.toActive(): ActiveWalk {
        return ActiveWalk(
            id = walk.id,
            label = walk.deviceLabel,
            startedAt = walk.startedAt,
            serverPetWalkingEnabled = walk.serverPetWalkingEnabled,
            summary = summary,
            lastTagStatus = samples.lastOrNull()?.toDraft()?.tagStatus
        )
    }

}
