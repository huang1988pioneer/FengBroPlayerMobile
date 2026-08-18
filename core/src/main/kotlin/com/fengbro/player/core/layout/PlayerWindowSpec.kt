package com.fengbro.player.core.layout

enum class WidthClass {
    Compact,
    Medium,
    Expanded,
}

enum class HeightClass {
    Compact,
    Medium,
    Expanded,
}

data class PlayerWindowSpec(
    val widthDp: Int,
    val heightDp: Int,
    val widthClass: WidthClass,
    val heightClass: HeightClass,
    val useSidePlaylist: Boolean,
    val landingTwoPane: Boolean,
    val landingMaxWidthDp: Int,
    val sidePaneWidthDp: Int,
    val coverSizeDp: Int,
    val playButtonDp: Int,
    val overlayHorizontalPadDp: Int,
    val sheetMaxHeightDp: Int,
) {
    val isCompactHeight: Boolean get() = heightClass == HeightClass.Compact
    val isCompactWidth: Boolean get() = widthClass == WidthClass.Compact
    val keepPlaylistOpenOnSelect: Boolean get() = useSidePlaylist
    val useDialogSettings: Boolean get() = useSidePlaylist

    companion object {
        fun from(widthDp: Int, heightDp: Int): PlayerWindowSpec {
            val width = widthDp.coerceAtLeast(1)
            val height = heightDp.coerceAtLeast(1)
            val widthClass = when {
                width < 600 -> WidthClass.Compact
                width < 840 -> WidthClass.Medium
                else -> WidthClass.Expanded
            }
            val heightClass = when {
                height < 480 -> HeightClass.Compact
                height < 900 -> HeightClass.Medium
                else -> HeightClass.Expanded
            }
            val useSidePlaylist =
                widthClass != WidthClass.Compact && heightClass != HeightClass.Compact
            val landingTwoPane = useSidePlaylist ||
                (heightClass == HeightClass.Compact && widthClass != WidthClass.Compact)
            val landingMaxWidthDp = if (widthClass == WidthClass.Compact) width else 520
            val sidePaneWidthDp = if (widthClass == WidthClass.Expanded) 360 else 320
            val coverSizeDp = when {
                heightClass == HeightClass.Compact -> 136
                widthClass == WidthClass.Compact -> 220
                else -> 280
            }
            val playButtonDp = if (heightClass == HeightClass.Compact) 64 else 76
            val overlayHorizontalPadDp = if (widthClass == WidthClass.Compact) 12 else 20
            val sheetMaxHeightDp = if (heightClass == HeightClass.Compact) {
                (height * 0.72f).toInt().coerceIn(200, (height - 24).coerceAtLeast(200))
            } else {
                (height * 0.62f).toInt().coerceIn(320, 560)
            }
            return PlayerWindowSpec(
                widthDp = width,
                heightDp = height,
                widthClass = widthClass,
                heightClass = heightClass,
                useSidePlaylist = useSidePlaylist,
                landingTwoPane = landingTwoPane,
                landingMaxWidthDp = landingMaxWidthDp,
                sidePaneWidthDp = sidePaneWidthDp,
                coverSizeDp = coverSizeDp,
                playButtonDp = playButtonDp,
                overlayHorizontalPadDp = overlayHorizontalPadDp,
                sheetMaxHeightDp = sheetMaxHeightDp,
            )
        }
    }
}
