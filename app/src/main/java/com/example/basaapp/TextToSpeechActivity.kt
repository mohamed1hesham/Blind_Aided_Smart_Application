package com.example.basaapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowInsets
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextBlock
import com.google.android.gms.vision.text.TextRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class TextToSpeechActivity : AppCompatActivity() {
    private val MY_PERMISSIONS_REQUEST_CAMERA: Int = 101
    private lateinit var mCameraSource: CameraSource
    private lateinit var textRecognizer: TextRecognizer
    private val tag: String? = "TextToSpeechActivity"

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1001
    }

    private lateinit var surface_camera_preview: SurfaceView
    private lateinit var tv_result: TextView
    private lateinit var resetButton: Button
    private lateinit var captureButton: Button
    private var isTextRecognitionActive = false

    private lateinit var textToSpeech: TextToSpeech
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars())
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TAG", "Language not supported")
                }
            } else {
                Log.e("TAG", "Initialization failed")
            }
        }
        super.onCreate(savedInstanceState)
        window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars())
        setContentView(R.layout.activity_text_to_speech)


        surface_camera_preview = findViewById(R.id.capturedImg)
        tv_result = findViewById(R.id.resultProcess)
        resetButton = findViewById(R.id.resetBtn)
        captureButton = findViewById(R.id.takeImgBtn)

        val myButton: ImageButton = findViewById(R.id.backBtn)
        myButton.setOnClickListener {
            finish()
        }

        captureButton.setOnClickListener {
            capturePhoto()
        }
        resetButton.setOnClickListener {
            resetTextRecognition()
        }

        textRecognizer = TextRecognizer.Builder(this).build()
        if (!textRecognizer.isOperational) {
            Toast.makeText(
                this,
                "Dependencies are not loaded yet...please try after a few moments!!",
                Toast.LENGTH_LONG
            ).show()
            Log.e(tag, "Dependencies are downloading....try after a few moments")
            return
        }

        mCameraSource = CameraSource.Builder(applicationContext, textRecognizer)
            .setFacing(CameraSource.CAMERA_FACING_BACK)
            .setRequestedPreviewSize(1280, 1024)
            .setAutoFocusEnabled(true)
            .setRequestedFps(2.0f)
            .build()

        surface_camera_preview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(p0: SurfaceHolder) {
                mCameraSource.stop()
            }

            @SuppressLint("MissingPermission")
            override fun surfaceCreated(p0: SurfaceHolder) {
                try {
                    if (!isTextRecognitionActive) {
                        if (isCameraPermissionGranted()) {
                            mCameraSource.start(surface_camera_preview.holder)
                        } else {
                            requestForPermission()
                        }
                    }
                } catch (e: Exception) {
                    toast("Error: ${e.message}")
                }
            }

            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                // No action required for surfaceChanged
            }
        })

        textRecognizer.setProcessor(object : Detector.Processor<TextBlock> {
            override fun release() {}

            override fun receiveDetections(detections: Detector.Detections<TextBlock>) {
                val items = detections.detectedItems

                if (items.size() <= 0 || isTextRecognitionActive) {
                    return
                }

                val stringBuilder = StringBuilder()
                for (i in 0 until items.size()) {
                    val item = items.valueAt(i)
                    stringBuilder.append(item.value)
                    stringBuilder.append("\n")
                }

                tv_result.post {
                    tv_result.text = stringBuilder.toString()
                }
            }
        })
    }

    private fun requestForPermission() {
        ActivityCompat.requestPermissions(
            this@TextToSpeechActivity,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
        if (ContextCompat.checkSelfPermission(
                this@TextToSpeechActivity,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this@TextToSpeechActivity,
                    Manifest.permission.CAMERA
                )
            ) {
                // Handle rationale if needed
            } else {
                ActivityCompat.requestPermissions(
                    this@TextToSpeechActivity,
                    arrayOf(Manifest.permission.CAMERA),
                    MY_PERMISSIONS_REQUEST_CAMERA
                )
            }
        } else {
            requestForPermission()
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this@TextToSpeechActivity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun capturePhoto() {
        if (!textRecognizer.isOperational) {
            Toast.makeText(
                this,
                "Dependencies are not loaded yet...please try again in a few moments!",
                Toast.LENGTH_LONG
            ).show()
            Log.e(tag, "Dependencies are downloading... try again in a few moments.")
            return
        }

        if (isTextRecognitionActive) {
            // Text recognition is already in progress, so ignore the button click
            return
        }

        isTextRecognitionActive = true

        mCameraSource.takePicture(null, object : CameraSource.PictureCallback {
            override fun onPictureTaken(photo: ByteArray) {
                val bitmap = BitmapFactory.decodeByteArray(photo, 0, photo.size)
                val frame = Frame.Builder().setBitmap(bitmap).build()
                val textBlocks = textRecognizer.detect(frame)

                val stringBuilder = StringBuilder()
                for (i in 0 until textBlocks.size()) {
                    val textBlock = textBlocks.valueAt(i)
                    stringBuilder.append(textBlock.value)
                    stringBuilder.append("\n")
                }
                val extractedText = stringBuilder.toString()

                runOnUiThread {
                    if (!isTextRecognitionActive) {
                        tv_result.text = extractedText
                    }
                    surface_camera_preview.background = BitmapDrawable(resources, bitmap)

                    // Reset the isTextRecognitionActive flag after processing the text
                    isTextRecognitionActive = false
                    captureButton.isEnabled = false

                    // Stop the camera
                    mCameraSource.stop()
                }
                textToSpeech.speak(extractedText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        })

    }

    @SuppressLint("MissingPermission")
    private fun resetTextRecognition() {
        tv_result.text = ""
        surface_camera_preview.background = null
        captureButton.isEnabled = true

        // Start the camera again
        if (isCameraPermissionGranted()) {
            mCameraSource.start(surface_camera_preview.holder)
        } else {
            requestForPermission()
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this@TextToSpeechActivity, text, Toast.LENGTH_LONG).show()
    }
    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}