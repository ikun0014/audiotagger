package me.ikun0014.audiotagger.ogg

import me.ikun0014.audiotagger.flac.MetaDataBlockPicture
import me.ikun0014.audiotagger.flac.MetaDataBlockVorbisComment
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*

@OptIn(ExperimentalEncodingApi::class)
class OggProcessorTest {

    private val CRC_TABLE = IntArray(256).also { table ->
        for (i in 0..255) {
            var r = i shl 24
            for (j in 0..7) {
                r = if ((r and 0x80000000.toInt()) != 0) (r shl 1) xor 0x04C11DB7 else r shl 1
            }
            table[i] = r
        }
    }

    private fun oggCrc(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = (crc shl 8) xor CRC_TABLE[((crc ushr 24) and 0xFF) xor (b.toInt() and 0xFF)]
        }
        return crc
    }

    private fun putIntLE(buf: ByteArray, off: Int, v: Int) {
        buf[off] = (v and 0xFF).toByte()
        buf[off + 1] = ((v ushr 8) and 0xFF).toByte()
        buf[off + 2] = ((v ushr 16) and 0xFF).toByte()
        buf[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun putLongLE(buf: ByteArray, off: Int, v: Long) {
        for (i in 0..7) buf[off + i] = ((v ushr (i * 8)) and 0xFF).toByte()
    }

    private fun buildOggPage(
        headerType: Int,
        granulePosition: Long,
        serialNumber: Int,
        pageSequenceNumber: Int,
        body: ByteArray
    ): ByteArray {
        val segments = mutableListOf<Byte>()
        var remaining = body.size
        while (remaining >= 255) {
            segments.add(255.toByte())
            remaining -= 255
        }
        segments.add(remaining.toByte())

        val segTable = segments.toByteArray()
        val headerSize = 27 + segTable.size
        val raw = ByteArray(headerSize + body.size)

        raw[0] = 'O'.code.toByte()
        raw[1] = 'g'.code.toByte()
        raw[2] = 'g'.code.toByte()
        raw[3] = 'S'.code.toByte()
        raw[4] = 0 // version
        raw[5] = headerType.toByte()
        putLongLE(raw, 6, granulePosition)
        putIntLE(raw, 14, serialNumber)
        putIntLE(raw, 18, pageSequenceNumber)
        // CRC at 22 - filled after
        raw[26] = segTable.size.toByte()
        System.arraycopy(segTable, 0, raw, 27, segTable.size)
        System.arraycopy(body, 0, raw, headerSize, body.size)
        putIntLE(raw, 22, oggCrc(raw))
        return raw
    }

    private fun buildVorbisIdHeader(): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(0x01) // packet type
        baos.write("vorbis".toByteArray(StandardCharsets.US_ASCII))
        // Minimal vorbis identification data (23 bytes to make 30 total)
        baos.write(ByteArray(23))
        return baos.toByteArray()
    }

    private fun buildVorbisCommentPacket(comments: List<String>, vendor: String = "test vendor"): ByteArray {
        val vcPayload = MetaDataBlockVorbisComment(vendor, comments).buildPayload()
        val baos = ByteArrayOutputStream()
        baos.write(0x03) // packet type
        baos.write("vorbis".toByteArray(StandardCharsets.US_ASCII))
        baos.write(vcPayload)
        baos.write(0x01) // framing bit
        return baos.toByteArray()
    }

    private fun buildOpusHeadPacket(): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write("OpusHead".toByteArray(StandardCharsets.US_ASCII))
        baos.write(ByteArray(11)) // minimal opus header data
        return baos.toByteArray()
    }

    private fun buildOpusCommentPacket(comments: List<String>, vendor: String = "libopus"): ByteArray {
        val vcPayload = MetaDataBlockVorbisComment(vendor, comments).buildPayload()
        val baos = ByteArrayOutputStream()
        baos.write("OpusTags".toByteArray(StandardCharsets.US_ASCII))
        baos.write(vcPayload)
        return baos.toByteArray()
    }

    private fun buildVorbisOgg(comments: List<String>): ByteArray {
        val page0Body = buildVorbisIdHeader()
        val page0 = buildOggPage(0x02, 0L, 1, 0, page0Body)
        val page1Body = buildVorbisCommentPacket(comments)
        val page1 = buildOggPage(0x00, 0L, 1, 1, page1Body)
        // Audio page with minimal data
        val audioBody = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val page2 = buildOggPage(0x00, 1024L, 1, 2, audioBody)

        val baos = ByteArrayOutputStream()
        baos.write(page0)
        baos.write(page1)
        baos.write(page2)
        return baos.toByteArray()
    }

    private fun buildOpusOgg(comments: List<String>): ByteArray {
        val page0Body = buildOpusHeadPacket()
        val page0 = buildOggPage(0x02, 0L, 1, 0, page0Body)
        val page1Body = buildOpusCommentPacket(comments)
        val page1 = buildOggPage(0x00, 0L, 1, 1, page1Body)
        val audioBody = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val page2 = buildOggPage(0x00, 960L, 1, 2, audioBody)

        val baos = ByteArrayOutputStream()
        baos.write(page0)
        baos.write(page1)
        baos.write(page2)
        return baos.toByteArray()
    }

    @Test
    fun `read Vorbis metadata`() {
        val comments = listOf("TITLE=Vorbis Song", "ARTIST=Vorbis Artist", "ALBUM=Vorbis Album")
        val oggData = buildVorbisOgg(comments)

        val tempFile = File.createTempFile("test", ".ogg")
        try {
            tempFile.writeBytes(oggData)
            val meta = OggProcessor.read(tempFile.absolutePath)

            assertNotNull(meta)
            assertEquals("Vorbis Song", meta.title)
            assertEquals("Vorbis Artist", meta.artist)
            assertEquals("Vorbis Album", meta.album)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `read Opus metadata`() {
        val comments = listOf("TITLE=Opus Song", "ARTIST=Opus Artist")
        val oggData = buildOpusOgg(comments)

        val tempFile = File.createTempFile("test", ".ogg")
        try {
            tempFile.writeBytes(oggData)
            val meta = OggProcessor.read(tempFile.absolutePath)

            assertNotNull(meta)
            assertEquals("Opus Song", meta.title)
            assertEquals("Opus Artist", meta.artist)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `read metadata with picture`() {
        val picData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02)
        val pic = MetaDataBlockPicture(
            MetaDataBlockPicture.TYPE_FRONT_COVER, "image/jpeg", "", 100, 100, 24, 0, picData
        )
        val b64 = Base64.encode(pic.buildPayload())
        val comments = listOf("TITLE=With Pic", "METADATA_BLOCK_PICTURE=$b64")
        val oggData = buildVorbisOgg(comments)

        val tempFile = File.createTempFile("test", ".ogg")
        try {
            tempFile.writeBytes(oggData)
            val meta = OggProcessor.read(tempFile.absolutePath)

            assertNotNull(meta)
            assertEquals("With Pic", meta.title)
            assertNotNull(meta.pictureData)
            assertContentEquals(picData, meta.pictureData)
            assertEquals("image/jpeg", meta.pictureMimeType)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `read with lyrics`() {
        val comments = listOf("TITLE=Song", "LYRICS=Line 1\nLine 2\nLine 3")
        val oggData = buildVorbisOgg(comments)

        val tempFile = File.createTempFile("test", ".ogg")
        try {
            tempFile.writeBytes(oggData)
            val meta = OggProcessor.read(tempFile.absolutePath)

            assertNotNull(meta)
            assertEquals("Line 1\nLine 2\nLine 3", meta.lyrics)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `read returns null for invalid file`() {
        val tempFile = File.createTempFile("test", ".ogg")
        try {
            tempFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
            val meta = OggProcessor.read(tempFile.absolutePath)
            assertNull(meta)
        } finally {
            tempFile.delete()
        }
    }
}
