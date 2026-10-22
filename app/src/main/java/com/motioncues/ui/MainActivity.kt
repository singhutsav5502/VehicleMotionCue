package com.motioncues.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.motioncues.MotionCues
import com.motioncues.R
import com.motioncues.databinding.ActivityMainBinding
import com.motioncues.settings.DotSize
import com.motioncues.settings.DotStyle
import com.motioncues.settings.MotionCuesSettings
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: MotionCuesSettings

    /** Set when the user asked for cues but we sent them to grant the overlay permission first. */
    private var startAfterPermission = false
    private var updatingUi = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Cues work either way; the permission only makes the "Turn off" notification visible.
            startCues()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = MotionCuesSettings(this)

        startAfterPermission = savedInstanceState?.getBoolean(STATE_PENDING_START) ?: false
        handleIntent(intent)

        bindControls()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MotionCues.running.collect { renderRunning(it) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val granted = MotionCues.canDrawOverlays(this)
        binding.permissionCard.visibility = if (granted) View.GONE else View.VISIBLE
        if (startAfterPermission && granted) {
            startAfterPermission = false
            requestStart()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PENDING_START, startAfterPermission)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_REQUESTED, false) == true) {
            intent.removeExtra(EXTRA_START_REQUESTED)
            if (MotionCues.canDrawOverlays(this)) requestStart() else startAfterPermission = true
        }
    }

    private fun bindControls() = with(binding) {
        enableSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUi) return@setOnCheckedChangeListener
            if (checked) requestStart() else MotionCues.stop(this@MainActivity)
        }

        grantPermissionButton.setOnClickListener { openOverlaySettings() }

        styleGroup.check(
            when (settings.style) {
                DotStyle.REGULAR -> R.id.style_regular
                DotStyle.DYNAMIC -> R.id.style_dynamic
            },
        )
        styleGroup.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            settings.style = if (id == R.id.style_dynamic) DotStyle.DYNAMIC else DotStyle.REGULAR
        }

        sizeGroup.check(
            when (settings.size) {
                DotSize.SMALL -> R.id.size_small
                DotSize.MEDIUM -> R.id.size_medium
                DotSize.LARGE -> R.id.size_large
            },
        )
        sizeGroup.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            settings.size = when (id) {
                R.id.size_small -> DotSize.SMALL
                R.id.size_large -> DotSize.LARGE
                else -> DotSize.MEDIUM
            }
        }

        intensitySlider.valueFrom = MotionCuesSettings.MIN_INTENSITY
        intensitySlider.valueTo = MotionCuesSettings.MAX_INTENSITY
        intensitySlider.value = snapToStep(settings.intensity)
        intensitySlider.setLabelFormatter { "%.2f×".format(it) }
        intensitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) settings.intensity = value
        }
    }

    private fun renderRunning(running: Boolean) {
        updatingUi = true
        binding.enableSwitch.isChecked = running
        updatingUi = false
        binding.statusText.setText(if (running) R.string.status_on else R.string.status_off)
    }

    private fun requestStart() {
        if (!MotionCues.canDrawOverlays(this)) {
            startAfterPermission = true
            openOverlaySettings()
            renderRunning(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startCues()
    }

    private fun startCues() {
        runCatching { MotionCues.start(this) }.onFailure {
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show()
            renderRunning(false)
        }
    }

    private fun openOverlaySettings() {
        Toast.makeText(this, R.string.overlay_permission_hint, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        )
    }

    /** Material Slider rejects values that aren't on its step grid. */
    private fun snapToStep(v: Float): Float {
        val step = INTENSITY_STEP
        val from = MotionCuesSettings.MIN_INTENSITY
        return (from + Math.round((v - from) / step) * step)
            .coerceIn(MotionCuesSettings.MIN_INTENSITY, MotionCuesSettings.MAX_INTENSITY)
    }

    companion object {
        const val EXTRA_START_REQUESTED = "com.motioncues.extra.START_REQUESTED"
        private const val STATE_PENDING_START = "pending_start"
        private const val INTENSITY_STEP = 0.25f
    }
}
