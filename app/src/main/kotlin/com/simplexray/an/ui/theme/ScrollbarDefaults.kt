package com.simplexray.an.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import my.nanihadesuka.compose.ScrollbarLayoutSide
import my.nanihadesuka.compose.ScrollbarSelectionActionable
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings

object ScrollbarDefaults {
    @Composable
    fun defaultScrollbarSettings(): ScrollbarSettings {
        return ScrollbarSettings(
            enabled = true,
            side = ScrollbarLayoutSide.End,
            alwaysShowScrollbar = false,
            thumbThickness = 6.dp,
            scrollbarPadding = 0.dp,
            thumbMinLength = 0.1f,
            thumbMaxLength = 1.0f,
            thumbUnselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            thumbSelectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            thumbShape = CircleShape,
            selectionMode = ScrollbarSelectionMode.Thumb,
            selectionActionable = ScrollbarSelectionActionable.Always,
            hideDelayMillis = 400,
            hideDisplacement = 14.dp,
            hideEasingAnimation = FastOutSlowInEasing,
            durationAnimationMillis = 500,
        )
    }
}
