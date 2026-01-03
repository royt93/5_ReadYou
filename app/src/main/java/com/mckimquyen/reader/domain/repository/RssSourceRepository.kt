package com.mckimquyen.reader.domain.repository

import android.content.Context
import com.google.gson.Gson
import com.mckimquyen.reader.domain.model.RssSource
import com.mckimquyen.reader.domain.model.RssSourcesData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssSourceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    fun getRssSources(): List<RssSource> {
        return try {
            val json = context.assets.open("rss_sources.json").bufferedReader().use { it.readText() }
            val sourcesData = gson.fromJson(json, RssSourcesData::class.java)

            // Get current locale and return appropriate sources
            val currentLanguage = Locale.getDefault().language
            when (currentLanguage) {
                "vi" -> sourcesData.vi ?: sourcesData.en ?: emptyList()
                else -> sourcesData.en ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getRssSourcesByCountry(countryCode: String): List<RssSource> {
        return try {
            val json = context.assets.open("rss_sources.json").bufferedReader().use { it.readText() }
            val sourcesData = gson.fromJson(json, RssSourcesData::class.java)

            when (countryCode) {
                "en" -> sourcesData.en ?: emptyList()
                "vi" -> sourcesData.vi ?: emptyList()
                "ja" -> sourcesData.ja ?: emptyList()
                "ko" -> sourcesData.ko ?: emptyList()
                "zh" -> sourcesData.zh ?: emptyList()
                "id" -> sourcesData.id ?: emptyList()
                "th" -> sourcesData.th ?: emptyList()
                "fr" -> sourcesData.fr ?: emptyList()
                "de" -> sourcesData.de ?: emptyList()
                "es" -> sourcesData.es ?: emptyList()
                "pt" -> sourcesData.pt ?: emptyList()
                "ar" -> sourcesData.ar ?: emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}