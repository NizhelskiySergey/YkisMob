package com.ykis.mob.core.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ykis.mob.R

@Composable
fun LogoImage() {
    Row(
        modifier = Modifier,
//            .padding(paddingValues = PaddingValues())
//            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically

    ) {
        val imageModifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .border(BorderStroke(0.dp, Color.Transparent))
            .background(Color.Transparent)
            .align(Alignment.CenterVertically)
        Image(
            painter = painterResource(id = R.drawable.ykis),
            contentDescription = stringResource(id = R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = imageModifier,
            alignment = Alignment.Center
        )
        Text(
            style = MaterialTheme.typography.titleSmall,
//            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            text = stringResource(R.string.full_name)
        )
    }

}
