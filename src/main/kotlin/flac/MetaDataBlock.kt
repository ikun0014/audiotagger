package me.ikun0014.audiotagger.flac

interface MetaDataBlock {
    fun buildPayload(): ByteArray
}
