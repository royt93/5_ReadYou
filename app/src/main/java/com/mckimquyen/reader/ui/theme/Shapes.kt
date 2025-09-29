package com.mckimquyen.reader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.dp

// Material Design 3 Shape System - Enhanced
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Custom shape variations for modern UI
@Stable
val Shape6 = RoundedCornerShape(6.dp)

@Stable
val Shape10 = RoundedCornerShape(10.dp)

@Stable
val Shape14 = RoundedCornerShape(14.dp)

@Stable
val Shape18 = RoundedCornerShape(18.dp)

@Stable
val Shape20 = RoundedCornerShape(20.dp)

@Stable
val Shape24 = RoundedCornerShape(24.dp)

@Stable
val Shape32 = RoundedCornerShape(32.dp)

// Asymmetric shapes for modern design elements
@Stable
val ShapeTop8 = RoundedCornerShape(8.dp, 8.dp, 0.dp, 0.dp)

@Stable
val ShapeTop12 = RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp)

@Stable
val ShapeTop16 = RoundedCornerShape(16.dp, 16.dp, 0.dp, 0.dp)

@Stable
val ShapeTop24 = RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)

@Stable
val ShapeTop32 = RoundedCornerShape(32.dp, 32.dp, 0.dp, 0.dp)

@Stable
val ShapeBottom8 = RoundedCornerShape(0.dp, 0.dp, 8.dp, 8.dp)

@Stable
val ShapeBottom12 = RoundedCornerShape(0.dp, 0.dp, 12.dp, 12.dp)

@Stable
val ShapeBottom16 = RoundedCornerShape(0.dp, 0.dp, 16.dp, 16.dp)

@Stable
val ShapeBottom24 = RoundedCornerShape(0.dp, 0.dp, 24.dp, 24.dp)

@Stable
val ShapeBottom32 = RoundedCornerShape(0.dp, 0.dp, 32.dp, 32.dp)

// Start and end shapes for directional UI elements
@Stable
val ShapeStart16 = RoundedCornerShape(16.dp, 0.dp, 0.dp, 16.dp)

@Stable
val ShapeEnd16 = RoundedCornerShape(0.dp, 16.dp, 16.dp, 0.dp)

// Custom shapes for cards and containers
@Stable
val CardShape = RoundedCornerShape(16.dp)

@Stable
val ButtonShape = RoundedCornerShape(24.dp)

@Stable
val InputShape = RoundedCornerShape(12.dp)

@Stable
val ChipShape = RoundedCornerShape(16.dp)

@Stable
val BottomSheetShape = RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)

@Stable
val DialogShape = RoundedCornerShape(20.dp)
