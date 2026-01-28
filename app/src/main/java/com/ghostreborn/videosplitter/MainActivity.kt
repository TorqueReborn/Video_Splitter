package com.ghostreborn.videosplitter

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

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

            Log.e("TAG", videoTrackIndex.toString())
            Log.e("TAG", audioTrackIndex.toString())
            Log.e("TAG", videoFormat.toString())
            Log.e("TAG", audioFormat.toString())
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