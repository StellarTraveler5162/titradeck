package com.titradeck.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.titradeck.app.R

/** The same untinted artwork used by the launcher, with an in-app rounded mask. */
@Composable
internal fun AppBrandIcon(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.ic_brand_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size).clip(RoundedCornerShape(percent = 24)),
    )
}
