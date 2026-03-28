package me.ikun0014.audiotagger.mp3

import me.ikun0014.audiotagger.MusicMeta
import me.ikun0014.audiotagger.setMeta
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.test.*

class Mp3ProcessorTest {

    private fun buildId3Tag(vararg frames: Pair<String, ByteArray>): ByteArray {
        val frameBuf = ByteArrayOutputStream()
        val frameDos = DataOutputStream(frameBuf)
        for ((id, data) in frames) {
            frameDos.write(id.toByteArray(StandardCharsets.ISO_8859_1))
            frameDos.writeInt(data.size)
            frameDos.writeShort(0) // flags
            frameDos.write(data)
        }
        val frameBytes = frameBuf.toByteArray()

        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.write("ID3".toByteArray())
        dos.write(3) // version major
        dos.write(0) // version minor
        dos.write(0) // flags
        // synch-safe size
        val size = frameBytes.size
        dos.write((size shr 21) and 0x7F)
        dos.write((size shr 14) and 0x7F)
        dos.write((size shr 7) and 0x7F)
        dos.write(size and 0x7F)
        dos.write(frameBytes)
        // Fake MP3 sync bytes
        dos.write(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00))
        return baos.toByteArray()
    }

    private fun textFrame(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(1) // UTF-16 encoding
        baos.write(0xFF) // BOM LE
        baos.write(0xFE)
        baos.write(text.toByteArray(StandardCharsets.UTF_16LE))
        return baos.toByteArray()
    }

    private fun textFrameIso(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(0) // ISO-8859-1 encoding
        baos.write(text.toByteArray(StandardCharsets.ISO_8859_1))
        return baos.toByteArray()
    }

    private fun usltFrame(lyrics: String): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(1) // UTF-16 encoding
        baos.write("eng".toByteArray()) // language
        baos.write(0xFF); baos.write(0xFE) // BOM for descriptor
        baos.write(0x00); baos.write(0x00) // null-terminated empty descriptor
        baos.write(0xFF); baos.write(0xFE) // BOM for text
        baos.write(lyrics.toByteArray(StandardCharsets.UTF_16LE))
        return baos.toByteArray()
    }

    private fun apicFrame(mimeType: String, pictureData: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(0) // ISO-8859-1 encoding
        baos.write(mimeType.toByteArray(StandardCharsets.ISO_8859_1))
        baos.write(0) // null terminator
        baos.write(3) // picture type = front cover
        baos.write(0) // null-terminated empty description
        baos.write(pictureData)
        return baos.toByteArray()
    }

    @Test
    fun `read text frames UTF-16`() {
        val mp3Data = buildId3Tag(
            "TIT2" to textFrame("Test Title"),
            "TPE1" to textFrame("Test Artist"),
            "TALB" to textFrame("Test Album")
        )

        val tempFile = File.createTempFile("test", ".mp3")
        try {
            tempFile.writeBytes(mp3Data)
            val meta = Mp3Processor.read(tempFile.absolutePath)

            assertNotNull(meta)
            assertEquals("Test Title", meta.title)
            assertEquals("Test Artist", meta.artist)
            assertEquals("Test Album", meta.album)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `read text frames ISO-8859-1`() {
        val mp3Data = buildId3Tag(
            "TIT2" to textFrameIso("Simple Title"),
            "TPE1" to textFrameIso("Simple Artist")
        )

        val tempFile = File.createTempFile("test", ".mp3")
        try {
            tempFile.writeBytes(mp3Data)
            val meta = Mp3Processor.read(tempFile.absolutePath)

            assertNotNull(meta)
            assertEquals("Simple Title", meta.title)
            assertEquals("Simple Artist", meta.artist)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `read USLT lyrics frame`() {
        val mp3Data = buildId3Tag(
            "TIT2" to textFrame("Song"),
            "USLT" to usltFrame("Lyrics line 1\nLyrics line 2")
        )

        val tempFile = File.createTempFile("test", ".mp3")
        try {
            tempFile.writeBytes(mp3Data)
            val meta = Mp3Processor.read(tempFile.absolutePath)

            assertNotNull(meta)
            assertEquals("Song", meta.title)
            assertEquals("Lyrics line 1\nLyrics line 2", meta.lyrics)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `read APIC picture frame`() {
        val picBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03)
        val mp3Data = buildId3Tag(
            "TIT2" to textFrame("Song"),
            "APIC" to apicFrame("image/jpeg", picBytes)
        )

        val tempFile = File.createTempFile("test", ".mp3")
        try {
            tempFile.writeBytes(mp3Data)
            val meta = Mp3Processor.read(tempFile.absolutePath)

            assertNotNull(meta)
            assertNotNull(meta.pictureData)
            assertContentEquals(picBytes, meta.pictureData)
            assertEquals("image/jpeg", meta.pictureMimeType)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `read returns null for non-ID3 file`() {
        val tempFile = File.createTempFile("test", ".mp3")
        try {
            tempFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00))
            val meta = Mp3Processor.read(tempFile.absolutePath)
            assertNull(meta)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `write and read round-trip`() {
        // Create a minimal MP3 file (just audio sync bytes, no ID3)
        val tempFile = File.createTempFile("test", ".mp3")
        try {
            tempFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00))

            val writeMeta = MusicMeta()
            writeMeta.title = "Round Trip Title"
            writeMeta.artist = "Round Trip Artist"
            writeMeta.album = "Round Trip Album"
            Mp3Processor.write(tempFile.absolutePath, writeMeta)

            val readMeta = Mp3Processor.read(tempFile.absolutePath)
            assertNotNull(readMeta)
            assertEquals("Round Trip Title", readMeta.title)
            assertEquals("Round Trip Artist", readMeta.artist)
            assertEquals("Round Trip Album", readMeta.album)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `setMeta preserves unset fields`() {
        val tempFile = File.createTempFile("test", ".mp3")
        try {
            tempFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00))

            // Write initial metadata with all fields
            val initial = MusicMeta()
            initial.title = "Original Title"
            initial.artist = "Original Artist"
            initial.album = "Original Album"
            setMeta(tempFile.absolutePath, initial)

            // Update only title, leave artist and album null
            val update = MusicMeta()
            update.title = "New Title"
            setMeta(tempFile.absolutePath, update)

            val result = Mp3Processor.read(tempFile.absolutePath)
            assertNotNull(result)
            assertEquals("New Title", result.title)
            assertEquals("Original Artist", result.artist)
            assertEquals("Original Album", result.album)
        } finally {
            tempFile.delete()
        }
    }
}
