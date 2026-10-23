package com.kieronquinn.app.utag.ui.screens.widget.container

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.repositories.AuthRepository
import com.kieronquinn.app.utag.repositories.EncryptedSettingsRepository
import com.kieronquinn.app.utag.ui.activities.BaseActivity
import com.kieronquinn.app.utag.ui.activities.HistoryWidgetConfigurationActivity
import com.kieronquinn.app.utag.ui.activities.LocationWidgetConfigurationActivity
import com.kieronquinn.app.utag.ui.activities.RingShortcutActivity
import com.kieronquinn.app.utag.ui.activities.RingWidgetConfigurationActivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class WidgetContainerViewModel: ViewModel() {

    abstract val state: StateFlow<State>

    abstract fun onBiometricSuccess()

    sealed class State(val destination: Int?) {
        data object Loading: State(R.id.action_global_decisionFragment3)
        data object NotSetup: State(null)
        data object Location: State(R.id.action_global_widgetLocationFragment)
        data object History: State(R.id.action_global_widgetHistoryFragment)
        data object Ring: State(R.id.action_global_widgetRingFragment)
        data object BiometricPrompt: State(null)
    }

    enum class WidgetType(val clazz: Class<out BaseActivity>) {
        LOCATION(LocationWidgetConfigurationActivity::class.java),
        HISTORY(HistoryWidgetConfigurationActivity::class.java),
        RING(RingWidgetConfigurationActivity::class.java),
        RING_SHORTCUT(RingShortcutActivity::class.java)
    }

}

class WidgetContainerViewModelImpl(
    private val authRepository: AuthRepository,
    encryptedSettingsRepository: EncryptedSettingsRepository,
    widgetType: WidgetType
): WidgetContainerViewModel() {

    private val isSignedIn = authRepository.isLoggedIn()

    private val ensureKeyAndIv = flow {
        emit(encryptedSettingsRepository.ensureKeyAndIV())
    }

    override val state = combine(
        isSignedIn,
        encryptedSettingsRepository.biometricPromptEnabled.asFlow(),
        authRepository.biometricPassed,
        ensureKeyAndIv
    ) { isSignedIn, biometricEnabled, biometricPassed, _ ->
        when {
            biometricEnabled && !biometricPassed -> State.BiometricPrompt
            isSignedIn == false -> State.NotSetup
            isSignedIn == true && widgetType == WidgetType.LOCATION -> State.Location
            isSignedIn == true && widgetType == WidgetType.HISTORY -> State.History
            isSignedIn == true &&
                (widgetType == WidgetType.RING || widgetType == WidgetType.RING_SHORTCUT) -> {
                State.Ring
            }
            else -> State.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override fun onBiometricSuccess() {
        viewModelScope.launch {
            authRepository.onBiometricSuccess()
        }
    }

}