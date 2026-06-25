package com.ssafy.e102.eumgil.app.navigation

import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.feature.search.SearchSelectionMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface AppRoute {
    val route: String
}

sealed interface AuthRoute : AppRoute {
    data object Login : AuthRoute {
        override val route: String = "auth/login"
    }

    data object ProfileSetup : AuthRoute {
        override val route: String = "auth/profile_setup"
    }
}

sealed interface OnboardingRoute : AppRoute {
    data object UserTypePrimary : OnboardingRoute {
        override val route: String = "onboarding/user_type_primary"
    }

    data object ProfileUserTypePrimary : OnboardingRoute {
        override val route: String = "onboarding/profile_user_type_primary"
    }

    data object LowVisionFollowUp : OnboardingRoute {
        override val route: String = "onboarding/low_vision_followup"
    }

    data object MobilityTypeSecondary : OnboardingRoute {
        override val route: String = "onboarding/mobility_type_secondary"
    }

    data object ProfileMobilityTypeSecondary : OnboardingRoute {
        override val route: String = "onboarding/profile_mobility_type_secondary"
    }

    data object Terms : OnboardingRoute {
        override val route: String = "onboarding/terms"
    }

    /**
     * High-contrast 5-step terms walkthrough screen
     * (Figma file MREqSzkmwhRcXnFS3lzW17, nodes 328:486 / 328:528 / 328:570 /
     * 328:612 / 328:652 — 약관 동의 / 민감정보 / 위치정보 / 14세 이상 / 처리방침).
     *
     * `step` 인자는 deep-link로 특정 단계에서 다시 시작하는 분기를 허용한다
     * ("각 화면이 분기 시작점"). 기본값은 첫 단계(agree)이며 step 라우트 값은
     * [com.ssafy.e102.eumgil.feature.terms.TermsGuideStep.routeValue]에서 정의한다.
     */
    data object TermsGuide : OnboardingRoute {
        const val ARG_STEP: String = "step"
        const val DEFAULT_STEP: String = "agree"

        override val route: String = "onboarding/terms_guide/{$ARG_STEP}"

        fun createRoute(stepRouteValue: String = DEFAULT_STEP): String =
            "onboarding/terms_guide/$stepRouteValue"
    }

    data object Permission : OnboardingRoute {
        const val ARG_NEXT_ROUTE: String = "next_route"

        override val route: String = "onboarding/permission/{$ARG_NEXT_ROUTE}"

        fun createRoute(nextRoute: String): String =
            "onboarding/permission/${nextRoute.navArgEncode()}"
    }
}

sealed interface TutorialRoute : AppRoute {
    data object Onboarding : TutorialRoute {
        override val route: String = "tutorial/onboarding"
    }

    data object Guide : TutorialRoute {
        override val route: String = "tutorial/guide"
    }
}

sealed interface TopLevelRoute : AppRoute {
    data object Map : TopLevelRoute {
        override val route: String = "map"
    }

    data object SavedRoute : TopLevelRoute {
        override val route: String = "saved_route"
    }

    data object MyPage : TopLevelRoute {
        override val route: String = "my_page"
    }
}

sealed interface MyPageChildRoute : AppRoute {
    data object TextSize : MyPageChildRoute {
        override val route: String = "my_page/text_size"
    }
}

/**
 * 시각지원(저시력/시각장애) 모드 풀스크린 셸 화면들의 라우트.
 *
 * 약관 walkthrough([OnboardingRoute.TermsGuide]) 이후 진입하는 음성 안내 메인 흐름.
 * 출처: Figma file MREqSzkmwhRcXnFS3lzW17, nodes 371:105 / 371:300.
 */
sealed interface LowVisionRoute : AppRoute {
    /** 시각지원 모드 메인 홈 (Figma node 371:105). */
    data object Home : LowVisionRoute {
        override val route: String = "low_vision/home"
    }

    /** 시각지원 모드 음성 입력 진행 화면 (Figma node 371:300). */
    data object VoiceInput : LowVisionRoute {
        override val route: String = "low_vision/voice_input"
    }

    data object Bookmark : LowVisionRoute {
        override val route: String = "low_vision/bookmark"
    }

    data object Search : LowVisionRoute {
        override val route: String = "low_vision/search"
    }

    data object CategorySearch : LowVisionRoute {
        override val route: String = "low_vision/category_search"
    }

    data object CategoryResult : LowVisionRoute {
        const val ARG_CATEGORY: String = "category"

        override val route: String = "low_vision/category_result/{$ARG_CATEGORY}"

        fun createRoute(category: String): String = "low_vision/category_result/${category.navArgEncode()}"
    }

    data object RouteBriefing : LowVisionRoute {
        override val route: String = "low_vision/route_briefing"
    }

    data object Guidance : LowVisionRoute {
        override val route: String = "low_vision/guidance"
    }

    data object NavigationComplete : LowVisionRoute {
        override val route: String = "low_vision/navigation_complete"
    }

    data object MyPage : LowVisionRoute {
        override val route: String = "low_vision/my_page"
    }

    data object AppInfo : LowVisionRoute {
        override val route: String = "low_vision/app_info"
    }

    data object TextSize : LowVisionRoute {
        override val route: String = "low_vision/text_size"
    }

    /** 음성 인식 결과로 진입하는 검색 화면. [query]는 URL 인코딩된 STT 결과. */
    data object VoiceSearch : LowVisionRoute {
        const val ARG_QUERY: String = "query"

        override val route: String = "low_vision/voice_search/{$ARG_QUERY}"

        fun createRoute(query: String): String =
            "low_vision/voice_search/${query.ifBlank { " " }.navArgEncode()}"
    }
}

sealed interface SearchRoute : AppRoute {
    data object Entry : SearchRoute {
        const val ARG_EDITING_TARGET: String = "editingTarget"
        const val ARG_SELECTION_MODE: String = "selectionMode"

        override val route: String =
            "search?$ARG_EDITING_TARGET={$ARG_EDITING_TARGET}&$ARG_SELECTION_MODE={$ARG_SELECTION_MODE}"

        fun createRoute(
            editingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
            selectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
        ): String =
            buildSearchRoute(
                baseRoute = "search",
                editingTarget = editingTarget,
                selectionMode = selectionMode,
            )
    }

    /**
     * Legacy search voice route kept for compatibility.
     *
     * New search microphone entry points open the global voice assistant instead of navigating here.
     */
    data object VoiceInput : SearchRoute {
        const val ARG_EDITING_TARGET: String = Entry.ARG_EDITING_TARGET
        const val ARG_SELECTION_MODE: String = Entry.ARG_SELECTION_MODE

        override val route: String =
            "search/voice?$ARG_EDITING_TARGET={$ARG_EDITING_TARGET}&$ARG_SELECTION_MODE={$ARG_SELECTION_MODE}"

        fun createRoute(
            editingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
            selectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
        ): String =
            buildSearchRoute(
                baseRoute = "search/voice",
                editingTarget = editingTarget,
                selectionMode = selectionMode,
            )
    }

    data object Results : SearchRoute {
        const val ARG_QUERY: String = "query"
        const val ARG_EDITING_TARGET: String = Entry.ARG_EDITING_TARGET
        const val ARG_SELECTION_MODE: String = Entry.ARG_SELECTION_MODE

        override val route: String =
            "search/results/{$ARG_QUERY}?$ARG_EDITING_TARGET={$ARG_EDITING_TARGET}" +
                "&$ARG_SELECTION_MODE={$ARG_SELECTION_MODE}"

        fun createRoute(
            query: String,
            editingTarget: RouteEditingTarget = RouteEditingTarget.DESTINATION,
            selectionMode: SearchSelectionMode = SearchSelectionMode.PREVIEW_ON_MAP,
        ): String =
            buildSearchRoute(
                baseRoute = "search/results/${query.navArgEncode()}",
                editingTarget = editingTarget,
                selectionMode = selectionMode,
            )
    }
}

sealed interface RouteSettingRoute : AppRoute {
    data object Setting : RouteSettingRoute {
        const val ARG_AUTO_START_NAVIGATION: String = "autoStartNavigation"
        const val ARG_INITIAL_ROUTE_OPTION: String = "initialRouteOption"
        const val ARG_LOCATION_PERMISSION_PRECHECKED: String = "locationPermissionPrechecked"

        override val route: String =
            "$ROUTE_SETTING_BASE_ROUTE?$ARG_AUTO_START_NAVIGATION={$ARG_AUTO_START_NAVIGATION}" +
                "&$ARG_INITIAL_ROUTE_OPTION={$ARG_INITIAL_ROUTE_OPTION}" +
                "&$ARG_LOCATION_PERMISSION_PRECHECKED={$ARG_LOCATION_PERMISSION_PRECHECKED}"

        fun createRoute(
            autoStartNavigation: Boolean = false,
            initialRouteOption: RouteOption? = null,
            locationPermissionPrechecked: Boolean = false,
        ): String {
            val queryParameters =
                buildList {
                    if (autoStartNavigation) {
                        add("$ARG_AUTO_START_NAVIGATION=true")
                    }
                    initialRouteOption?.let { routeOption ->
                        add("$ARG_INITIAL_ROUTE_OPTION=${routeOption.name.navArgEncode()}")
                    }
                    if (locationPermissionPrechecked) {
                        add("$ARG_LOCATION_PERMISSION_PRECHECKED=true")
                    }
                }

            return if (queryParameters.isEmpty()) {
                ROUTE_SETTING_BASE_ROUTE
            } else {
                "$ROUTE_SETTING_BASE_ROUTE?${queryParameters.joinToString(separator = "&")}"
            }
        }
    }

    data object PermissionGate : RouteSettingRoute {
        const val ARG_AUTO_START_NAVIGATION: String = Setting.ARG_AUTO_START_NAVIGATION
        const val ARG_INITIAL_ROUTE_OPTION: String = Setting.ARG_INITIAL_ROUTE_OPTION

        override val route: String =
            "$ROUTE_SETTING_BASE_ROUTE/permission?$ARG_AUTO_START_NAVIGATION={$ARG_AUTO_START_NAVIGATION}" +
                "&$ARG_INITIAL_ROUTE_OPTION={$ARG_INITIAL_ROUTE_OPTION}"

        fun createRoute(
            autoStartNavigation: Boolean = false,
            initialRouteOption: RouteOption? = null,
        ): String {
            val queryParameters =
                buildList {
                    if (autoStartNavigation) {
                        add("$ARG_AUTO_START_NAVIGATION=true")
                    }
                    initialRouteOption?.let { routeOption ->
                        add("$ARG_INITIAL_ROUTE_OPTION=${routeOption.name.navArgEncode()}")
                    }
                }

            val baseRoute = "$ROUTE_SETTING_BASE_ROUTE/permission"
            return if (queryParameters.isEmpty()) {
                baseRoute
            } else {
                "$baseRoute?${queryParameters.joinToString(separator = "&")}"
            }
        }
    }

    data object Detail : RouteSettingRoute {
        const val ARG_ROUTE_OPTION: String = "routeOption"
        const val ARG_FROM_NAVIGATION: String = "fromNavigation"

        override val route: String =
            "$ROUTE_SETTING_BASE_ROUTE/detail/{$ARG_ROUTE_OPTION}?$ARG_FROM_NAVIGATION={$ARG_FROM_NAVIGATION}"

        fun createRoute(
            routeOption: RouteOption,
            fromNavigation: Boolean = false,
        ): String =
            buildString {
                append("$ROUTE_SETTING_BASE_ROUTE/detail/${routeOption.name.navArgEncode()}")
                if (fromNavigation) {
                    append("?$ARG_FROM_NAVIGATION=true")
                }
            }
    }
}

sealed interface ReportRoute : AppRoute {
    data object Report : ReportRoute {
        override val route: String = "report"
    }

    data object Guidance : ReportRoute {
        override val route: String = "report/navigation_guidance"
    }

    data object History : ReportRoute {
        const val ARG_HISTORY_ID: String = "historyId"
        private const val BASE_ROUTE: String = "report/history"

        override val route: String = "$BASE_ROUTE?$ARG_HISTORY_ID={$ARG_HISTORY_ID}"

        fun createRoute(historyId: String? = null): String =
            if (historyId.isNullOrBlank()) {
                BASE_ROUTE
            } else {
                "$BASE_ROUTE?$ARG_HISTORY_ID=${historyId.navArgEncode()}"
            }
    }
}

sealed interface NavigationRoute : AppRoute {
    data object Guidance : NavigationRoute {
        override val route: String = "navigation_guidance"
    }
}

private fun String.navArgEncode(): String =
    URLEncoder
        .encode(this, StandardCharsets.UTF_8.toString())
        .replace("+", "%20")

private fun buildSearchRoute(
    baseRoute: String,
    editingTarget: RouteEditingTarget,
    selectionMode: SearchSelectionMode,
): String {
    val queryParameters =
        buildList {
            if (editingTarget != RouteEditingTarget.DESTINATION) {
                add("${SearchRoute.Entry.ARG_EDITING_TARGET}=${editingTarget.name.navArgEncode()}")
            }
            if (selectionMode != SearchSelectionMode.PREVIEW_ON_MAP) {
                add("${SearchRoute.Entry.ARG_SELECTION_MODE}=${selectionMode.name.navArgEncode()}")
            }
        }

    return if (queryParameters.isEmpty()) {
        baseRoute
    } else {
        "$baseRoute?${queryParameters.joinToString(separator = "&")}"
    }
}

private const val ROUTE_SETTING_BASE_ROUTE: String = "route_setting"

sealed interface ArrivalRoute : AppRoute {
    data object Entry : ArrivalRoute {
        override val route: String = "arrival"
    }
}
