package me.ikun0014.audiotagger.flac

import kotlin.test.Test
import kotlin.test.assertEquals

class MetaDataBlockVorbisCommentTest {

    @Test
    fun `build and parse round-trip`() {
        val vendor = "test vendor 1.0"
        val comments = listOf("TITLE=Test Song", "ARTIST=Test Artist", "ALBUM=Test Album")
        val original = MetaDataBlockVorbisComment(vendor, comments)

        val payload = original.buildPayload()
        val parsed = MetaDataBlockVorbisComment.parse(payload)

        assertEquals(vendor, parsed.vendor)
        assertEquals(comments, parsed.comments)
    }

    @Test
    fun `empty comments`() {
        val vc = MetaDataBlockVorbisComment("vendor", emptyList())
        val payload = vc.buildPayload()
        val parsed = MetaDataBlockVorbisComment.parse(payload)

        assertEquals("vendor", parsed.vendor)
        assertEquals(emptyList(), parsed.comments)
    }

    @Test
    fun `unicode content`() {
        val comments = listOf("TITLE=测试歌曲", "ARTIST=テストアーティスト", "LYRICS=가사 테스트")
        val vc = MetaDataBlockVorbisComment("vendor", comments)
        val payload = vc.buildPayload()
        val parsed = MetaDataBlockVorbisComment.parse(payload)

        assertEquals(comments, parsed.comments)
    }

    @Test
    fun `comments with equals sign in value`() {
        val comments = listOf("TITLE=A=B=C", "ARTIST=x=y")
        val vc = MetaDataBlockVorbisComment("vendor", comments)
        val payload = vc.buildPayload()
        val parsed = MetaDataBlockVorbisComment.parse(payload)

        assertEquals(comments, parsed.comments)
    }
}
