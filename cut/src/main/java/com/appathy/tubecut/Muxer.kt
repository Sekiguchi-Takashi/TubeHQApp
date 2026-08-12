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
 * 切れ目はキーフレームに限られる（Probe.snapBefore で吸着済みの前提）。
 */
object Muxer {

    class Progress(var done: Int, var total: Int, var cancelled: Boolean = false)

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
        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()

        try {
            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 先頭素材のフォーマットでトラックを作る
            val first = proj.sources[segs[0].srcIndex]
            val probe = MediaExtractor()
            probe.setDataSource(ctx, Uri.parse(first.uri), null)
            var vFmt: MediaFormat? = null
            var aFmt: MediaFormat? = null
            for (i in 0 until probe.trackCount) {
                val f = probe.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && vFmt == null) vFmt = f
                if (mime.startsWith("audio/") && aFmt == null) aFmt = f
            }
            probe.release()
            if (vFmt == null) return "映像トラックがありません"

            val vTrack = muxer.addTrack(vFmt)
            val aTrack = if (aFmt != null) muxer.addTrack(aFmt) else -1
            if (first.rotation != 0) muxer.setOrientationHint(first.rotation)
            muxer.start()

            var offsetUs = 0L
            prog.total = segs.size

            for ((si, seg) in segs.withIndex()) {
                if (prog.cancelled) break
                val src = proj.sources[seg.srcIndex]
                var lastUs = 0L

                for (pass in 0..1) {
                    if (pass == 1 && aTrack < 0) continue
                    val ex = MediaExtractor()
                    try {
                        ex.setDataSource(ctx, Uri.parse(src.uri), null)
                        var track = -1
                        for (i in 0 until ex.trackCount) {
                            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                            val want = if (pass == 0) "video/" else "audio/"
                            if (mime.startsWith(want)) {
                                track = i
                                break
                            }
                        }
                        if (track < 0) continue
                        ex.selectTrack(track)
                        ex.seekTo(seg.inMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                        val dst = if (pass == 0) vTrack else aTrack
                        val startUs = ex.sampleTime
                        while (true) {
                            if (prog.cancelled) break
                            val t = ex.sampleTime
                            if (t < 0) break
                            if (t > seg.outMs * 1000) break
                            buffer.clear()
                            val size = ex.readSampleData(buffer, 0)
                            if (size < 0) break
                            info.offset = 0
                            info.size = size
                            info.presentationTimeUs = offsetUs + (t - startUs)
                            info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                                MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            muxer.writeSampleData(dst, buffer, info)
                            if (info.presentationTimeUs > lastUs) lastUs = info.presentationTimeUs
                            if (!ex.advance()) break
                        }
                    } catch (e: Throwable) {
                    } finally {
                        try {
                            ex.release()
                        } catch (e: Exception) {
                        }
                    }
                }

                offsetUs = lastUs + 33000
                prog.done = si + 1
                onStep(si + 1, segs.size)
            }

            return if (prog.cancelled) "中断しました" else ""
        } catch (e: Throwable) {
            return "失敗: ${e.message}"
        } finally {
            try {
                muxer?.stop(); muxer?.release()
            } catch (e: Exception) {
            }
            try {
                pfd.close()
            } catch (e: Exception) {
            }
        }
    }
}
