package com.mckimquyen.reader.domain.sv

import org.json.JSONObject

/** Invented Bluesky API answers in the shape measured on the real API (2026-11-16); only the keys the app reads. */
object BlueskyDeviceFixtures {
    const val DID = "did:plc:z72i7hdynmk6r22z27h6tvur"
    const val OTHER_DID = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"
    const val HANDLE = "front02h-probe.bsky.social"

    /** Different from what the feed was stored with, so a sync that renamed it or changed its icon would show. */
    const val HOSTILE_NAME = "Renamed By The Server"
    const val HOSTILE_AVATAR = "https://cdn.bsky.app/img/avatar/hostile@jpeg"

    val PROFILE_BODY: String = """{"did":"$DID","handle":"$HANDLE","displayName":"$HOSTILE_NAME","avatar":"$HOSTILE_AVATAR"}"""

    fun post(rkey: String, text: String): String =
        """{"post":{"uri":"at://$DID/app.bsky.feed.post/$rkey","cid":"c$rkey","author":{"did":"$DID","handle":"$HANDLE"},""" +
            """"record":{"${'$'}type":"app.bsky.feed.post","text":${JSONObject.quote(text)},"createdAt":"2026-11-10T08:00:00.000Z"}}}"""

    fun repost(rkey: String): String =
        """{"post":{"uri":"at://$OTHER_DID/app.bsky.feed.post/$rkey","cid":"c$rkey","author":{"did":"$OTHER_DID","handle":"bob.bsky.social"},""" +
            """"record":{"text":"someone else","createdAt":"2026-11-10T08:00:00.000Z"}},"reason":{"${'$'}type":"app.bsky.feed.defs#reasonRepost"}}"""

    fun feedBody(vararg items: String): String = """{"feed":[${items.joinToString(",")}]}"""
}
