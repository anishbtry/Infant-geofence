package com.infantgeofence.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infantgeofence.R
import com.infantgeofence.data.model.FenceStatus
import com.infantgeofence.databinding.FragmentDashboardBinding
import com.infantgeofence.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setColorSchemeColors(0xFF57BFC9.toInt())

        // When the sheet is fully expanded, let downward drags collapse it instead of
        // SwipeRefresh stealing the gesture (NestedScrollView reports "not scrollable up" at top).
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            val sheet = BottomSheetBehavior.from(binding.dashboardBottomSheet)
            val canScrollContent = binding.sheetNestedScroll.canScrollVertically(-1)
            canScrollContent || sheet.state == BottomSheetBehavior.STATE_EXPANDED
        }

        binding.swipeRefresh.doOnLayout { swipe ->
            val sheet = BottomSheetBehavior.from(binding.dashboardBottomSheet)
            sheet.isDraggable = true
            val parentH = swipe.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            val minPeek = (280 * resources.displayMetrics.density).toInt()
            val maxPeek = (560 * resources.displayMetrics.density).toInt()
            sheet.peekHeight = (parentH * 0.42f).toInt().coerceIn(minPeek, maxPeek)
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.fetchLocation()
            viewModel.fetchAlerts()
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.location.observe(viewLifecycleOwner) { loc ->
            if (loc == null || !loc.success) {
                binding.tvChildName.text = "No Data"
                binding.tvOnlineStatus.text = "● Offline"
                binding.tvOnlineStatus.setTextColor(0xFFFF3B30.toInt())
                binding.tvSatellites.text = "--"
                binding.tvSpeed.text = "--"
                binding.tvLatitude.text = "--"
                binding.tvLongitude.text = "--"
                binding.tvLastUpdate.text = "No data yet"
                return@observe
            }

            binding.tvChildName.text = loc.childName
            binding.tvOnlineStatus.text = if (loc.online) "● Online" else "● Offline"
            binding.tvOnlineStatus.setTextColor(
                if (loc.online) 0xFF4CD964.toInt() else 0xFFFF3B30.toInt()
            )
            binding.tvSatellites.text = "${loc.satellites}"
            binding.tvSpeed.text = "%.1f km/h".format(loc.speedKmph)
            binding.tvLatitude.text = "%.6f".format(loc.latitude)
            binding.tvLongitude.text = "%.6f".format(loc.longitude)

            runCatching {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(loc.timestamp)
                val local = SimpleDateFormat("MMM d, yyyy  hh:mm:ss a", Locale.getDefault())
                binding.tvLastUpdate.text = date?.let { local.format(it) } ?: loc.timestamp
            }.onFailure { binding.tvLastUpdate.text = loc.timestamp }
        }

        viewModel.fenceStatus.observe(viewLifecycleOwner) { status ->
            val (iconRes, title, subtitle, chipText, chipColor, cardColor) = when (status) {
                FenceStatus.INSIDE -> Sextuple(
                    R.drawable.ic_check_circle,
                    "Inside Safe Zone ✅",
                    "Child is within the geofenced area",
                    "Inside", 0xFF34C759.toInt(), R.color.fence_inside_bg
                )
                FenceStatus.OUTSIDE -> Sextuple(
                    R.drawable.ic_warning,
                    "OUTSIDE Safe Zone ⚠️",
                    "Child has left the safe boundary!",
                    "Outside", 0xFFFF3B30.toInt(), R.color.fence_outside_bg
                )
                FenceStatus.UNKNOWN -> Sextuple(
                    R.drawable.ic_fence,
                    "Geofence Not Set",
                    "Go to Geofence tab to draw a safe zone",
                    "Unknown", 0xFF999999.toInt(), R.color.fence_unknown_bg
                )
            }

            binding.ivFenceIcon.setImageResource(iconRes)
            binding.cardFenceStatus.setCardBackgroundColor(
                requireContext().getColor(cardColor)
            )
            binding.tvFenceTitle.text = title
            binding.tvFenceSubtitle.text = subtitle

            // Update the inline status chip in the info row
            binding.tvFenceStatusChip.text = chipText
            binding.tvFenceStatusChip.setTextColor(chipColor)

            binding.chipAlert.visibility =
                if (status == FenceStatus.OUTSIDE) View.VISIBLE else View.GONE
        }

        viewModel.polygon.observe(viewLifecycleOwner) { poly ->
            if (poly.isEmpty()) {
                binding.tvFenceTitle.text = "No Geofence Set"
                binding.tvFenceSubtitle.text = "Go to Geofence tab to draw a safe zone"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class Sextuple<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
}