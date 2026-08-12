package com.appathy.tubecut

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 音声をデコードして RMS を取り、無音区間を出す。
 * RMS 配列は 50個/秒（20ms窓）。10分尺で30,000要素。
 */
object Silence {

    const val PER_SEC = 50
    private const val WINDOW_MS = 1000 / PER_SEC

    class Result(val rms: FloatArray, val durationMs: Long)

    fun analyze(ctx: Context, uri: String, onProgress: (Int) -> Unit): Result? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, Uri.parse(uri), null)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    fmt = f
                    break
                }
            }
            if (track < 0 || fmt == null) return null
            ex.selectTrack(track)

            val mime = fmt.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val totalUs = if (fmt.containsKey(MediaFormat.KEY_DURATION))
                fmt.getLong(MediaFormat.KEY_DURATION) else 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(fmt, null, null, 0)
            codec.start()

            val samplesPerWindow = Math.max(1, sampleRate * WINDOW_MS / 1000) * channels
            val out = ArrayList<Float>(4096)
            var acc = 0.0
            var accCount = 0
            var lastPct = -1

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)
                        val size = if (buf == null) -1 else ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }

                val oi = codec.dequeueOutputBuffer(info, 10000)
                if (oi >= 0) {
                    if (info.size > 0) {
                        val buf = codec.getOutputBuffer(oi)
                        if (buf != null) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val sb = buf.asShortBuffer()
                            while (sb.hasRemaining()) {
                                val v = sb.get() / 32768.0
                                acc += v * v
                                accCount++
                                if (accCount >= samplesPerWindow) {
                                    out.add(sqrt(acc / accCount).toFloat())
                                    acc = 0.0
                                    accCount = 0
                                }
                            }
                        }
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (totalUs > 0) {
                        val pct = (info.presentationTimeUs * 100 / totalUs).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
            }
            if (accCount > 0) out.add(sqrt(acc / accCount).toFloat())

            val arr = FloatArray(out.size) { out[it] }
            return Result(arr, (arr.size.toLong() * WINDOW_MS))
        } catch (e: Throwable) {
            return null
        } finally {
            try {
                codec?.stop(); codec?.release()
            } catch (e: Exception) {
            }
            try {
                ex.release()
            } catch (e: Exception) {
            }
        }
    }

    fun db(v: Float): Float = if (v <= 0.000001f) -90f else (20.0 * log10(v.toDouble())).toFloat()

    /**
     * RMS 配列から区間を組む。
     * 無音区間は use=0、発話区間は use=1 として返す。
     */
    fun segments(
        rms: FloatArray,
        srcIndex: Int,
        thresholdDb: Int,
        minSilenceMs: Int,
        padHeadMs: Int,
        padTailMs: Int
    ): MutableList<Segment> {
        val out = mutableListOf<Segment>()
        if (rms.isEmpty()) return out

        val quiet = BooleanArray(rms.size) { db(rms[it]) < thresholdDb }
        val minWin = Math.max(1, minSilenceMs / (1000 / PER_SEC))

        // 短すぎる無音は無音と見なさない
        var i = 0
        while (i < quiet.size) {
            if (quiet[i]) {
                var j = i
                while (j < quiet.size && quiet[j]) j++
                if (j - i < minWin) for (k in i until j) quiet[k] = false
                i = j
            } else i++
        }

        val toMs = { idx: Int -> idx.toLong() * (1000 / PER_SEC) }

        i = 0
        while (i < quiet.size) {
            val silent = quiet[i]
            var j = i
            while (j < quiet.size && quiet[j] == silent) j++
            var start = toMs(i)
            var end = toMs(j)
            if (!silent) {
                start = Math.max(0L, start - padHeadMs)
                end = Math.max(start + 200L, end + padTailMs)
            } else {
                start += padTailMs
                end -= padHeadMs
            }
            if (end > start) {
                out.add(
                    Segment(
                        srcIndex = srcIndex,
                        inMs = start,
                        outMs = end,
                        use = if (silent) 0 else 1,
                        silent = if (silent) 1 else 0
                    )
                )
            }
            i = j
        }
        return out
    }
}
