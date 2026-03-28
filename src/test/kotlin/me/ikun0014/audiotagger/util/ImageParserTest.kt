package me.ikun0014.audiotagger.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageParserTest {

    @Test
    fun `parse JPEG image`() {
        // Minimal JPEG: SOI marker + SOF0 marker with dimensions
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),                         // SOI
            0xFF.toByte(), 0xC0.toByte(),                         // SOF0 marker
            0x00, 0x0B,                                            // length = 11
            0x08,                                                  // precision
            0x01, 0x00,                                            // height = 256
            0x02, 0x00,                                            // width = 512
            0x03,                                                  // num components
            0x01, 0x11, 0x00,                                      // component data
            0xFF.toByte(), 0xD9.toByte()                           // EOI
        )

        val tempFile = File.createTempFile("test", ".jpg")
        try {
            tempFile.writeBytes(jpeg)
            val info = ImageParser.parse(tempFile.absolutePath)

            assertEquals("image/jpeg", info.mimeType)
            assertEquals(512, info.width)
            assertEquals(256, info.height)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `parse PNG image`() {
        // Minimal PNG header with IHDR chunk containing dimensions
        val png = ByteArray(25)
        // PNG signature
        png[0] = 0x89.toByte()
        png[1] = 0x50 // P
        png[2] = 0x4E // N
        png[3] = 0x47 // G
        png[4] = 0x0D
        png[5] = 0x0A
        png[6] = 0x1A
        png[7] = 0x0A
        // IHDR chunk
        png[8] = 0x00; png[9] = 0x00; png[10] = 0x00; png[11] = 0x0D // length = 13
        png[12] = 0x49; png[13] = 0x48; png[14] = 0x44; png[15] = 0x52 // "IHDR"
        // Width = 800 (big-endian)
        png[16] = 0x00; png[17] = 0x00; png[18] = 0x03; png[19] = 0x20.toByte()
        // Height = 600 (big-endian)
        png[20] = 0x00; png[21] = 0x00; png[22] = 0x02; png[23] = 0x58
        png[24] = 0x08 // bit depth

        val tempFile = File.createTempFile("test", ".png")
        try {
            tempFile.writeBytes(png)
            val info = ImageParser.parse(tempFile.absolutePath)

            assertEquals("image/png", info.mimeType)
            assertEquals(800, info.width)
            assertEquals(600, info.height)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `unknown format defaults to jpeg 500x500`() {
        val unknown = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09)

        val tempFile = File.createTempFile("test", ".bin")
        try {
            tempFile.writeBytes(unknown)
            val info = ImageParser.parse(tempFile.absolutePath)

            assertEquals("image/jpeg", info.mimeType)
            assertEquals(500, info.width)
            assertEquals(500, info.height)
        } finally {
            tempFile.delete()
        }
    }
}
