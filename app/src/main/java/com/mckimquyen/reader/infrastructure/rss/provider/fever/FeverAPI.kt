package com.mckimquyen.reader.infrastructure.rss.provider.fever

import com.mckimquyen.reader.infrastructure.rss.provider.ProviderAPI
import com.mckimquyen.reader.infrastructure.rss.provider.credentialApiClient
import com.mckimquyen.reader.ui.ext.encodeBase64
import com.mckimquyen.reader.ui.ext.md5
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.executeAsync
import java.util.concurrent.ConcurrentHashMap

class FeverAPI private constructor(
    private val serverUrl: String,
    private val apiKey: String,
    private val httpUsername: String? = null,
    private val httpPassword: String? = null,
    trustSelfSigned: Boolean = false,
) : ProviderAPI(credentialApiClient(trustSelfSigned)) {

    private suspend inline fun <reified T> postRequest(query: String?): T {
        val response = client.newCall(
            Request.Builder()
                .apply {
                    if (httpUsername != null) {
                        addHeader("Authorization", "Basic ${"$httpUsername:$httpPassword".encodeBase64()}")
                    }
                }
                .url("$serverUrl?api=&${query ?: ""}")
                .post(FormBody.Builder().add("api_key", apiKey).build())
                .build())
            .executeAsync()

        when (response.code) {
            401 -> throw Exception("Unauthorized")
            !in 200..299 -> throw Exception("Forbidden")
        }

        return toDTO(response.body.string())
    }

    private fun checkAuth(authMap: Map<String, Any>): Int = checkAuth(authMap["auth"] as Int?)

    private fun checkAuth(auth: Int?): Int = auth?.takeIf { it > 0 } ?: throw Exception("Unauthorized")

    @Throws
    suspend fun validCredentials(): Int = checkAuth(postRequest<FeverDTO.Common>(null).auth)

    suspend fun getApiVersion(): Long =
        postRequest<Map<String, Any>>(null)["api_version"] as Long?
            ?: throw Exception("Unable to get version")

    suspend fun getGroups(): FeverDTO.Groups =
        postRequest<FeverDTO.Groups>("groups").apply { checkAuth(auth) }

    suspend fun getFeeds(): FeverDTO.Feeds =
        postRequest<FeverDTO.Feeds>("feeds").apply { checkAuth(auth) }

    suspend fun getFavicons(): FeverDTO.Favicons =
        postRequest<FeverDTO.Favicons>("favicons").apply { checkAuth(auth) }

    suspend fun getItems(): FeverDTO.Items =
        postRequest<FeverDTO.Items>("items").apply { checkAuth(auth) }

    suspend fun getItemsSince(id: String): FeverDTO.Items =
        postRequest<FeverDTO.Items>("items&since_id=$id").apply { checkAuth(auth) }

    suspend fun getItemsMax(id: String): FeverDTO.Items =
        postRequest<FeverDTO.Items>("items&max_id=$id").apply { checkAuth(auth) }

    suspend fun getItemsWith(ids: List<String>): FeverDTO.Items =
        if (ids.size > 50) throw Exception("Too many ids")
        else postRequest<FeverDTO.Items>("items&with_ids=${ids.joinToString(",")}").apply { checkAuth(auth) }

    suspend fun getLinks(): FeverDTO.Links =
        postRequest<FeverDTO.Links>("links").apply { checkAuth(auth) }

    suspend fun getLinksWith(offset: Long, days: Long, page: Long): FeverDTO.Links =
        postRequest<FeverDTO.Links>("links&offset=$offset&range=$days&page=$page").apply { checkAuth(auth) }

    suspend fun getUnreadItems(): FeverDTO.ItemsByUnread =
        postRequest<FeverDTO.ItemsByUnread>("unread_item_ids").apply { checkAuth(auth) }

    suspend fun getSavedItems(): FeverDTO.ItemsByStarred =
        postRequest<FeverDTO.ItemsByStarred>("saved_item_ids").apply { checkAuth(auth) }

    suspend fun markItem(status: FeverDTO.StatusEnum, id: String): FeverDTO.Common =
        postRequest<FeverDTO.Common>("&mark=item&as=${status.value}&id=$id").apply { checkAuth(auth) }

    private suspend fun markFeedOrGroup(
        act: String,
        status: FeverDTO.StatusEnum,
        id: Long,
        before: Long,
    ): FeverDTO.Common = postRequest<FeverDTO.Common>("&mark=$act&as=${status.value}&id=$id&before=$before")
        .apply { checkAuth(auth) }

    suspend fun markGroup(status: FeverDTO.StatusEnum, id: Long, before: Long) =
        markFeedOrGroup("group", status, id, before)

    suspend fun markFeed(status: FeverDTO.StatusEnum, id: Long, before: Long) =
        markFeedOrGroup("feed", status, id, before)

    companion object {

        private val instances: ConcurrentHashMap<String, FeverAPI> = ConcurrentHashMap()

        fun getInstance(
            serverUrl: String,
            username: String,
            password: String,
            httpUsername: String? = null,
            httpPassword: String? = null,
            trustSelfSigned: Boolean = false,
        ): FeverAPI = "$username:$password".md5().run {
            // The trust flag is part of the key: switching the exception on or off must not keep serving the old client.
            instances.getOrPut("$serverUrl$this$httpUsername$httpPassword$trustSelfSigned") {
                FeverAPI(serverUrl, this, httpUsername, httpPassword, trustSelfSigned)
            }
        }
    }
}
