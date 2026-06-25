package com.ssafy.e102.eumgil.core.designsystem.component.place

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing

// ── 다크 테마 장소 목록 공통 색상 ──────────────────────────────────────────────
val PlaceListBg       = Color(0xFF1C1C1E)
val PlaceListAmber    = Color(0xFFF2B705)
val PlaceListSurface  = Color(0xFF2C2C2E)
val PlaceListDivider  = Color(0xFF3A3A3C)
val PlaceListOnAmber  = Color(0xFF1C1C1E)
val PlaceListSubText  = Color(0xFFAEAEB2)
val PlaceListTabInactive = Color(0xFF636366)

internal object PlaceListCardDefaults {
    @DrawableRes
    val bookmarkIconRes: Int = R.drawable.ic_nav_bookmark_selected

    @DrawableRes
    val routeIconRes: Int = R.drawable.ic_nav_route

    val actionOrder: List<PlaceListCardAction> = listOf(
        PlaceListCardAction.Navigate,
        PlaceListCardAction.Bookmark,
    )
}

internal enum class PlaceListCardAction {
    Navigate,
    Bookmark,
}

/**
 * 다크 테마 장소 목록 카드.
 *
 * 검색 결과와 저장 목록 화면이 공용으로 사용합니다.
 *
 * @param index          리스트 순번 (1부터 시작, 번호 뱃지에 표시)
 * @param name           장소 이름
 * @param address        주소 또는 지역명 (null 이면 표시 안 함)
 * @param bookmarkLabel  북마크 버튼 레이블 (e.g. "북마크" / "삭제")
 * @param onBookmarkClick 북마크 버튼 클릭
 * @param onNavigateClick 길찾기 버튼 클릭
 */
@Composable
fun PlaceListCard(
    index: Int,
    name: String,
    address: String?,
    bookmarkLabel: String,
    onBookmarkClick: () -> Unit,
    onNavigateClick: () -> Unit,
    modifier: Modifier = Modifier,
    onContentClick: (() -> Unit)? = null,
    contentClickDescription: String? = null,
    bookmarkContentDescription: String? = null,
    navigateContentDescription: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 3.dp,
                color = PlaceListAmber,
                shape = RoundedCornerShape(18.dp),
            )
            .clip(RoundedCornerShape(18.dp))
            .background(PlaceListBg)
            .then(
                if (onContentClick != null) {
                    Modifier
                        .clickable(
                            role = Role.Button,
                            onClick = onContentClick,
                        )
                        .semantics {
                            contentClickDescription?.let { description ->
                                contentDescription = description
                            }
                        }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 26.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // ── 헤더: 번호 뱃지 + 이름 + 주소 ──────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 번호 뱃지
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = PlaceListAmber,
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = PlaceListOnAmber,
                    lineHeight = 48.sp,
                    letterSpacing = 0.sp,
                )
            }

            // 이름 + 주소
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = name,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 52.sp,
                    letterSpacing = 0.sp,
                    maxLines = 3,
                )
                if (!address.isNullOrBlank()) {
                    Text(
                        text = address,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 32.sp,
                        letterSpacing = 0.sp,
                        maxLines = 2,
                    )
                }
            }
        }

        // ── 버튼 영역 ────────────────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlaceListCardDefaults.actionOrder.forEach { action ->
                when (action) {
                    PlaceListCardAction.Navigate -> PlaceActionButton(
                        label = "\uAE38\uCC3E\uAE30",
                        iconRes = PlaceListCardDefaults.routeIconRes,
                        onClick = onNavigateClick,
                        contentDescription = navigateContentDescription ?: "$name \uAE38\uCC3E\uAE30",
                    )

                    PlaceListCardAction.Bookmark -> PlaceActionButton(
                        label = bookmarkLabel,
                        iconRes = PlaceListCardDefaults.bookmarkIconRes,
                        onClick = onBookmarkClick,
                        contentDescription = bookmarkContentDescription ?: "$name $bookmarkLabel",
                    )
                }
            }
        }
    }
}

// ── 내부 버튼 컴포넌트 ────────────────────────────────────────────────────────

@Composable
private fun PlaceActionButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PlaceListAmber)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(color = PlaceListOnAmber),
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = PlaceListOnAmber,
            modifier = Modifier.size(46.dp),
        )
        Spacer(modifier = Modifier.size(28.dp))
        Text(
            text = label,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
            color = PlaceListOnAmber,
            letterSpacing = 0.sp,
        )
    }
}

/**
 * 검색/북마크 화면 공통 하단 탭 바.
 *
 * @param activeTab 활성 탭 ("home" | "bookmark" | "category" | "mypage")
 */
@Composable
fun PlaceListTabBar(
    activeTab: String,
    onHomeClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        Triple("home",     R.drawable.ic_nav_home,            "홈"),
        Triple("bookmark", R.drawable.ic_nav_bookmark_outline, "북마크"),
        Triple("category", R.drawable.ic_nav_category_grid,   "카테고리"),
        Triple("mypage",   R.drawable.ic_nav_mypage,          "마이페이지"),
    )

    Column(modifier = modifier.background(PlaceListBg)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PlaceListDivider),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.small),
        ) {
            tabs.forEach { (key, iconRes, label) ->
                val selected = key == activeTab
                val tint = if (selected) PlaceListOnAmber else PlaceListTabInactive
                val bg   = if (selected) PlaceListAmber   else Color.Transparent

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(EumRadius.small))
                        .background(bg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(),
                            onClick = when (key) {
                                "home"     -> onHomeClick
                                "bookmark" -> onBookmarkClick
                                else       -> ({})
                            },
                        )
                        .padding(vertical = EumSpacing.xSmall),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = tint,
                    )
                }
            }
        }
    }
}
