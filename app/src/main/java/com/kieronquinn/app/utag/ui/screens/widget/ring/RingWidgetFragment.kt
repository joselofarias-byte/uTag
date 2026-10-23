package com.kieronquinn.app.utag.ui.screens.widget.ring

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.repositories.RingOutcome
import com.kieronquinn.app.utag.ui.activities.BaseWidgetConfigurationActivity
import com.kieronquinn.app.utag.ui.activities.RingShortcutActivity
import com.kieronquinn.app.utag.ui.base.BackAvailable
import com.kieronquinn.app.utag.ui.base.BaseSettingsFragment
import com.kieronquinn.app.utag.ui.base.ProvidesTitle
import com.kieronquinn.app.utag.ui.screens.widget.picker.WidgetDevicePickerFragment.Companion.setupSingleTagPickerListener
import com.kieronquinn.app.utag.ui.screens.widget.ring.RingWidgetViewModel.Event
import com.kieronquinn.app.utag.ui.screens.widget.ring.RingWidgetViewModel.State
import com.kieronquinn.app.utag.utils.extensions.whenResumed
import com.kieronquinn.app.utag.utils.preferences.onClick
import com.kieronquinn.app.utag.utils.preferences.preference
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class RingWidgetFragment : BaseSettingsFragment(), BackAvailable, ProvidesTitle {

    private val isShortcut by lazy { requireActivity() is RingShortcutActivity }

    private val viewModel by viewModel<RingWidgetViewModel> {
        val widgetId = if (isShortcut) {
            -1
        } else {
            BaseWidgetConfigurationActivity.getAppWidgetId(requireActivity())
        }
        parametersOf(widgetId, isShortcut)
    }

    override fun getTitle(): CharSequence {
        return getString(if (isShortcut) R.string.ring_shortcut_title else R.string.ring_widget_title)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSingleTagPickerListener(RingWidgetViewModelImpl.KEY_PICKER) {
            viewModel.onTagChosen(it)
        }
        whenResumed {
            viewModel.events.collect { handleEvent(it) }
        }
        whenResumed {
            viewModel.state.collect { handleState(it) }
        }
        if (savedInstanceState == null) {
            whenResumed {
                viewModel.promptIfNeeded()
            }
        }
    }

    private fun handleState(state: State) {
        when (state) {
            is State.Loading -> setLoading(true)
            is State.Loaded -> setPreferences {
                preference {
                    title = getString(R.string.ring_widget_pick_title)
                    summary = state.label ?: getString(R.string.ring_widget_unset)
                    onClick { viewModel.onChangeTagClicked() }
                }
            }
        }
    }

    private fun handleEvent(event: Event) {
        when (event) {
            Event.CloseWidget -> {
                requireActivity().setResult(Activity.RESULT_OK, Intent())
                requireActivity().finish()
            }
            is Event.ShortcutFinished -> {
                val message = when (event.outcome) {
                    RingOutcome.RINGING -> R.string.ring_widget_started
                    RingOutcome.STOPPED -> R.string.ring_widget_stopped
                    RingOutcome.STOP_FAILED -> R.string.ring_widget_stop_failed
                    RingOutcome.FAILED -> R.string.ring_widget_failed
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                requireActivity().finish()
            }
            Event.Failed -> {
                Toast.makeText(
                    requireContext(),
                    R.string.ring_widget_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

}
