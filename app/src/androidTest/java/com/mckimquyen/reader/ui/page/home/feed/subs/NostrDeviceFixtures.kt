package com.mckimquyen.reader.ui.page.home.feed.subs

/** One signed note and one signed profile of the same author, copied from the JVM fixtures (src/test is not visible here). */
object NostrDeviceFixtures {
    const val AUTHOR_HEX = "dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659"
    const val NOTE_JSON = """{"id":"c0279077872984dffec95d0f6f833820e4d5e7d843290610bc2a8982fcd9737e","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000300,"kind":1,"tags":[],"content":"Hello nostr, first note","sig":"4993b5aead88e3b16b5c15a8616d296ef70124ea8e8c0374c80e9f220dbd82facfe4ff1559285d79e2ce89b8b9bf5425b5255314bc0887d62c43d5bedb798623"}"""
    const val PROFILE_JSON = """{"id":"0d9a8e7564b00aa09efed3aa0b9ece8fb177d3c191bd403678124073419d31af","pubkey":"dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659","created_at":1700000900,"kind":0,"tags":[],"content":"{\"name\":\"alice\",\"display_name\":\"Alice Liddell\"}","sig":"25c665edde362e4cefe1b659e07bad3624e791a7c94ea463d8d160bc4cb70eec5d03cce94db2ad731f38144b85bb47281980883ad4a9aacda0ca56f10df635c3"}"""
    const val PROFILE_NAME = "Alice Liddell"
}
