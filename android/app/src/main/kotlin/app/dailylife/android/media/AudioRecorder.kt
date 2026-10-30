package app.dailylife.android.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/** Records audio into app storage. Permission is requested by the caller at the moment of use. */
class AudioRecorder(private val context: Context, private val dir: File) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    var startedAt: Long = 0
        private set

    fun start() {
        val f = File(dir, "${UUID.randomUUID()}.m4a")
        val r =
            if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
            else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = f
        startedAt = System.currentTimeMillis()
    }

    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        return runCatching {
            r.stop()
            r.release()
            file
        }
            .getOrElse {
                r.release()
                file?.delete()
                null
            }
    }

    val isRecording
        get() = recorder != null
}
