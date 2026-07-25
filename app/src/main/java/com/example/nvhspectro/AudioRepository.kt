package com.example.nvhspectro

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max

class AudioRepository {
    private var audioRecord: AudioRecord? = null
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var isRecording = false

    val bufferSize = max(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        2048
    )

    @SuppressLint("MissingPermission")
    fun startAudioCapture(fftSize: Int = 2048): Flow<ShortArray> = callbackFlow {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        // On s'assure que le buffer d'enregistrement est assez grand
        val recordBufferSize = max(minBufferSize, fftSize * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            recordBufferSize
        )

        audioRecord?.startRecording()

        // Overlap de 50%
        val stepSize = fftSize / 2
        val readBuffer = ShortArray(stepSize)
        val slidingWindow = ShortArray(fftSize)

        while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val readResult = audioRecord?.read(readBuffer, 0, stepSize) ?: 0
            if (readResult > 0) {
                // Décalage de la fenêtre glissante
                System.arraycopy(slidingWindow, stepSize, slidingWindow, 0, fftSize - stepSize)
                // Ajout des nouvelles données
                System.arraycopy(readBuffer, 0, slidingWindow, fftSize - stepSize, readResult)
                
                // On émet une copie de la fenêtre pour la FFT
                trySend(slidingWindow.clone())
            }
        }

        awaitClose {
            stopAudioCapture()
        }
    }.flowOn(Dispatchers.IO)

    fun stopAudioCapture() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
