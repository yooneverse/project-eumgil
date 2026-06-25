package com.ssafy.e102.eumgil.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ssafy.e102.eumgil.R

sealed class TopLevelDestination(
    val route: AppRoute,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int,
    val iconSizeDp: Int,
) {
    data object Map : TopLevelDestination(
        route = TopLevelRoute.Map,
        labelRes = R.string.route_map,
        iconRes = R.drawable.ic_nav_home,
        selectedIconRes = R.drawable.ic_nav_home_selected,
        iconSizeDp = 30,
    )

    data object SavedRoute : TopLevelDestination(
        route = TopLevelRoute.SavedRoute,
        labelRes = R.string.route_saved_route,
        iconRes = R.drawable.ic_nav_bookmark_outline,
        selectedIconRes = R.drawable.ic_nav_bookmark_selected,
        iconSizeDp = 30,
    )

    data object Report : TopLevelDestination(
        route = ReportRoute.Report,
        labelRes = R.string.route_report,
        iconRes = R.drawable.ic_nav_report,
        selectedIconRes = R.drawable.ic_nav_report_selected,
        iconSizeDp = 30,
    )

    data object MyPage : TopLevelDestination(
        route = TopLevelRoute.MyPage,
        labelRes = R.string.route_my_page,
        iconRes = R.drawable.ic_nav_mypage,
        selectedIconRes = R.drawable.ic_nav_mypage_selected,
        iconSizeDp = 30,
    )

    companion object {
        val entries: List<TopLevelDestination>
            get() =
                listOf(
                    Map,
                    SavedRoute,
                    Report,
                    MyPage,
                )
    }
}
