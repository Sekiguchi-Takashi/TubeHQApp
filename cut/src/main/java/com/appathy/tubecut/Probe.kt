package com.appathy.tubecut

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

object Probe {

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) return c.getString(i)
            }
        } catch (e: Exception) {
        }
        return uri.lastPathSegment ?: "素材"
    }

    /** MediaExtractor で素性を読む。失敗しても probed=0 のまま返す */
    fun probe(ctx: Context, s: Source) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, Uri.parse(s.uri), null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    s.vCodec = mime
                    s.width = optInt(f, MediaFormat.KEY_WIDTH)
                    s.height = optInt(f, MediaFormat.KEY_HEIGHT)
                    s.fps = optInt(f, MediaFormat.KEY_FRAME_RATE)
                    val d = optLong(f, MediaFormat.KEY_DURATION)
                    if (d > 0) s.durationMs = d / 1000
                } else if (mime.startsWith("audio/")) {
                    s.aCodec = mime
                    val d = optLong(f, MediaFormat.KEY_DURATION)
                    if (d / 1000 > s.durationMs) s.durationMs = d / 1000
                }
            }
        } catch (e: Throwable) {
        } finally {
            try {
                ex.release()
            } catch (e: Exception) {
            }
        }

        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, Uri.parse(s.uri))
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            s.rotation = rot?.toIntOrNull() ?: 0
            if (s.durationMs <= 0) {
                s.durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0
            }
            if (s.fps <= 0) {
                s.fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()?.toInt() ?: 30
            }
        } catch (e: Throwable) {
        } finally {
            try {
                r.release()
            } catch (e: Exception) {
            }
        }

        s.probed = if (s.durationMs > 0) 1 else 0
    }

    private fun optInt(f: MediaFormat, key: String): Int =
        try {
            if (f.containsKey(key)) f.getInteger(key) else 0
        } catch (e: Exception) {
            0
        }

    private fun optLong(f: MediaFormat, key: String): Long =
        try {
            if (f.containsKey(key)) f.getLong(key) else 0L
        } catch (e: Exception) {
            0L
        }

    /**
     * キーフレーム（sync sample）の位置を列挙する。
     * MediaMuxer での無劣化カットはここにしか切れ目を置けない。
     */
    fun keyframes(ctx: Context, s: Source): LongArray {
        val out = ArrayList<Long>()
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, Uri.parse(s.uri), null)
            var track = -1
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    track = i
                    break
                }
            }
            if (track < 0) return LongArray(0)
            ex.selectTrack(track)
            while (true) {
                val t = ex.sampleTime
                if (t < 0) break
                if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) out.add(t / 1000)
                if (!ex.advance()) break
            }
        } catch (e: Throwable) {
        } finally {
            try {
                ex.release()
            } catch (e: Exception) {
            }
        }
        return out.toLongArray()
    }

    /** 指定位置以前の直近キーフレーム */
    fun snapBefore(keys: LongArray, ms: Long): Long {
        if (keys.isEmpty()) return ms
        var best = keys[0]
        for (k in keys) {
            if (k <= ms) best = k else break
        }
        return best
    }

    fun frameAt(ctx: Context, uri: String, ms: Long): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, Uri.parse(uri))
            r.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Throwable) {
            null
        } finally {
            try {
                r.release()
            } catch (e: Exception) {
            }
        }
    }
}
