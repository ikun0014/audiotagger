package me.ikun0014.audiotagger

import me.ikun0014.audiotagger.flac.FlacHandler
import me.ikun0014.audiotagger.mp3.Mp3Processor
import me.ikun0014.audiotagger.ogg.OggProcessor

fun getMeta(filePath: String): MusicMeta? {
    val lowerPath = filePath.lowercase()
    return when {
        lowerPath.endsWith(".mp3") -> Mp3Processor.read(filePath)
        lowerPath.endsWith(".flac") -> FlacHandler.read(filePath)
        lowerPath.endsWith(".ogg") -> OggProcessor.read(filePath)
        else -> null
    }
}

fun setMeta(filePath: String, meta: MusicMeta) {
    val existing = getMeta(filePath)
    if (existing != null) {
        if (meta.title == null) meta.title = existing.title
        if (meta.artist == null) meta.artist = existing.artist
        if (meta.album == null) meta.album = existing.album
        if (meta.lyrics == null) meta.lyrics = existing.lyrics
        if (meta.picture == null && meta.pictureData == null) {
            meta.pictureData = existing.pictureData
            meta.pictureMimeType = existing.pictureMimeType
        }
    }
    val lowerPath = filePath.lowercase()
    when {
        lowerPath.endsWith(".mp3") -> Mp3Processor.write(filePath, meta)
        lowerPath.endsWith(".flac") -> FlacHandler.write(filePath, meta)
        lowerPath.endsWith(".ogg") -> OggProcessor.write(filePath, meta)
    }
}
