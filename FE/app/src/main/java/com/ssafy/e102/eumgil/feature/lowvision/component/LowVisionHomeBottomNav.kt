package com.ssafy.e102.eumgil.feature.lowvision.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.feature.lowvision.LowVisionBottomTab
import com.ssafy.e102.eumgil.feature.lowvision.LowVisionScreenDefaults
import com.ssafy.e102.eumgil.feature.lowvision.lowVisionButtonSemantics

object LowVisionBottomNavDefaults {
    const val itemCount = 4
    const val itemWeight = 1f
    const val reservesNavigationBarSafeZone = true
    val height = 80.dp
}

/**
 * 시각지원 모드 메인 홈 화면용 하단 네비.
 *
 * Figma node 371:157 — 80dp 높이, 활성 탭은 노랑(#FFCC00) 아이콘+라벨, 비활성은
 * Boulder(#777). 약관 walkthrough의 nav가 활성 탭에 노랑 배경을 깔던 것과 다르게
 * 본 셸은 텍스트·아이콘 색만 노랑으로 바뀐다.
 */
@Composable
fun LowVisionHomeBottomNav(
    selectedTab: LowVisionBottomTab,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    LowVisionBottomNav(
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier,
    )
}

@Composable
fun LowVisionBottomNav(
    selectedTab: LowVisionBottomTab,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    backgroundColor: Color = Color.Black,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        LowVisionBottomTab.HOME to LowVisionNavItem(
            iconRes = R.drawable.ic_nav_home_filled,
            labelRes = R.string.low_vision_nav_home,
        ),
        LowVisionBottomTab.BOOKMARK to LowVisionNavItem(
            iconRes = R.drawable.ic_nav_bookmark_outline,
            labelRes = R.string.low_vision_nav_bookmark,
        ),
        LowVisionBottomTab.CATEGORY to LowVisionNavItem(
            iconRes = R.drawable.ic_nav_category_grid,
            labelRes = R.string.low_vision_nav_category,
        ),
        LowVisionBottomTab.MY_PAGE to LowVisionNavItem(
            iconRes = R.drawable.ic_nav_person_outline,
            labelRes = R.string.low_vision_nav_my_page,
        ),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(LowVisionBottomNavDefaults.height),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (tab, item) ->
                val tint = if (tab == selectedTab) LowVisionScreenDefaults.brandYellow else Color(0xFF777777)
                val label = stringResource(id = item.labelRes)
                Column(
                    modifier = Modifier
                        .weight(LowVisionBottomNavDefaults.itemWeight)
                        .fillMaxHeight()
                        .lowVisionButtonSemantics(label)
                        .clickable(role = Role.Button) { onTabSelected(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

private data class LowVisionNavItem(
    val iconRes: Int,
    val labelRes: Int,
)
