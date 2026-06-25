package com.ssafy.e102.eumgil.feature.navigation

import com.ssafy.e102.eumgil.R

internal fun NavigationGuidanceAction.iconRes(): Int =
    when (this) {
        NavigationGuidanceAction.ARRIVAL -> R.drawable.ic_navigation_rail_destination_pin
        NavigationGuidanceAction.START -> R.drawable.ic_route_start_navigation
        NavigationGuidanceAction.ALIGHT -> R.drawable.ic_route_alight
        NavigationGuidanceAction.BUS -> R.drawable.ic_place_bus
        NavigationGuidanceAction.SUBWAY -> R.drawable.ic_route_subway
        NavigationGuidanceAction.STRAIGHT -> R.drawable.ic_direction_straight
        NavigationGuidanceAction.TURN_LEFT -> R.drawable.ic_direction_turn_left
        NavigationGuidanceAction.TURN_RIGHT -> R.drawable.ic_direction_turn_right
        NavigationGuidanceAction.CROSSWALK -> R.drawable.ic_direction_crosswalk
        NavigationGuidanceAction.TACTILE_GUIDE -> R.drawable.ic_route_tactile_blocks
        NavigationGuidanceAction.ELEVATOR -> R.drawable.ic_route_elevator
        NavigationGuidanceAction.CONSTRUCTION -> R.drawable.ic_route_construction
        NavigationGuidanceAction.CURB_GAP -> R.drawable.ic_status_warning
        NavigationGuidanceAction.STAIRS -> R.drawable.ic_route_stairs
        NavigationGuidanceAction.FALLBACK -> R.drawable.ic_status_help_circle
    }
