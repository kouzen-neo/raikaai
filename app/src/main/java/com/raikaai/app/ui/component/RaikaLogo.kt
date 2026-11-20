package com.raikaai.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.raikaai.app.R

/**
 * The app icon, drawn from the adaptive icon layers (the adaptive mipmap
 * itself can't be loaded by painterResource). The foreground is scaled so
 * only the 72/108 safe zone shows, like a launcher mask.
 */
@Composable
fun RaikaLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(size * 0.3f),
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Raika AI",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().scale(1.5f),
        )
    }
}
