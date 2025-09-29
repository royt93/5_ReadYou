package com.mckimquyen.reader.domain.model.general

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.general.Filter.Companion.All
import com.mckimquyen.reader.domain.model.general.Filter.Companion.AddSources
import com.mckimquyen.reader.domain.model.general.Filter.Companion.Starred
import com.mckimquyen.reader.domain.model.general.Filter.Companion.Unread

/**
 * Indicates filter conditions.
 *
 * - [All]: all items
 * - [Unread]: unread items
 * - [Starred]: starred items
 * - [AddSources]: add sources page
 */
class Filter private constructor(
    val index: Int,
    val iconOutline: ImageVector,
    val iconFilled: ImageVector,
) {

    fun isStarred(): Boolean = this == Starred
    fun isUnread(): Boolean = this == Unread
    fun isAll(): Boolean = this == All
    fun isAddSources(): Boolean = this == AddSources

    @Stable
    @Composable
    fun toName(): String = when (this) {
        Unread -> stringResource(R.string.unread)
        Starred -> stringResource(R.string.starred)
        AddSources -> "Add Sources"
        else -> stringResource(R.string.all)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Stable
    @Composable
    fun toDesc(important: Int): String = when (this) {
        Starred -> pluralStringResource(R.plurals.starred_desc, important, important)
        Unread -> pluralStringResource(R.plurals.unread_desc, important, important)
        AddSources -> "Browse and add RSS sources"
        else -> pluralStringResource(R.plurals.all_desc, important, important)
    }

    companion object {

        val Starred = Filter(
            index = 0,
            iconOutline = Icons.Rounded.StarOutline,
            iconFilled = Icons.Rounded.Star,
        )
        val Unread = Filter(
            index = 1,
            iconOutline = Icons.Outlined.FiberManualRecord,
            iconFilled = Icons.Rounded.FiberManualRecord,
        )
        val All = Filter(
            index = 2,
            iconOutline = Icons.Rounded.Subject,
            iconFilled = Icons.Rounded.Subject,
        )
        val AddSources = Filter(
            index = 3,
            iconOutline = Icons.Rounded.Add,
            iconFilled = Icons.Rounded.Add,
        )
        val values = listOf(Starred, Unread, All, AddSources)
    }
}
