package com.kieronquinn.app.utag.ui.screens.settings.findcatalog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.model.find.FindCatalogEntry
import com.kieronquinn.app.utag.model.find.FindCatalogKind
import com.kieronquinn.app.utag.model.find.FindDeviceClassifier
import com.kieronquinn.app.utag.repositories.FindCatalogRepository
import com.kieronquinn.app.utag.repositories.FindCatalogRepository.TagLocateResult
import com.kieronquinn.app.utag.repositories.SettingsRepository
import com.kieronquinn.app.utag.ui.base.BackAvailable
import com.kieronquinn.app.utag.ui.base.BaseSettingsFragment
import com.kieronquinn.app.utag.utils.extensions.whenResumed
import com.kieronquinn.app.utag.utils.preferences.onChange
import com.kieronquinn.app.utag.utils.preferences.onClick
import com.kieronquinn.app.utag.utils.preferences.preference
import com.kieronquinn.app.utag.utils.preferences.preferenceCategory
import com.kieronquinn.app.utag.utils.preferences.switchPreference
import com.kieronquinn.app.utag.utils.preferences.tipsCardPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.DateFormat
import java.util.Date

abstract class FindCatalogViewModel: ViewModel() {

    abstract val state: StateFlow<State>

    abstract fun onResume()
    abstract fun onExperimentalChanged(enabled: Boolean)
    abstract fun onRefreshLocations()

    sealed class State {
        data object Loading: State()
        data class Loaded(
            val enabled: Boolean,
            val entries: List<FindCatalogEntry>?,
            val locations: Map<String, TagLocateResult>,
            val locating: Boolean
        ): State()
    }

}

class FindCatalogViewModelImpl(
    private val settingsRepository: SettingsRepository,
    private val findCatalogRepository: FindCatalogRepository
): FindCatalogViewModel() {

    private val entries = MutableStateFlow<List<FindCatalogEntry>?>(null)
    private val loadedOnce = MutableStateFlow(false)
    private val locations = MutableStateFlow<Map<String, TagLocateResult>>(emptyMap())
    private val locating = MutableStateFlow(false)

    override val state = combine(
        settingsRepository.findCatalogExperimentalEnabled.asFlow(),
        entries,
        locations,
        locating,
        loadedOnce
    ) { enabled, rows, fixes, busy, ready ->
        if (!ready && enabled) {
            State.Loading
        } else {
            State.Loaded(
                enabled = enabled,
                entries = if (enabled) rows else null,
                locations = fixes,
                locating = busy
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    init {
        viewModelScope.launch {
            settingsRepository.findCatalogExperimentalEnabled.asFlow().collect { enabled ->
                if (enabled) {
                    refreshList()
                } else {
                    loadedOnce.emit(true)
                }
            }
        }
    }

    override fun onResume() {
        viewModelScope.launch {
            if (settingsRepository.findCatalogExperimentalEnabled.get()) {
                refreshList()
            }
        }
    }

    override fun onExperimentalChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.findCatalogExperimentalEnabled.set(enabled)
            if (!enabled) {
                locations.emit(emptyMap())
            }
        }
    }

    override fun onRefreshLocations() {
        viewModelScope.launch {
            val rows = entries.value ?: return@launch
            val requests = FindDeviceClassifier.locateRequests(rows)
            if (requests.isEmpty() || locating.value) return@launch
            locating.emit(true)
            val next = locations.value.toMutableMap()
            requests.forEach { request ->
                next[request.stableId] = findCatalogRepository.locateTag(request.tagDeviceId)
                locations.emit(next.toMap())
            }
            locating.emit(false)
        }
    }

    private suspend fun refreshList() {
        entries.emit(findCatalogRepository.load())
        loadedOnce.emit(true)
    }

}

class FindCatalogFragment: BaseSettingsFragment(), BackAvailable {

    private val viewModel by viewModel<FindCatalogViewModel>()
    private val timeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handleState(viewModel.state.value)
        whenResumed {
            viewModel.state.collect { handleState(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun handleState(state: FindCatalogViewModel.State) {
        when (state) {
            is FindCatalogViewModel.State.Loading -> setLoading(true)
            is FindCatalogViewModel.State.Loaded -> setPreferences {
                tipsCardPreference {
                    title = getString(R.string.find_catalog_experimental_title)
                    summary = getString(R.string.find_catalog_intro)
                }
                preferenceCategory("find_catalog_switch") {
                    switchPreference {
                        key = "find_catalog_enabled"
                        title = getString(R.string.find_catalog_experimental_switch)
                        summary = getString(R.string.find_catalog_experimental_summary)
                        isChecked = state.enabled
                        onChange<Boolean> { viewModel.onExperimentalChanged(it) }
                    }
                }
                if (!state.enabled) return@setPreferences
                val rows = state.entries
                if (rows == null) {
                    tipsCardPreference {
                        title = getString(R.string.find_catalog_error_title)
                        summary = getString(R.string.find_catalog_error)
                    }
                    return@setPreferences
                }
                preferenceCategory("find_catalog_summary") {
                    title = getString(R.string.find_catalog_summary_title)
                    preference {
                        key = "find_catalog_counts"
                        title = getString(
                            R.string.find_catalog_counts,
                            rows.count { it.kind == FindCatalogKind.TAG },
                            rows.count { it.kind == FindCatalogKind.FMM_DEVICE },
                            rows.count { it.kind == FindCatalogKind.PERSON },
                            rows.count {
                                it.kind == FindCatalogKind.SHARED_ACCOUNT ||
                                    it.kind == FindCatalogKind.UNKNOWN
                            }
                        )
                        summary = getString(R.string.find_catalog_counts_summary)
                        isSelectable = false
                    }
                    val tagCount = rows.count { it.tagDeviceId != null }
                    preference {
                        key = "find_catalog_refresh"
                        title = getString(R.string.find_catalog_refresh)
                        summary = if (tagCount == 0) {
                            getString(R.string.find_catalog_refresh_none)
                        } else if (state.locating) {
                            getString(R.string.find_catalog_refresh_running)
                        } else {
                            getString(R.string.find_catalog_refresh_summary, tagCount)
                        }
                        isEnabled = tagCount > 0 && !state.locating
                        onClick { viewModel.onRefreshLocations() }
                    }
                }
                FindCatalogKind.entries.forEach { kind ->
                    val section = rows.filter { it.kind == kind }
                    if (section.isEmpty()) return@forEach
                    preferenceCategory("find_catalog_${kind.name}") {
                        title = getString(kindTitle(kind))
                        section.forEach { entry ->
                            preference {
                                key = entry.stableId
                                title = entry.title ?: getString(kindFallback(kind))
                                summary = describe(entry, state.locations[entry.stableId])
                                isSelectable = false
                            }
                        }
                    }
                }
            }
        }
    }

    private fun describe(entry: FindCatalogEntry, location: TagLocateResult?): String {
        val lines = mutableListOf<String>()
        entry.locationType?.let {
            lines += getString(R.string.find_catalog_location_type, it)
        }
        when {
            entry.tagDeviceId != null -> {
                lines += getString(R.string.find_catalog_can_locate)
            }
            entry.kind == FindCatalogKind.TAG -> {
                lines += getString(R.string.find_catalog_tag_missing_id)
            }
            entry.kind == FindCatalogKind.PERSON -> {
                lines += getString(R.string.find_catalog_person_only)
            }
            else -> {
                lines += getString(R.string.find_catalog_not_located)
            }
        }
        entry.fmmDeviceId?.let {
            lines += getString(R.string.find_catalog_fmm_id, it)
        }
        entry.shareGeolocation?.let {
            lines += getString(
                R.string.find_catalog_share,
                getString(if (it) R.string.find_catalog_yes else R.string.find_catalog_no)
            )
        }
        entry.mutualAgreement?.let {
            lines += getString(
                R.string.find_catalog_agreement,
                getString(if (it) R.string.find_catalog_yes else R.string.find_catalog_no)
            )
        }
        when (location) {
            is TagLocateResult.Located -> {
                lines += getString(
                    R.string.find_catalog_located,
                    location.latitude,
                    location.longitude,
                    timeFormat.format(Date(location.time)),
                    getString(
                        if (location.cached) R.string.find_catalog_cached
                        else R.string.find_catalog_live
                    )
                )
            }
            is TagLocateResult.Unavailable -> {
                lines += getString(reasonText(location.reason))
            }
            null -> Unit
        }
        return lines.joinToString(separator = "\n")
    }

    private fun kindTitle(kind: FindCatalogKind): Int {
        return when (kind) {
            FindCatalogKind.TAG -> R.string.find_catalog_kind_tags
            FindCatalogKind.FMM_DEVICE -> R.string.find_catalog_kind_devices
            FindCatalogKind.SHARED_ACCOUNT -> R.string.find_catalog_kind_accounts
            FindCatalogKind.PERSON -> R.string.find_catalog_kind_people
            FindCatalogKind.UNKNOWN -> R.string.find_catalog_kind_unknown
        }
    }

    private fun kindFallback(kind: FindCatalogKind): Int {
        return when (kind) {
            FindCatalogKind.TAG -> R.string.find_catalog_unnamed_tag
            FindCatalogKind.FMM_DEVICE -> R.string.find_catalog_unnamed_device
            FindCatalogKind.SHARED_ACCOUNT -> R.string.find_catalog_unnamed_account
            FindCatalogKind.PERSON -> R.string.find_catalog_unnamed_person
            FindCatalogKind.UNKNOWN -> R.string.find_catalog_unnamed_unknown
        }
    }

    private fun reasonText(reason: TagLocateResult.Reason): Int {
        return when (reason) {
            TagLocateResult.Reason.NO_LOCATION -> R.string.find_catalog_reason_none
            TagLocateResult.Reason.PIN_REQUIRED -> R.string.find_catalog_reason_pin
            TagLocateResult.Reason.NOT_ALLOWED -> R.string.find_catalog_reason_denied
            TagLocateResult.Reason.ERROR -> R.string.find_catalog_reason_error
        }
    }

}
