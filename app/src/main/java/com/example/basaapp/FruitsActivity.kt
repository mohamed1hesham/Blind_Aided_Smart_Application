package com.example.basaapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.basaapp.ml.Fruitsmodel
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.objdetect.CascadeClassifier
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import java.io.FileInputStream
import java.nio.ByteOrder
import java.nio.channels.FileChannel

import android.Manifest
import android.media.ThumbnailUtils
import android.view.View
import android.view.WindowInsets
import android.widget.ImageButton
import androidx.annotation.Nullable
import com.example.basaapp.ml.Fruits
import org.tensorflow.lite.support.model.Model

import java.io.IOException
import java.util.Locale

class FruitsActivity : AppCompatActivity() {
    private lateinit var result: TextView
    private lateinit var imageView: ImageView
    private lateinit var picture: Button
    private val imageSize = 224

    private lateinit var textToSpeech: TextToSpeech
    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fruits)
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
        val myButton: ImageButton = findViewById(R.id.backBtn)
        myButton.setOnClickListener {
            finish()
        }
        result = findViewById(R.id.resultProcess)
        imageView = findViewById(R.id.capturedImg)
        picture = findViewById(R.id.takeImgBtn)

        picture.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, 1)
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            }
        }
    }

    private fun classifyImage(image: Bitmap) {
        try {
            val model = Fruits.newInstance(getApplicationContext())

            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
            byteBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(imageSize * imageSize)
            image.getPixels(intValues, 0, image.width, 0, 0, image.width, image.height)

            var pixel = 0
            for (i in 0 until imageSize) {
                for (j in 0 until imageSize) {
                    val value = intValues[pixel++]
                    byteBuffer.putFloat(((value shr 16) and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat(((value shr 8) and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat((value and 0xFF) * (1f / 255f))
                }
            }

            inputFeature0.loadBuffer(byteBuffer)

            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.getOutputFeature0AsTensorBuffer()

            val confidences = outputFeature0.floatArray
            var maxPos = 0
            var maxConfidence = 0f
            for (i in confidences.indices) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i]
                    maxPos = i
                }
            }
            val classes = arrayOf("fresh apple", "fresh banana", "fresh orange", "rotten apples","rotten banana","rotten orange")
            result.text = classes[maxPos]
            textToSpeech.speak(result.text, TextToSpeech.QUEUE_FLUSH, null, null)
            var s = ""
            for (i in classes.indices) {
                s += String.format("%s: %.1f%%\n", classes[i], confidences[i] * 100)
            }

            model.close()
        } catch (e: IOException) {
            // Handle the exception
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val image = data?.extras?.get("data") as Bitmap
            val dimension = image.width.coerceAtMost(image.height)
            val thumbnail = ThumbnailUtils.extractThumbnail(image, dimension, dimension)
            imageView.setImageBitmap(thumbnail)

            val resizedImage = Bitmap.createScaledBitmap(thumbnail, imageSize, imageSize, false)
            classifyImage(resizedImage)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

}