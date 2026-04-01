package com.mckimquyen.reader.ui.page.setting.color.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.pref.LocalReadingFonts
import com.mckimquyen.reader.infrastructure.pref.LocalReadingSubheadAlign
import com.mckimquyen.reader.infrastructure.pref.LocalReadingSubheadBold
import com.mckimquyen.reader.infrastructure.pref.LocalReadingSubheadUpperCase
import com.mckimquyen.reader.infrastructure.pref.LocalReadingTitleAlign
import com.mckimquyen.reader.infrastructure.pref.LocalReadingTitleBold
import com.mckimquyen.reader.infrastructure.pref.LocalReadingTitleUpperCase
import com.mckimquyen.reader.ui.component.reader.bodyStyle
import com.mckimquyen.reader.ui.component.reader.h3Style
import com.mckimquyen.reader.ui.component.reader.textHorizontalPadding

@Composable
fun TitleAndTextPreview() {
    val context = LocalContext.current
    val titleBold = LocalReadingTitleBold.current
    val titleUpperCase = LocalReadingTitleUpperCase.current
    val subheadUpperCase = LocalReadingSubheadUpperCase.current
    val titleAlign = LocalReadingTitleAlign.current


    val titleUpperCaseString by remember {
        derivedStateOf {
            context.getString(R.string.title).uppercase()
        }
    }

    val subheadUpperCaseString by remember {
        derivedStateOf {
            context.getString(R.string.subhead).uppercase()
        }
    }
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = textHorizontalPadding().dp),
            text = if (titleUpperCase.value) titleUpperCaseString else stringResource(id = R.string.title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = LocalReadingFonts.current.asFontFamily(context),
                fontWeight = if (titleBold.value) FontWeight.SemiBold else FontWeight.Normal,
            ),
            textAlign = titleAlign.toTextAlign(),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (subheadUpperCase.value) subheadUpperCaseString else stringResource(id = R.string.subhead),
            style = h3Style().copy(textAlign = bodyStyle().textAlign),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = textHorizontalPadding().dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(id = R.string.preview_article_desc),
            style = bodyStyle(),
            modifier = Modifier.padding(horizontal = textHorizontalPadding().dp)
        )
    }
}
