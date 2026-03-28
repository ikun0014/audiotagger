package me.ikun0014.audiotagger.flac

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FlacProcessorTest {

    private fun buildMinimalFlac(vararg blocks: Pair<Int, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.write("fLaC".toByteArray())
        for ((index, pair) in blocks.withIndex()) {
            val (type, data) = pair
            val isLast = index == blocks.size - 1
            var headerFirst = type and 0x7F
            if (isLast) headerFirst = headerFirst or 0x80
            dos.write(headerFirst)
            dos.write((data.size ushr 16) and 0xFF)
            dos.write((data.size ushr 8) and 0xFF)
            dos.write(data.size and 0xFF)
            dos.write(data)
        }
        // Fake audio frame
        dos.write(byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x00, 0x00))
        return baos.toByteArray()
    }

    @Test
    fun `read metadata from FLAC stream`() {
        val comments = listOf("TITLE=Test Title", "ARTIST=Test Artist", "ALBUM=Test Album", "LYRICS=La la la")
        val vcPayload = MetaDataBlockVorbisComment("test vendor", comments).buildPayload()

        val picData = byteArrayOf(1, 2, 3, 4, 5)
        val picPayload = MetaDataBlockPicture(
            MetaDataBlockPicture.TYPE_FRONT_COVER, "image/jpeg", "", 100, 100, 24, 0, picData
        ).buildPayload()

        val streamInfo = ByteArray(34) // STREAMINFO block (34 bytes of zeros for testing)
        val flacData = buildMinimalFlac(
            0 to streamInfo,
            4 to vcPayload,
            6 to picPayload
        )

        val meta = FlacProcessor.readMeta(ByteArrayInputStream(flacData))

        assertEquals("Test Title", meta.title)
        assertEquals("Test Artist", meta.artist)
        assertEquals("Test Album", meta.album)
        assertEquals("La la la", meta.lyrics)
        assertNotNull(meta.pictureData)
        assertEquals("image/jpeg", meta.pictureMimeType)
    }

    @Test
    fun `read metadata without picture`() {
        val comments = listOf("TITLE=Song", "ARTIST=Band")
        val vcPayload = MetaDataBlockVorbisComment("vendor", comments).buildPayload()
        val streamInfo = ByteArray(34)
        val flacData = buildMinimalFlac(0 to streamInfo, 4 to vcPayload)

        val meta = FlacProcessor.readMeta(ByteArrayInputStream(flacData))

        assertEquals("Song", meta.title)
        assertEquals("Band", meta.artist)
        assertNull(meta.pictureData)
    }

    @Test
    fun `write and read round-trip`() {
        val comments = listOf("TITLE=Original")
        val vcPayload = MetaDataBlockVorbisComment("vendor", comments).buildPayload()
        val streamInfo = ByteArray(34)
        val flacData = buildMinimalFlac(0 to streamInfo, 4 to vcPayload)

        // Write new metadata
        val newVc = MetaDataBlockVorbisComment("new vendor", listOf("TITLE=Updated", "ARTIST=New Artist"))
        val newMeta = FlacProcessor.NewMetadata(newVc, null)
        val output = ByteArrayOutputStream()
        FlacProcessor(newMeta).process(ByteArrayInputStream(flacData), output)

        // Read back
        val result = FlacProcessor.readMeta(ByteArrayInputStream(output.toByteArray()))

        assertEquals("Updated", result.title)
        assertEquals("New Artist", result.artist)
    }

    @Test
    fun `write adds new blocks when not present`() {
        val streamInfo = ByteArray(34)
        // FLAC with only STREAMINFO, no VorbisComment
        val flacData = buildMinimalFlac(0 to streamInfo)

        val vc = MetaDataBlockVorbisComment("vendor", listOf("TITLE=Added"))
        val newMeta = FlacProcessor.NewMetadata(vc, null)
        val output = ByteArrayOutputStream()
        FlacProcessor(newMeta).process(ByteArrayInputStream(flacData), output)

        val result = FlacProcessor.readMeta(ByteArrayInputStream(output.toByteArray()))
        assertEquals("Added", result.title)
    }
}
