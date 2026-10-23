package com.kieronquinn.app.utag.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.model.LaunchRouting
import com.kieronquinn.app.utag.repositories.AuthRepository
import com.kieronquinn.app.utag.repositories.SettingsRepository
import com.kieronquinn.app.utag.workers.UpdateWorker
import org.koin.android.ext.android.inject

class MainActivity : BaseActivity() {

    private val authRepository by inject<AuthRepository>()
    private val settingsRepository by inject<SettingsRepository>()

    companion object {
        const val EXTRA_SKIP_SPLASH = "skip_splash"
        const val EXTRA_FORCE_SETTINGS = "force_settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        UpdateWorker.queueCheckWorker(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openMapIfPreferred()
    }

    /**
     *  Called once setup has resolved the account, and again when the launcher
     *  brings this activity forward. Finishing here leaves the map as the task
     *  the icon opened.
     */
    fun openMapIfPreferred() {
        val openMap = LaunchRouting.shouldOpenMap(
            screen = settingsRepository.launchScreen.getSync(),
            forceSettings = intent.getBooleanExtra(EXTRA_FORCE_SETTINGS, false),
            isSignedIn = authRepository.isLoggedIn().value == true
        )
        if (!openMap) return
        startActivity(Intent(this, TagActivity::class.java))
        finish()
    }

}