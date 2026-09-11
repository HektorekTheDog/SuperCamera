package com.example.supercamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.PermissionChecker
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var statusTextView: TextView
    private lateinit var zoomTextView: TextView
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var captureButton: Button

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService

    private var isVideoMode = false
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        statusTextView = findViewById(R.id.statusTextView)
        zoomTextView = findViewById(R.id.zoomTextView)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)
        captureButton = findViewById(R.id.captureButton)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Obsługa suwaka zoomu
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && camera != null) {
                    val linearZoom = progress / 1200f
                    camera?.cameraControl?.setLinearZoom(linearZoom)
                    val zoomVal = 1.0f + (linearZoom * 9.0f)
                    zoomTextView.text = String.format(Locale.US, "%.1fx", zoomVal)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Główny przycisk akcji (ZDJĘCIE / NAGRYWAJ)
        captureButton.setOnClickListener {
            if (isVideoMode) {
                if (isRecording) {
                    // Zatrzymaj nagrywanie
                    recording?.stop()
                    recording = null
                    isRecording = false
                    captureButton.text = "ZDJĘCIE"
                    captureButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    Toast.Video?.let { } // holder
                } else {
                    // Rozpocznij nagrywanie wideo
                    startRecording()
                }
            } else {
                // Zrób zdjęcie
                takePhoto()
            }
        }

        // Przyciski trybów
        findViewById<Button>(R.id.btnFoto).setOnClickListener { 
            isVideoMode = false
            statusTextView.text = "Tryb: Standard | AI: OFF"
            captureButton.text = "ZDJĘCIE"
        }
        findViewById<Button>(R.id.btnVideo).setOnClickListener { 
            isVideoMode = true
            statusTextView.text = "Tryb: Wideo | AUDIO: ON"
            captureButton.text = "NAGRYWAJ"
        }
        findViewById<Button>(R.id.btnBetter).setOnClickListener { 
            isVideoMode = false
            statusTextView.text = "Tryb: Better (10x) | AI: ON"
            zoomSeekBar.progress = 1200 
        }
        findViewById<Button>(R.id.btnUltra).setOnClickListener { statusTextView.text = "Tryb: Ultra (15x) | AI: ON" }
        findViewById<Button>(R.id.btnNight).setOnClickListener { statusTextView.text = "Tryb: Nocny | AI: ON" }
        findViewById<Button>(R.id.btnUltraNight).setOnClickListener { statusTextView.text = "Tryb: Ultra Night AI | MAX" }
        findViewById<Button>(R.id.btnPortrait).setOnClickListener { statusTextView.text = "Tryb: Portret | Rozmycie: ON" }
        findViewById<Button>(R.id.btnExtraHd).setOnClickListener { statusTextView.text = "Tryb: Extra HD | 48MP" }
        findViewById<Button>(R.id.btnTimelapse).setOnClickListener { statusTextView.text = "Tryb: Poklatkowy" }
        findViewById<Button>(R.id.btnSlowMo).setOnClickListener { statusTextView.text = "Tryb: Zwolnione T." }
        findViewById<Button>(R.id.btnPanorama).setOnClickListener { statusTextView.text = "Tryb: Panorama" }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 10
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, videoCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Błąd startu kamery", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SuperCamera")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Błąd zapisu zdjęcia: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(baseContext, "Zapisano zdjęcie w Galerii!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun startRecording() {
        val videoCapture = videoCapture ?: return

        captureButton.text = "STOP"
        captureButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SuperCamera")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).contentValues(contentValues).build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(baseContext, Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        Toast.makeText(baseContext, "Rozpoczęto nagrywanie wideo", Toast.LENGTH_SHORT).show()
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            Toast.makeText(baseContext, "Zapisano wideo w Galerii!", Toast.LENGTH_SHORT).show()
                        } else {
                            recording?.close()
                            recording = null
                            Toast.makeText(baseContext, "Błąd nagrywania: ${recordEvent.error}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Wymagane uprawnienia do kamery i mikrofonu!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
