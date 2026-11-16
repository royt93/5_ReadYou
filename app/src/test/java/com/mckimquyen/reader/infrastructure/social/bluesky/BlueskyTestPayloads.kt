package com.mckimquyen.reader.infrastructure.social.bluesky

/** Shapes measured on the real public API (2026-11-16), with invented text. Only keys the mapper reads are present. */
object BlueskyTestPayloads {
    const val DID = "did:plc:z72i7hdynmk6r22z27h6tvur"
    const val OTHER_DID = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"
    const val HANDLE = "alice.bsky.social"
    const val NOW = 1_800_000_000_000L

    fun post(
        rkey: String,
        text: String,
        createdAt: String = "2026-11-10T08:00:00.000Z",
        did: String = DID,
        embed: String? = null,
        recordExtra: String = "",
    ): String = """{"post":{"uri":"at://$did/app.bsky.feed.post/$rkey","cid":"c$rkey","author":{"did":"$did","handle":"$HANDLE"},""" +
        """"record":{"${'$'}type":"app.bsky.feed.post","text":${quote(text)},"createdAt":"$createdAt"$recordExtra}""" +
        (embed?.let { ""","embed":$it""" } ?: "") + "}}"

    fun repost(rkey: String, did: String = OTHER_DID): String =
        """{"post":{"uri":"at://$did/app.bsky.feed.post/$rkey","cid":"c$rkey","author":{"did":"$did","handle":"bob.bsky.social"},""" +
            """"record":{"text":"someone else","createdAt":"2026-11-10T08:00:00.000Z"}},""" +
            """"reason":{"${'$'}type":"app.bsky.feed.defs#reasonRepost"}}"""

    const val EMBED_IMAGES = """{"${'$'}type":"app.bsky.embed.images#view","images":[{"alt":"a","fullsize":"https://cdn.bsky.app/x","thumb":"https://cdn.bsky.app/y"}]}"""
    const val EMBED_VIDEO = """{"${'$'}type":"app.bsky.embed.video#view","playlist":"https://video.bsky.app/p","thumbnail":"https://video.bsky.app/t"}"""
    const val EMBED_QUOTE = """{"${'$'}type":"app.bsky.embed.record#view","record":{"uri":"at://x/y/z"}}"""
    fun embedExternal(uri: String, title: String = "A page") =
        """{"${'$'}type":"app.bsky.embed.external#view","external":{"uri":${quote(uri)},"title":${quote(title)},"description":"d"}}"""

    fun array(vararg items: String): org.json.JSONArray = org.json.JSONArray("[" + items.joinToString(",") + "]")

    private fun quote(text: String): String = org.json.JSONObject.quote(text)
}
