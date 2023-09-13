package com.example.basaapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.basaapp.ml.YoloCommonObjects
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.WindowInsets
import android.widget.ImageButton
import java.util.Locale

class CommonThingsActivity : AppCompatActivity() {
    private lateinit var labels: List<String>
    private val colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    private val paint = Paint()
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var bitmap: Bitmap
    private lateinit var imageView: ImageView
    private lateinit var imageView2: ImageView
    private lateinit var cameraDevice: CameraDevice
    private lateinit var handler: Handler
    private lateinit var cameraManager: CameraManager
    private lateinit var textureView: TextureView
    private lateinit var model: YoloCommonObjects
    private lateinit var predictionTextView: TextView
    private lateinit var textToSpeech: TextToSpeech

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
        setContentView(R.layout.activity_common_things)
        val myButton: ImageButton = findViewById(R.id.backBtn)
        myButton.setOnClickListener {
            finish()
        }
        getPermission()
        val stopButton: Button = findViewById(R.id.takeImgBtn)
        stopButton.setOnClickListener {
            stopTextureViewAndImageView()
            textToSpeech.speak(predictionTextView.text, TextToSpeech.QUEUE_FLUSH, null, null)
            stopButton.isEnabled = false
        }
        val resetButton: Button = findViewById(R.id.resetBtn)
        resetButton.setOnClickListener {
            resetTextureViewAndImageView()
            stopButton.isEnabled = true
        }
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = YoloCommonObjects.newInstance(this)
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)
        predictionTextView = findViewById(R.id.resultProcess)

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }


            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width
                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f
                var x = 0
                val predictedLabels = mutableListOf<String>()
                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {
                        paint.color = colors[index]
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(
                            RectF(
                                locations[x + 1] * w,
                                locations[x] * h,
                                locations[x + 3] * w,
                                locations[x + 2] * h
                            ),
                            paint
                        )
                        paint.style = Paint.Style.FILL
                        val label = labels[classes[index].toInt()]
                        val prediction = "$label ${fl.toString()}"
                        predictedLabels.add(prediction)
                        canvas.drawText(
                            prediction,
                            locations[x + 1] * w,
                            locations[x] * h,
                            paint
                        )
                    }
                }

                imageView.setImageBitmap(mutable)

                runOnUiThread {
                    val filteredLabels = predictedLabels.map { it.replace(Regex("\\d+"), "") }
                    predictionTextView.text = filteredLabels.joinToString("\n")
                }
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                val surfaceTexture = textureView.surfaceTexture
                val surface = Surface(surfaceTexture)

                val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(p0: CameraCaptureSession) {
                            p0.setRepeatingRequest(captureRequest.build(), null, null)
                        }

                        override fun onConfigureFailed(p0: CameraCaptureSession) {
                        }
                    },
                    handler
                )
            }

            override fun onDisconnected(p0: CameraDevice) {}

            override fun onError(p0: CameraDevice, p1: Int) {}
        }, handler)
    }

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            getPermission()
        }
    }
    private fun stopTextureViewAndImageView() {
        // Remove the textureView's surface texture listener
        textureView.surfaceTextureListener = null

        // Capture the current frame from the TextureView
        val bitmap = textureView.bitmap
        // Display the captured image in the ImageView
        imageView.setImageBitmap(bitmap)
    }
    private fun resetTextureViewAndImageView() {
        // Show the TextureView and ImageView
        textureView.visibility = View.VISIBLE
        imageView.visibility = View.VISIBLE


        // Set the surfaceTexture listener again
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width
                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f
                var x = 0
                val predictedLabels = mutableListOf<String>()
                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {
                        paint.color = colors[index]
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(
                            RectF(
                                locations[x + 1] * w,
                                locations[x] * h,
                                locations[x + 3] * w,
                                locations[x + 2] * h
                            ),
                            paint
                        )
                        paint.style = Paint.Style.FILL
                        val label = labels[classes[index].toInt()]
                        val prediction = "$label ${fl.toString()}"
                        predictedLabels.add(prediction)
                        canvas.drawText(
                            prediction,
                            locations[x + 1] * w,
                            locations[x] * h,
                            paint
                        )
                    }
                }

                imageView.setImageBitmap(mutable)

                runOnUiThread {
                    val filteredLabels = predictedLabels.map { it.replace(Regex("\\d+"), "") }
                    predictionTextView.text = filteredLabels.joinToString("\n")
                }
            }
        }
    }
}