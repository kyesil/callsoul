package com.bettercallsoul.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bettercallsoul.R
import com.bettercallsoul.calllog.BirthdayNear

/** Red on the birthday, yellow the day before; the same in light and dark. */
private val BirthdayToday = Color(0xFFE53935)
private val BirthdayTomorrow = Color(0xFFF2A900)

@Composable
fun BirthdayCake(near: BirthdayNear, modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.Cake,
        stringResource(if (near == BirthdayNear.Today) R.string.birthday_today else R.string.birthday_tomorrow),
        tint = if (near == BirthdayNear.Today) BirthdayToday else BirthdayTomorrow,
        modifier = modifier,
    )
}
