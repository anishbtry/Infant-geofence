package com.infantgeofence.ui.geofence

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infantgeofence.InfantGeoFenceApp
import com.infantgeofence.R
import com.infantgeofence.databinding.FragmentGeofenceBinding
import com.infantgeofence.ui.MainViewModel
import com.infantgeofence.util.GeofencePrefs
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

class GeofenceFragment : Fragment() {

    private var _binding: FragmentGeofenceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private val draftPoints = mutableListOf<GeoPoint>()
    private val pointMarkers = mutableListOf<Marker>()
    private var draftPolyline: Polyline? = null
    private var draftPolygon: Polygon? = null
    private var savedPolygon: Polygon? = null
    private var childMarker: Marker? = null
    private var drawMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGeofenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupButtons()
        observeData()
        updateUI()
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(26.1433, 91.7362))
        }

        val mapReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (drawMode) addPoint(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint) = false
        }
        binding.mapView.overlays.add(MapEventsOverlay(mapReceiver))
    }

    private fun setupButtons() {
        binding.btnDraw.setOnClickListener {
            drawMode = true
            updateModeUI()
        }
        binding.btnPan.setOnClickListener {
            drawMode = false
            updateModeUI()
        }

        binding.btnUndo.setOnClickListener {
            if (draftPoints.isEmpty()) return@setOnClickListener
            draftPoints.removeLastOrNull()
            val last = pointMarkers.removeLastOrNull()
            last?.let { binding.mapView.overlays.remove(it) }
            redrawDraft()
            updateUI()
        }

        binding.btnClear.setOnClickListener {
            clearDraft()
            updateUI()
        }

        binding.btnMore.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menuInflater.inflate(R.menu.menu_geofence_overflow, popup.menu)
            val hasSaved = (viewModel.polygon.value?.size ?: 0) >= 3
            popup.menu.findItem(R.id.action_load_zone)?.isEnabled = hasSaved
            popup.menu.findItem(R.id.action_delete_zone)?.isEnabled = hasSaved
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_load_zone -> {
                        onLoadSavedZone()
                        true
                    }
                    R.id.action_delete_zone -> {
                        confirmDeleteSavedZone()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        binding.btnSave.setOnClickListener {
            if (draftPoints.size < 3) {
                Toast.makeText(requireContext(), "Draw at least 3 points", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_save_zone, null)
            val etName = dialogView.findViewById<EditText>(R.id.et_dialog_zone_name)

            MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    val name = etName.text.toString().trim().ifEmpty { "Safe Zone" }
                    saveFence(name)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        updateModeUI()
    }

    private fun onLoadSavedZone() {
        val poly = viewModel.polygon.value ?: emptyList()
        if (poly.isEmpty()) {
            Toast.makeText(requireContext(), "No saved geofence to load", Toast.LENGTH_SHORT).show()
            return
        }
        clearDraft()
        poly.forEach { addPoint(it) }
        updateUI()
        Toast.makeText(requireContext(), "Existing fence loaded for editing", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteSavedZone() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Geofence?")
            .setMessage("This will remove the safe zone for this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val serverDeleted = viewModel.deleteGeofenceFromServer()
                    clearDraft()
                    viewModel.clearPolygon()
                    GeofencePrefs.clearPolygon(requireContext(), InfantGeoFenceApp.DEVICE_ID)
                    showSavedFence(emptyList())
                    updateUI()
                    val msg = if (serverDeleted) {
                        "Geofence deleted"
                    } else {
                        "Geofence removed locally, server delete failed"
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun observeData() {
        viewModel.polygon.observe(viewLifecycleOwner) { poly ->
            if (draftPoints.isEmpty()) showSavedFence(poly)
        }

        viewModel.location.observe(viewLifecycleOwner) { loc ->
            if (loc == null || !loc.success) return@observe
            val pt = GeoPoint(loc.latitude, loc.longitude)
            if (childMarker == null) {
                childMarker = Marker(binding.mapView).apply {
                    title = loc.childName
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = resources.getDrawable(R.drawable.ic_child_marker, null)
                    binding.mapView.overlays.add(this)
                }
                binding.mapView.controller.animateTo(pt)
            }
            childMarker?.position = pt
            binding.mapView.invalidate()
        }
    }

    private fun addPoint(p: GeoPoint) {
        draftPoints.add(p)
        val marker = Marker(binding.mapView).apply {
            position = p
            title = "Point ${draftPoints.size}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = resources.getDrawable(
                if (draftPoints.size == 1) R.drawable.ic_point_start else R.drawable.ic_point,
                null
            )
        }
        binding.mapView.overlays.add(marker)
        pointMarkers.add(marker)
        redrawDraft()
        updateUI()
    }

    private fun redrawDraft() {
        binding.mapView.overlays.remove(draftPolyline)
        binding.mapView.overlays.remove(draftPolygon)
        draftPolyline = null
        draftPolygon = null

        if (draftPoints.size >= 3) {
            draftPolygon = Polygon(binding.mapView).apply {
                points = draftPoints + draftPoints.first()
                fillColor = Color.argb(50, 255, 165, 0)
                strokeColor = Color.argb(220, 255, 140, 0)
                strokeWidth = 7f
            }
            binding.mapView.overlays.add(1, draftPolygon)
        } else if (draftPoints.size >= 2) {
            draftPolyline = Polyline(binding.mapView).apply {
                setPoints(draftPoints)
                outlinePaint.color = Color.argb(220, 255, 140, 0)
                outlinePaint.strokeWidth = 7f
            }
            binding.mapView.overlays.add(1, draftPolyline)
        }

        binding.mapView.invalidate()
    }

    private fun showSavedFence(poly: List<GeoPoint>) {
        binding.mapView.overlays.remove(savedPolygon)
        if (poly.size < 3) {
            binding.mapView.invalidate()
            return
        }
        savedPolygon = Polygon(binding.mapView).apply {
            points = poly + poly.first()
            fillColor = Color.argb(35, 87, 191, 201)
            strokeColor = Color.argb(190, 87, 191, 201)
            strokeWidth = 5f
        }
        binding.mapView.overlays.add(0, savedPolygon)
        binding.mapView.invalidate()
    }

    private fun clearDraft() {
        draftPoints.clear()
        pointMarkers.forEach { binding.mapView.overlays.remove(it) }
        pointMarkers.clear()
        binding.mapView.overlays.remove(draftPolyline)
        binding.mapView.overlays.remove(draftPolygon)
        draftPolyline = null
        draftPolygon = null
        binding.mapView.invalidate()
    }

    private fun saveFence(name: String) {
        binding.btnSave.isEnabled = false
        binding.btnSave.text = getString(R.string.geofence_saving)
        val points = draftPoints.toList()

        lifecycleScope.launch {
            GeofencePrefs.savePolygon(requireContext(), InfantGeoFenceApp.DEVICE_ID, points, name)
            viewModel.setPolygon(points, name)

            val ok = viewModel.saveGeofenceToServer(points, name)

            binding.btnSave.text = getString(R.string.geofence_save_zone)
            binding.btnSave.isEnabled = true

            val msg = if (ok)
                "✅ Geofence saved! (${points.size} points)"
            else
                "⚠️ Saved locally — server sync failed"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

            if (ok) {
                showSavedFence(points)
                clearDraft()
                updateUI()
            } else {
                updateUI()
            }
        }
    }

    private fun updateUI() {
        val hasDraft = draftPoints.size >= 3

        binding.btnUndo.isEnabled = draftPoints.isNotEmpty()
        binding.btnClear.isEnabled = draftPoints.isNotEmpty()
        binding.btnSave.isEnabled = hasDraft

        binding.tvPointsHint.text = when {
            draftPoints.isEmpty() ->
                if (drawMode) "Tap the map to place points" else "Switch to Draw mode to add points"
            draftPoints.size < 3 -> "${draftPoints.size} point(s) — need ${3 - draftPoints.size} more"
            else -> "${draftPoints.size} points — ready to save!"
        }
        binding.tvPointsHint.setTextColor(
            requireContext().getColor(if (hasDraft) R.color.green else R.color.orange)
        )

        binding.tvHeaderHint.setText(
            if (drawMode) R.string.geofence_header_draw else R.string.geofence_header_pan
        )
    }

    private fun updateModeUI() {
        binding.btnDraw.isSelected = drawMode
        binding.btnPan.isSelected = !drawMode
        binding.mapView.setScrollableAreaLimitLatitude(85.05113, -85.05113, 0)
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
