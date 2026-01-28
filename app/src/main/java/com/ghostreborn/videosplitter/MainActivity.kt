package com.ghostreborn.videosplitter

import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private val videoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) {uri: Uri? ->
        if (uri != null) {
            val inputFd = applicationContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalArgumentException("Cannot open input URI")
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFd.fileDescriptor)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)

                when {
                    mime?.startsWith("video/") == true -> {
                        videoTrackIndex = i
                        videoFormat = format
                    }
                    mime?.startsWith("audio/") == true -> {
                        audioTrackIndex = i
                        audioFormat = format
                    }
                }
            }

            val duration = videoFormat!!.getLong(MediaFormat.KEY_DURATION)
            val halfDuration = duration / 2

            // Create output dirs
            val outputDir = File(Environment.getExternalStorageDirectory(), "Movies")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val firstHalfFile = File(outputDir, "video_first_half_${System.currentTimeMillis()}.mp4")
            val secondHalfFile = File(outputDir, "video_second_half_${System.currentTimeMillis()}.mp4")

            // Split first half
            extractSegment(uri, firstHalfFile, 0, halfDuration, videoTrackIndex, audioTrackIndex, videoFormat, audioFormat)

            // Split second half
            extractSegment(uri, secondHalfFile, halfDuration, duration, videoTrackIndex, audioTrackIndex, videoFormat, audioFormat)

            inputFd.close()

            // Make files visible to media scanner
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(firstHalfFile)))
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(secondHalfFile)))


        }
    }

    private fun extractSegment(
        uri: Uri,
        outputFile: File,
        startTimeUs: Long,
        endTimeUs: Long,
        videoTrackIndex: Int,
        audioTrackIndex: Int,
        videoFormat: MediaFormat,
        audioFormat: MediaFormat?
    ) {
        val extractor = MediaExtractor()
        val inputFd = contentResolver.openFileDescriptor(uri, "r")!!
        extractor.setDataSource(inputFd.fileDescriptor)

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Add video track
        extractor.selectTrack(videoTrackIndex)
        val muxerVideoTrack = muxer.addTrack(videoFormat)

        // Add audio track if available
        var muxerAudioTrack = -1
        if (audioTrackIndex != -1 && audioFormat != null) {
            extractor.unselectTrack(videoTrackIndex)
            extractor.selectTrack(audioTrackIndex)
            muxerAudioTrack = muxer.addTrack(audioFormat)
        }

        muxer.start()

        // Extract video
        if (audioTrackIndex != -1) {
            extractor.unselectTrack(audioTrackIndex)
        }
        extractor.selectTrack(videoTrackIndex)
        extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        extractTrack(extractor, muxer, muxerVideoTrack, startTimeUs, endTimeUs)

        // Extract audio
        if (audioTrackIndex != -1) {
            extractor.unselectTrack(videoTrackIndex)
            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            extractTrack(extractor, muxer, muxerAudioTrack, startTimeUs, endTimeUs)
        }

        muxer.stop()
        muxer.release()
        extractor.release()
        inputFd.close()
    }

    private fun extractTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        muxerTrackIndex: Int,
        startTimeUs: Long,
        endTimeUs: Long
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocate(1024 * 1024)
        var offsetTimeUs = -1L

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            val sampleTime = extractor.sampleTime
            if (sampleTime > endTimeUs) break

            if (sampleTime >= startTimeUs) {
                if (offsetTimeUs == -1L) {
                    offsetTimeUs = sampleTime
                }

                bufferInfo.presentationTimeUs = sampleTime - offsetTimeUs
                bufferInfo.size = sampleSize
                bufferInfo.flags = extractor.sampleFlags
                bufferInfo.offset = 0

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
            }

            extractor.advance()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.button).setOnClickListener {
            videoPicker.launch("video/*")
        }
    }
}