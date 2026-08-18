package com.fengbro.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.fengbro.player.core.layout.PlayerWindowSpec

@Composable
fun rememberPlayerWindowSpec(): PlayerWindowSpec {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        PlayerWindowSpec.from(configuration.screenWidthDp, configuration.screenHeightDp)
    }
}
