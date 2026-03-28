package me.ikun0014.audiotagger.flac

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MetaDataBlockPictureTest {

    @Test
    fun `build and parse round-trip`() {
        val picData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03)
        val original = MetaDataBlockPicture(
            MetaDataBlockPicture.TYPE_FRONT_COVER,
            "image/jpeg",
            "Front Cover",
            640,
            480,
            24,
            0,
            picData
        )

        val payload = original.buildPayload()
        val parsed = MetaDataBlockPicture.parse(payload)

        assertEquals(MetaDataBlockPicture.TYPE_FRONT_COVER, parsed.pictureType)
        assertEquals("image/jpeg", parsed.mimeType)
        assertEquals("Front Cover", parsed.description)
        assertEquals(640, parsed.width)
        assertEquals(480, parsed.height)
        assertEquals(24, parsed.bitsPerPixel)
        assertEquals(0, parsed.colors)
        assertContentEquals(picData, parsed.pictureData)
    }

    @Test
    fun `png image`() {
        val picData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val original = MetaDataBlockPicture(
            MetaDataBlockPicture.TYPE_FRONT_COVER,
            "image/png",
            "",
            1920,
            1080,
            32,
            0,
            picData
        )

        val payload = original.buildPayload()
        val parsed = MetaDataBlockPicture.parse(payload)

        assertEquals("image/png", parsed.mimeType)
        assertEquals(1920, parsed.width)
        assertEquals(1080, parsed.height)
        assertEquals(32, parsed.bitsPerPixel)
        assertContentEquals(picData, parsed.pictureData)
    }

    @Test
    fun `build payload produces valid bytes`() {
        val picData = byteArrayOf(0x01, 0x02)
        val block = MetaDataBlockPicture(3, "image/jpeg", "", 100, 200, 24, 0, picData)
        val payload = block.buildPayload()

        // picture type (big-endian int) = 3
        assertEquals(0, payload[0].toInt())
        assertEquals(0, payload[1].toInt())
        assertEquals(0, payload[2].toInt())
        assertEquals(3, payload[3].toInt())

        // mime type length = 10 ("image/jpeg")
        assertEquals(0, payload[4].toInt())
        assertEquals(0, payload[5].toInt())
        assertEquals(0, payload[6].toInt())
        assertEquals(10, payload[7].toInt())
    }
}
