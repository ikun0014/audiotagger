package me.ikun0014.audiotagger

import kotlin.test.Test

class MainTest {
    @Test
    fun `test write meta`() {
        val meta = MusicMeta()
        meta.title = "将进酒"
        meta.artist = "凤凰传奇"
        meta.album = "经典咏流传 第11期"

        MusicTagger.setMeta("src/test/resources/test.mp3", meta)
    }

    @Test
    fun `test read meta`() {
        val meta = MusicTagger.getMeta("src/test/resources/test.mp3")
        if (meta != null) {
            println(meta.title)
            println(meta.artist)
            println(meta.album)
            println(meta.lyrics)
        }
    }
}