package com.kieronquinn.app.utag.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.repositories.RingController
import com.kieronquinn.app.utag.repositories.RingOutcome
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Launcher shortcut. With [EXTRA_DEVICE_ID] it rings or stops that tag and
 * closes. Without it, the widget configuration nav graph asks which tag.
 */
class RingShortcutActivity : BaseActivity() {

    private val ringController by inject<RingController>()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getStringExtra(EXTRA_DEVICE_ID) != null) {
            setTheme(R.style.Theme_uTag_Transparent)
        }
        super.onCreate(savedInstanceState)
        if (!handleRingIntent(intent)) {
            setContentView(R.layout.activity_widget_configuration)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRingIntent(intent)
    }

    private fun handleRingIntent(intent: Intent): Boolean {
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return false
        lifecycleScope.launch {
            val outcome = ringController.toggle(deviceId)
            val message = when (outcome) {
                RingOutcome.RINGING -> R.string.ring_widget_started
                RingOutcome.STOPPED -> R.string.ring_widget_stopped
                RingOutcome.STOP_FAILED -> R.string.ring_widget_stop_failed
                RingOutcome.FAILED -> R.string.ring_widget_failed
            }
            Toast.makeText(this@RingShortcutActivity, message, Toast.LENGTH_LONG).show()
            finish()
        }
        return true
    }

    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        const val ACTION_RING = "com.kieronquinn.app.utag.RING"
    }

}
