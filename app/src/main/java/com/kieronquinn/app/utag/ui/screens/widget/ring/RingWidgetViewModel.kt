package com.kieronquinn.app.utag.ui.screens.widget.ring

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.components.navigation.WidgetContainerNavigation
import com.kieronquinn.app.utag.providers.RingWidgetProvider
import com.kieronquinn.app.utag.repositories.DeviceRepository
import com.kieronquinn.app.utag.repositories.RingController
import com.kieronquinn.app.utag.repositories.RingOutcome
import com.kieronquinn.app.utag.repositories.RingWidgetStore
import com.kieronquinn.app.utag.ui.activities.RingShortcutActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

abstract class RingWidgetViewModel : ViewModel() {

    abstract val state: StateFlow<State>
    abstract val events: kotlinx.coroutines.flow.SharedFlow<Event>

    abstract fun onChangeTagClicked()
    abstract fun onTagChosen(deviceId: String)
    abstract fun promptIfNeeded()

    sealed class State {
        data object Loading : State()
        data class Loaded(val label: String?) : State()
    }

    sealed class Event {
        data object CloseWidget : Event()
        data class ShortcutFinished(val outcome: RingOutcome, val label: String) : Event()
        data object Failed : Event()
    }

}

class RingWidgetViewModelImpl(
    private val context: Context,
    private val navigation: WidgetContainerNavigation,
    private val devices: DeviceRepository,
    private val store: RingWidgetStore,
    private val controller: RingController,
    private val appWidgetId: Int,
    private val isShortcut: Boolean
) : RingWidgetViewModel() {

    private var prompted = false

    override val state = MutableStateFlow<State>(State.Loading)
    private val _events = MutableSharedFlow<Event>()
    override val events = _events.asSharedFlow()

    override fun onChangeTagClicked() {
        viewModelScope.launch {
            val selected = if (isShortcut) {
                emptyArray()
            } else {
                store.deviceId(appWidgetId)?.let { arrayOf(it) } ?: emptyArray()
            }
            navigation.navigate(
                RingWidgetFragmentDirections.actionWidgetRingFragmentToWidgetDevicePickerFragment(
                    isMultiSelect = false,
                    key = KEY_PICKER,
                    title = R.string.ring_widget_pick_title,
                    selectedDeviceIds = selected,
                    popUpTo = R.id.widgetRingFragment,
                    knownDeviceIds = emptyArray()
                )
            )
        }
    }

    override fun onTagChosen(deviceId: String) {
        viewModelScope.launch {
            val label = devices.getDeviceInfo(deviceId)?.label ?: deviceId
            if (isShortcut) {
                pinShortcut(deviceId, label)
                val outcome = controller.toggle(deviceId)
                _events.emit(Event.ShortcutFinished(outcome, label))
            } else {
                store.setWidget(appWidgetId, deviceId, label)
                RingWidgetProvider.renderAll(context, store)
                state.emit(State.Loaded(label))
                _events.emit(Event.CloseWidget)
            }
        }
    }

    override fun promptIfNeeded() {
        if (prompted) return
        prompted = true
        val hasDevice = !isShortcut && store.deviceId(appWidgetId) != null
        if (!hasDevice) {
            onChangeTagClicked()
        }
    }

    private fun pinShortcut(deviceId: String, label: String) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        if (!manager.isRequestPinShortcutSupported) return
        val intent = Intent(context, RingShortcutActivity::class.java).apply {
            action = RingShortcutActivity.ACTION_RING
            putExtra(RingShortcutActivity.EXTRA_DEVICE_ID, deviceId)
        }
        val shortcut = ShortcutInfo.Builder(context, "ring_$deviceId")
            .setShortLabel(label)
            .setLongLabel(context.getString(R.string.ring_shortcut_pinned, label))
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        runCatching { manager.requestPinShortcut(shortcut, null) }
    }

    init {
        if (!isShortcut) {
            val label = store.label(appWidgetId)
            state.value = State.Loaded(label)
        } else {
            state.value = State.Loaded(null)
        }
    }

    companion object {
        const val KEY_PICKER = "ring_tag_picker"
    }

}
