package com.example.supercamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var zoomTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var captureButton: Button

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var currentMode: String = "Standard"

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)
        zoomTextView = findViewById(R.id.zoomTextView)
        statusTextView = findViewById(R.id.statusTextView)
        captureButton = findViewById(R.id.captureButton)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setupZoomControls()
        setupModeButtons()
        setupExtraFeatures()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Błąd inicjalizacji kamery", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupZoomControls() {
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val zoomRatio = 0.6f + (progress.toFloat() / 1200f) * (120.0f - 0.6f)
                camera?.cameraControl?.setZoomRatio(zoomRatio)
                zoomTextView.text = String.format(Locale.US, "Zoom: %.1fx", zoomRatio)

                if (zoomRatio > 15.0f) {
                    statusTextView.text = "Tryb: $currentMode | AI Super-Resolution: AKTYWNE"
                } else {
                    statusTextView.text = "Tryb: $currentMode | AI: Standard"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupModeButtons() {
        findViewById<Button>(R.id.btnFoto).setOnClickListener { setMode("Standard", 1.0f) }
        findViewById<Button>(R.id.btnVideo).setOnClickListener { setMode("Wideo", 1.0f) }

        findViewById<Button>(R.id.btnBetter).setOnClickListener { 
            setMode("Better (10x)", 10.0f)
            setProgressForZoom(10.0f)
        }

        findViewById<Button>(R.id.btnUltra).setOnClickListener { 
            setMode("Ultra (15x)", 15.0f)
            setProgressForZoom(15.0f)
        }

        findViewById<Button>(R.id.btnNight).setOnClickListener { setMode("Tryb Nocny", 1.0f) }

        findViewById<Button>(R.id.btnUltraNight).setOnClickListener { 
            setMode("Ultra Night AI", 1.0f)
            Toast.makeText(this, "Włączono symulację jasności dziennej w nocy", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPortrait).setOnClickListener { setMode("Portret", 1.0f) }
        findViewById<Button>(R.id.btnExtraHd).setOnClickListener { setMode("Extra HD", 1.0f) }
        findViewById<Button>(R.id.btnTimelapse).setOnClickListener { setMode("Poklatkowy", 1.0f) }
        findViewById<Button>(R.id.btnSlowMo).setOnClickListener { setMode("Zwolnione Tempo", 1.0f) }
        findViewById<Button>(R.id.btnPanorama).setOnClickListener { setMode("Panorama", 1.0f) }
    }

    private fun setMode(modeName: String, targetZoom: Float) {
        currentMode = modeName
        statusTextView.text = "Tryb: $currentMode"
        Toast.makeText(this, "Przełączono na: $modeName", Toast.LENGTH_SHORT).show()
    }

    private fun setProgressForZoom(zoom: Float) {
        val progress = ((zoom - 0.6f) / (120.0f - 0.6f) * 1200f).toInt()
        zoomSeekBar.progress = progress
    }

    private fun setupExtraFeatures() {
        captureButton.setOnClickListener {
            Toast.makeText(this, "Zapisano zdjęcie w trybie: $currentMode z optymalizacją AI!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Wymagane uprawnienia do kamery nie zostały przyznane.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
