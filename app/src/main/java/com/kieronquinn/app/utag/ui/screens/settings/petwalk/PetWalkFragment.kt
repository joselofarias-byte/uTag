package com.kieronquinn.app.utag.ui.screens.settings.petwalk

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.model.petwalk.PetWalkEventType
import com.kieronquinn.app.utag.model.petwalk.PetWalkGeometry
import com.kieronquinn.app.utag.service.UTagPetWalkService
import com.kieronquinn.app.utag.ui.base.BackAvailable
import com.kieronquinn.app.utag.ui.base.BaseSettingsFragment
import com.kieronquinn.app.utag.ui.screens.settings.petwalk.PetWalkViewModel.State
import com.kieronquinn.app.utag.utils.extensions.whenResumed
import com.kieronquinn.app.utag.utils.preferences.onChange
import com.kieronquinn.app.utag.utils.preferences.onClick
import com.kieronquinn.app.utag.utils.preferences.preference
import com.kieronquinn.app.utag.utils.preferences.preferenceCategory
import com.kieronquinn.app.utag.utils.preferences.switchPreference
import com.kieronquinn.app.utag.utils.preferences.tipsCardPreference
import dev.oneuiproject.oneui.widget.Toast
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.DateFormat
import java.util.Date

class PetWalkFragment: BaseSettingsFragment(), BackAvailable {

    private val viewModel by viewModel<PetWalkViewModel>()
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupState()
        setupEvents()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun setupState() {
        handleState(viewModel.state.value)
        whenResumed {
            viewModel.state.collect { handleState(it) }
        }
    }

    private fun setupEvents() = whenResumed {
        viewModel.events.collect { event ->
            when (event) {
                is PetWalkViewModel.Event.StartService -> {
                    UTagPetWalkService.start(requireContext(), event.walkId)
                }
                is PetWalkViewModel.Event.StopService -> {
                    UTagPetWalkService.stop(requireContext())
                }
                is PetWalkViewModel.Event.Toast -> {
                    Toast.makeText(requireContext(), event.messageRes, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleState(state: State) {
        when (state) {
            is State.Loading -> setLoading(true)
            is State.Loaded -> setList(state)
        }
    }

    private fun setList(state: State.Loaded) = setPreferences {
        tipsCardPreference {
            title = getString(R.string.pet_walk_experimental_title)
            summary = getString(R.string.pet_walk_intro)
        }
        preferenceCategory("pet_walk_switch") {
            switchPreference {
                key = "pet_walk_enabled"
                title = getString(R.string.pet_walk_experimental_switch)
                summary = getString(R.string.pet_walk_experimental_summary)
                isChecked = state.enabled
                onChange<Boolean> { viewModel.onExperimentalChanged(it) }
            }
        }
        if (!state.hasLocationPermission) {
            tipsCardPreference {
                title = getString(R.string.pet_walk_permission_title)
                summary = getString(R.string.pet_walk_permission)
            }
        }
        preferenceCategory("pet_walk_record") {
            title = getString(R.string.pet_walk_record_category)
            preference {
                key = "pet_walk_tag"
                title = getString(R.string.pet_walk_pick_tag)
                summary = state.selectedTag?.label ?: getString(R.string.pet_walk_pick_tag_none)
                isEnabled = state.enabled && state.active == null && state.tags.isNotEmpty()
                onClick { showTagPicker(state) }
            }
            val selected = state.selectedTag
            if (selected?.serverPetWalkingEnabled == true || state.active?.serverPetWalkingEnabled == true) {
                tipsCardPreference {
                    title = getString(R.string.pet_walk_server_flag_title)
                    summary = getString(
                        R.string.pet_walk_server_flag,
                        state.active?.label ?: selected?.label.orEmpty()
                    )
                }
            }
            val active = state.active
            if (active != null) {
                preference {
                    key = "pet_walk_active"
                    title = getString(R.string.pet_walk_active_title, active.label)
                    summary = getString(
                        R.string.pet_walk_active_summary,
                        timeFormat.format(Date(active.startedAt)),
                        PetWalkGeometry.formatDistance(active.summary.phoneDistanceMetres),
                        PetWalkGeometry.formatDistance(active.summary.tagDistanceMetres),
                        active.summary.locatedTagSamples,
                        active.summary.sampleCount
                    )
                    isSelectable = false
                }
                preference {
                    key = "pet_walk_stop"
                    title = getString(R.string.pet_walk_stop)
                    summary = if (state.serviceRunning) {
                        getString(R.string.pet_walk_recording_running)
                    } else {
                        getString(R.string.pet_walk_recording_paused)
                    }
                    isEnabled = state.enabled
                    onClick { viewModel.onStop() }
                }
                if (!state.serviceRunning) {
                    preference {
                        key = "pet_walk_resume"
                        title = getString(R.string.pet_walk_resume)
                        isEnabled = state.enabled && state.hasLocationPermission
                        onClick { viewModel.onStartOrResume() }
                    }
                }
                preference {
                    key = "pet_walk_toilet"
                    title = getString(R.string.pet_walk_event_toilet)
                    summary = getString(R.string.pet_walk_event_summary)
                    isEnabled = state.enabled
                    onClick { viewModel.onMarkEvent(PetWalkEventType.TOILET, null) }
                }
                preference {
                    key = "pet_walk_interaction"
                    title = getString(R.string.pet_walk_event_interaction)
                    summary = getString(R.string.pet_walk_event_summary)
                    isEnabled = state.enabled
                    onClick { viewModel.onMarkEvent(PetWalkEventType.INTERACTION, null) }
                }
                preference {
                    key = "pet_walk_note"
                    title = getString(R.string.pet_walk_event_note)
                    summary = getString(R.string.pet_walk_event_summary)
                    isEnabled = state.enabled
                    onClick { showNoteDialog() }
                }
            } else {
                preference {
                    key = "pet_walk_start"
                    title = getString(R.string.pet_walk_start)
                    summary = getString(R.string.pet_walk_start_summary)
                    isEnabled = state.enabled && state.hasLocationPermission && state.selectedTag != null
                    onClick { viewModel.onStartOrResume() }
                }
            }
        }
        preferenceCategory("pet_walk_history_category") {
            preference {
                key = "pet_walk_history"
                title = getString(R.string.pet_walk_history)
                summary = getString(R.string.pet_walk_history_summary)
                onClick { viewModel.onHistoryClicked() }
            }
        }
    }

    private fun showTagPicker(state: State.Loaded) {
        if (state.tags.isEmpty()) return
        val labels = state.tags.map { it.label }.toTypedArray()
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.pet_walk_pick_tag)
            setItems(labels) { _, which ->
                viewModel.onTagSelected(state.tags[which].id)
            }
        }.show()
    }

    private fun showNoteDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.pet_walk_event_note_hint)
        }
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.pet_walk_event_note_title)
            setView(input)
            setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.onMarkEvent(PetWalkEventType.NOTE, input.text?.toString())
            }
            setNegativeButton(android.R.string.cancel, null)
        }.show()
    }

}
