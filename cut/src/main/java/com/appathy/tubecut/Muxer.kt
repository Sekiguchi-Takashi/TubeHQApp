package com.appathy.tubecut

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.nio.ByteBuffer

/**
 * 速いレーン。再エンコードせずに区間を切り出して連結する。
 * 切れ目はキーフレームに限られる（区間タブでズレを表示している）。
 *
 * 時刻の扱いに2つの決まりがある。守らないと音ズレする。
 *
 * 1. 区間の基準時刻は**映像の吸着位置**に統一する。
 *    音声トラックを別々に seek すると着地点が違い、区間ごとに音がずれていく。
 * 2. 区間のつなぎ目に空ける間隔は**素材のfpsから出す**。
 *    30fps決め打ちだと 60fps 素材で毎回2フレーム分の隙間ができる。
 */
object Muxer {

    class Progress(var done: Int, var total: Int, var cancelled: Boolean = false)

    private class PassResult(var count: Int = 0, var lastUs: Long = 0L)

    fun run(
        ctx: Context,
        proj: EditProject,
        outUri: Uri,
        prog: Progress,
        onStep: (Int, Int) -> Unit
    ): String {
        val segs = proj.used()
        if (segs.isEmpty()) return "区間がありません"

        val opened = try {
            ctx.contentResolver.openFileDescriptor(outUri, "rw")
        } catch (e: Exception) {
            null
        }
        if (opened == null) return "出力先を開けません"
        val pfd = opened

        var muxer: MediaMuxer? = null
        var started = false
        val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        var failure = ""

        try {
            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val first = proj.sources[segs[0].srcIndex]
            var vFmt: MediaFormat? = null
            var aFmt: MediaFormat? = null
            val probe = MediaExtractor()
            try {
                probe.setDataSource(ctx, Uri.parse(first.uri), null)
                for (i in 0 until probe.trackCount) {
                    val f = probe.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") && vFmt == null) vFmt = f
                    if (mime.startsWith("audio/") && aFmt == null) aFmt = f
                }
            } finally {
                try {
                    probe.release()
                } catch (e: Exception) {
                }
            }
            if (vFmt == null) return "映像トラックがありません"

            val vTrack = muxer.addTrack(vFmt)
            val aTrack = if (aFmt != null) muxer.addTrack(aFmt) else -1
            if (first.rotation != 0) muxer.setOrientationHint(first.rotation)
            muxer.start()
            started = true

            var offsetUs = 0L
            prog.total = segs.size

            for ((si, seg) in segs.withIndex()) {
                if (prog.cancelled) break
                val src = proj.sources.getOrNull(seg.srcIndex)
                if (src == null) {
                    failure = "区間 ${si + 1} の素材が見つかりません"
                    break
                }

                // 1. 映像の吸着位置を先に確定させ、音声もこれを基準にする
                val baseUs = videoBase(ctx, src, seg)
                if (baseUs < 0) {
                    failure = "区間 ${si + 1} の映像を読めません"
                    break
                }

                val v = copyTrack(ctx, muxer, src, seg, "video/", vTrack, baseUs, offsetUs, buffer, info, prog)
                if (v.count == 0) {
                    failure = "区間 ${si + 1} に書き出せる映像がありません（切り出し位置を見直してください）"
                    break
                }
                var lastUs = v.lastUs

                if (aTrack >= 0) {
                    val a = copyTrack(ctx, muxer, src, seg, "audio/", aTrack, baseUs, offsetUs, buffer, info, prog)
                    if (a.lastUs > lastUs) lastUs = a.lastUs
                }

                // 2. つなぎ目の間隔は素材のfpsから
                val fps = if (src.fps in 1..480) src.fps else 30
                offsetUs = lastUs + (1_000_000L / fps)

                prog.done = si + 1
                onStep(si + 1, segs.size)
            }

            return when {
                failure.isNotBlank() -> failure
                prog.cancelled -> "中断しました"
                else -> ""
            }
        } catch (e: Throwable) {
            return "失敗: " + (e.message ?: e.javaClass.simpleName)
        } finally {
            try {
                if (started) muxer?.stop()
            } catch (e: Exception) {
            }
            try {
                muxer?.release()
            } catch (e: Exception) {
            }
            try {
                pfd.close()
            } catch (e: Exception) {
            }
        }
    }

    /** 区間の開始位置を、直前のキーフレームに吸着した実時刻で返す */
    private fun videoBase(ctx: Context, src: Source, seg: Segment): Long {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(ctx, Uri.parse(src.uri), null)
            val track = findTrack(ex, "video/")
            if (track < 0) return -1L
            ex.selectTrack(track)
            ex.seekTo(seg.inMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val t = ex.sampleTime
            if (t < 0) -1L else t
        } catch (e: Throwable) {
            -1L
        } finally {
            try {
                ex.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun findTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    private fun copyTrack(
        ctx: Context,
        muxer: MediaMuxer,
        src: Source,
        seg: Segment,
        prefix: String,
        dst: Int,
        baseUs: Long,
        offsetUs: Long,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        prog: Progress
    ): PassResult {
        val res = PassResult()
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, Uri.parse(src.uri), null)
            val track = findTrack(ex, prefix)
            if (track < 0) return res
            ex.selectTrack(track)
            ex.seekTo(baseUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val endUs = seg.outMs * 1000
            while (true) {
                if (prog.cancelled) break
                val t = ex.sampleTime
                if (t < 0) break
                if (t > endUs) break
                if (t < baseUs) {
                    // 音声は映像より前から始まることがある。基準より前は捨てる
                    if (!ex.advance()) break
                    continue
                }
                buffer.clear()
                val size = ex.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = offsetUs + (t - baseUs)
                info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(dst, buffer, info)
                res.count++
                if (info.presentationTimeUs > res.lastUs) res.lastUs = info.presentationTimeUs
                if (!ex.advance()) break
            }
        } catch (e: Throwable) {
            // 1トラックの失敗で全体を止めない。件数0として呼び出し側が判断する
        } finally {
            try {
                ex.release()
            } catch (e: Exception) {
            }
        }
        return res
    }
}
