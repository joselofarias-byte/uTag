package com.kieronquinn.app.utag.ui.screens.settings.petwalk.history

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.components.navigation.SettingsNavigation
import com.kieronquinn.app.utag.model.petwalk.PetWalkGeometry
import com.kieronquinn.app.utag.repositories.PetWalkRepository
import com.kieronquinn.app.utag.ui.base.BackAvailable
import com.kieronquinn.app.utag.ui.base.BaseSettingsFragment
import com.kieronquinn.app.utag.utils.extensions.whenResumed
import com.kieronquinn.app.utag.utils.preferences.onClick
import com.kieronquinn.app.utag.utils.preferences.preference
import com.kieronquinn.app.utag.utils.preferences.tipsCardPreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.DateFormat
import java.util.Date

abstract class PetWalkHistoryViewModel: ViewModel() {

    abstract val state: StateFlow<State>

    abstract fun onWalkClicked(id: Long)

    sealed class State {
        data object Loading: State()
        data class Loaded(val walks: List<PetWalkRepository.PetWalkHistoryRow>): State()
    }

}

class PetWalkHistoryViewModelImpl(
    private val navigation: SettingsNavigation,
    petWalkRepository: PetWalkRepository
): PetWalkHistoryViewModel() {

    override val state = petWalkRepository.observeHistory().map { walks ->
        State.Loaded(walks)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override fun onWalkClicked(id: Long) {
        viewModelScope.launch {
            navigation.navigate(
                PetWalkHistoryFragmentDirections
                    .actionPetWalkHistoryFragmentToPetWalkDetailFragment(id)
            )
        }
    }

}

class PetWalkHistoryFragment: BaseSettingsFragment(), BackAvailable {

    private val viewModel by viewModel<PetWalkHistoryViewModel>()
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handleState(viewModel.state.value)
        whenResumed {
            viewModel.state.collect { handleState(it) }
        }
    }

    private fun handleState(state: PetWalkHistoryViewModel.State) {
        when (state) {
            is PetWalkHistoryViewModel.State.Loading -> setLoading(true)
            is PetWalkHistoryViewModel.State.Loaded -> setPreferences {
                if (state.walks.isEmpty()) {
                    tipsCardPreference {
                        title = getString(R.string.pet_walk_history)
                        summary = getString(R.string.pet_walk_history_empty)
                    }
                }
                state.walks.forEach { row ->
                    preference {
                        key = "walk_${row.walk.id}"
                        title = getString(
                            R.string.pet_walk_history_item_title,
                            row.walk.deviceLabel,
                            dateFormat.format(Date(row.walk.startedAt))
                        )
                        summary = getString(
                            R.string.pet_walk_history_item_summary,
                            if (row.walk.endedAt == null) {
                                getString(R.string.pet_walk_in_progress)
                            } else {
                                PetWalkGeometry.formatDuration(row.summary.durationMillis)
                            },
                            PetWalkGeometry.formatDistance(row.summary.phoneDistanceMetres),
                            PetWalkGeometry.formatDistance(row.summary.tagDistanceMetres),
                            row.summary.eventCount
                        )
                        onClick { viewModel.onWalkClicked(row.walk.id) }
                    }
                }
            }
        }
    }

}
