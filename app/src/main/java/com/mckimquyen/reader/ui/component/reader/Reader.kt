package com.mckimquyen.reader.ui.component.reader

import android.util.Log
import androidx.compose.foundation.lazy.LazyListScope
import com.mckimquyen.reader.R

@Suppress("FunctionName")
fun LazyListScope.Reader(
    subheadUpperCase: Boolean = false,
    link: String,
    content: String,
    onLinkClick: (String) -> Unit,
) {
    Log.i("RLog", "Reader: ")
    htmlFormattedText(
        inputStream = content.byteInputStream(),
        subheadUpperCase = subheadUpperCase,
        baseUrl = link,
        imagePlaceholder = R.drawable.ic_launcher_foreground,
        onLinkClick = onLinkClick
    )
}
