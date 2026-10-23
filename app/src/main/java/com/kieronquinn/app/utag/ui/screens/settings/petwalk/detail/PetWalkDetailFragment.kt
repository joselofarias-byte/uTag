package com.kieronquinn.app.utag.ui.screens.settings.petwalk.detail

import android.os.Bundle
import android.content.Intent
import android.app.Activity
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kieronquinn.app.utag.model.petwalk.PetWalkGpx
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.components.navigation.SettingsNavigation
import com.kieronquinn.app.utag.databinding.FragmentPetWalkDetailBinding
import com.kieronquinn.app.utag.model.database.petwalk.PetWalkEventEntity
import com.kieronquinn.app.utag.model.petwalk.GeoPoint
import com.kieronquinn.app.utag.model.petwalk.PetWalkEventType
import com.kieronquinn.app.utag.model.petwalk.PetWalkGeometry
import com.kieronquinn.app.utag.model.petwalk.TagSampleStatus
import com.kieronquinn.app.utag.repositories.PetWalkRepository
import com.kieronquinn.app.utag.repositories.SettingsRepository
import com.kieronquinn.app.utag.ui.base.BackAvailable
import com.kieronquinn.app.utag.ui.base.BoundFragment
import com.kieronquinn.app.utag.utils.extensions.getMap
import com.kieronquinn.app.utag.utils.extensions.whenResumed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.text.DateFormat
import java.util.Date

abstract class PetWalkDetailViewModel: ViewModel() {

    abstract val state: StateFlow<State>

    abstract fun onDelete()

    sealed class State {
        data object Loading: State()
        data object Missing: State()
        data class Loaded(
            val snapshot: PetWalkRepository.PetWalkSnapshot,
            val mapType: Int
        ): State()
    }

}

class PetWalkDetailViewModelImpl(
    private val walkId: Long,
    private val navigation: SettingsNavigation,
    petWalkRepository: PetWalkRepository,
    settingsRepository: SettingsRepository
): PetWalkDetailViewModel() {

    private val repository = petWalkRepository

    override val state = combine(
        petWalkRepository.observeWalk(walkId),
        settingsRepository.mapStyle.asFlow()
    ) { snapshot, style ->
        if (snapshot == null) {
            State.Missing
        } else {
            State.Loaded(snapshot, style.style)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override fun onDelete() {
        viewModelScope.launch {
            repository.deleteWalk(walkId)
            navigation.navigateBack()
        }
    }

}

class PetWalkDetailFragment: BoundFragment<FragmentPetWalkDetailBinding>(
    FragmentPetWalkDetailBinding::inflate
), BackAvailable {

    private val args by navArgs<PetWalkDetailFragmentArgs>()
    private val viewModel by viewModel<PetWalkDetailViewModel> {
        parametersOf(args.walkId)
    }
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    private var movedCamera = false
    private var exportSnapshot: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bottom = resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
        binding.root.setPadding(0, 0, 0, bottom)
        binding.petWalkDetailDelete.setOnClickListener { confirmDelete() }
        binding.petWalkDetailExport.setOnClickListener {
            val state = viewModel.state.value as? PetWalkDetailViewModel.State.Loaded ?: return@setOnClickListener
            exportSnapshot = PetWalkGpx.export(state.snapshot.walk.deviceLabel, state.snapshot.samples.map { it.toDraft() })
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_TITLE, "pet-walk-${state.snapshot.walk.startedAt}.gpx")
            }, 7301)
        }
        whenResumed {
            viewModel.state.collect { render(it) }
        }
    }

    @Deprecated("Uses the Activity API supported by this project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7301) return
        val snapshot = exportSnapshot
        exportSnapshot = null
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val context = requireContext().applicationContext
        if (snapshot == null) {
            Toast.makeText(context, R.string.pet_walk_export_retry, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                        .bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
                }.isSuccess
            }
            Toast.makeText(context, if (success) R.string.pet_walk_export_success else R.string.pet_walk_export_failure,
                Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.pet_walk_detail_delete)
            setMessage(R.string.pet_walk_detail_delete_confirm)
            setPositiveButton(R.string.pet_walk_detail_delete) { _, _ ->
                viewModel.onDelete()
            }
            setNegativeButton(android.R.string.cancel, null)
        }.show()
    }

    private suspend fun render(state: PetWalkDetailViewModel.State) {
        when (state) {
            is PetWalkDetailViewModel.State.Loading -> {
                binding.petWalkDetailSummary.setText(R.string.pet_walk_detail_loading)
            }
            is PetWalkDetailViewModel.State.Missing -> {
                binding.petWalkDetailSummary.setText(R.string.pet_walk_detail_missing)
            }
            is PetWalkDetailViewModel.State.Loaded -> {
                binding.petWalkDetailSummary.text = describe(state.snapshot)
                val mapFragment = childFragmentManager
                    .findFragmentById(R.id.pet_walk_detail_map) as SupportMapFragment
                val map = mapFragment.getMap().first()
                map.mapType = state.mapType
                map.uiSettings.isMapToolbarEnabled = false
                draw(map, state.snapshot)
            }
        }
    }

    private fun describe(snapshot: PetWalkRepository.PetWalkSnapshot): String {
        val summary = snapshot.summary
        val lines = mutableListOf<String>()
        lines += getString(R.string.pet_walk_detail_tag, snapshot.walk.deviceLabel)
        lines += getString(
            R.string.pet_walk_detail_started,
            dateFormat.format(Date(snapshot.walk.startedAt))
        )
        lines += if (snapshot.walk.endedAt == null) {
            getString(R.string.pet_walk_in_progress)
        } else {
            getString(
                R.string.pet_walk_detail_ended,
                dateFormat.format(Date(snapshot.walk.endedAt)),
                PetWalkGeometry.formatDuration(summary.durationMillis)
            )
        }
        lines += getString(
            R.string.pet_walk_detail_distances,
            PetWalkGeometry.formatDistance(summary.phoneDistanceMetres),
            PetWalkGeometry.formatDistance(summary.tagDistanceMetres)
        )
        lines += getString(
            R.string.pet_walk_detail_samples,
            summary.sampleCount,
            summary.locatedTagSamples
        )
        lines += getString(R.string.pet_walk_detail_limits)
        if (snapshot.events.isEmpty()) {
            lines += getString(R.string.pet_walk_detail_no_events)
        } else {
            snapshot.events.forEach { event ->
                lines += describeEvent(event)
            }
        }
        val last = snapshot.samples.lastOrNull()
        if (last != null) {
            lines += getString(
                R.string.pet_walk_detail_last_tag,
                tagStatusLabel(last.toDraft().tagStatus),
                dateFormat.format(Date(last.recordedAt))
            )
        }
        return lines.joinToString(separator = "\n\n")
    }

    private fun describeEvent(event: PetWalkEventEntity): String {
        val title = when (event.eventType()) {
            PetWalkEventType.TOILET -> getString(R.string.pet_walk_event_toilet)
            PetWalkEventType.INTERACTION -> getString(R.string.pet_walk_event_interaction)
            PetWalkEventType.NOTE -> event.note ?: getString(R.string.pet_walk_event_note)
        }
        val where = when {
            event.phoneLatitude != null && event.phoneLongitude != null -> {
                getString(
                    R.string.pet_walk_detail_event_at,
                    event.phoneLatitude,
                    event.phoneLongitude
                )
            }
            event.tagLatitude != null && event.tagLongitude != null -> {
                getString(
                    R.string.pet_walk_detail_event_at,
                    event.tagLatitude,
                    event.tagLongitude
                )
            }
            else -> getString(R.string.pet_walk_detail_event_no_position)
        }
        return getString(
            R.string.pet_walk_detail_event,
            dateFormat.format(Date(event.recordedAt)),
            title,
            where
        )
    }

    private fun tagStatusLabel(status: TagSampleStatus): String {
        val res = when (status) {
            TagSampleStatus.LOCATED -> R.string.pet_walk_status_located
            TagSampleStatus.NO_LOCATION -> R.string.pet_walk_status_no_location
            TagSampleStatus.PIN_REQUIRED -> R.string.pet_walk_status_pin
            TagSampleStatus.NOT_ALLOWED -> R.string.pet_walk_status_not_allowed
            TagSampleStatus.ERROR -> R.string.pet_walk_status_error
        }
        return getString(res)
    }

    private fun draw(map: GoogleMap, snapshot: PetWalkRepository.PetWalkSnapshot) {
        map.clear()
        val phoneSegments = PetWalkGeometry.segments(snapshot.samples.map { it.toDraft().phone })
        val tagSegments = PetWalkGeometry.segments(snapshot.samples.map { it.toDraft().tag })
        phoneSegments.forEach { addLine(map, it, requireContext().getColor(R.color.pet_walk_phone_line)) }
        tagSegments.forEach { addLine(map, it, requireContext().getColor(R.color.pet_walk_tag_line)) }
        val phone = phoneSegments.flatten()
        val tag = tagSegments.flatten()
        snapshot.events.forEach { event ->
            val point = eventPoint(event) ?: return@forEach
            map.addMarker(
                MarkerOptions()
                    .position(point.toLatLng())
                    .title(event.note ?: event.eventType().name)
            )
        }
        val all = phone + tag
        if (all.isEmpty() || movedCamera) return
        movedCamera = true
        val first = all.first().toLatLng()
        if (all.size == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(first, 16f))
            return
        }
        val bounds = LatLngBounds.Builder()
        all.forEach { bounds.include(it.toLatLng()) }
        try {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 48))
        } catch (e: IllegalStateException) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(first, 15f))
        }
    }

    private fun addLine(map: GoogleMap, points: List<GeoPoint>, color: Int) {
        if (points.size < 2) {
            points.firstOrNull()?.let {
                map.addMarker(MarkerOptions().position(it.toLatLng()))
            }
            return
        }
        map.addPolyline(
            PolylineOptions()
                .addAll(points.map { it.toLatLng() })
                .color(color)
                .width(8f)
        )
    }

    private fun eventPoint(event: PetWalkEventEntity): GeoPoint? {
        if (event.phoneLatitude != null && event.phoneLongitude != null) {
            return GeoPoint(event.phoneLatitude, event.phoneLongitude, null, event.recordedAt)
        }
        if (event.tagLatitude != null && event.tagLongitude != null) {
            return GeoPoint(event.tagLatitude, event.tagLongitude, null, event.recordedAt)
        }
        return null
    }

    private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

}
