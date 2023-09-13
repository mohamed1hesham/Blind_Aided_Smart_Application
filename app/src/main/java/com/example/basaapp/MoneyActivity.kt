package com.example.basaapp

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.basaapp.ml.TfliteModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import android.speech.tts.TextToSpeech
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import java.util.Locale
import android.Manifest
import android.media.ThumbnailUtils
import com.example.basaapp.ml.ModelUnquant
import java.io.IOException
import java.nio.ByteOrder
class MoneyActivity : AppCompatActivity() {
    private lateinit var result: TextView
    private lateinit var confidence: TextView
    private lateinit var imageView: ImageView
    private lateinit var picture: Button
    private val imageSize = 224
    private lateinit var textToSpeech: TextToSpeech
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars())
        setContentView(R.layout.activity_money)
        val myButton: ImageButton = findViewById(R.id.backBtn)
        myButton.setOnClickListener {
            finish()
        }
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
        result = findViewById(R.id.resultProcess)
        //confidence = findViewById(R.id.confidence)
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
            val model = ModelUnquant.newInstance(getApplicationContext())

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
            val classes = arrayOf("one pound", "five pounds", "ten pounds new", "ten pounds old","twenty pounds","fifty pounds","hundred pound","two hundred pounds")
            result.text = classes[maxPos]
            textToSpeech.speak(result.text, TextToSpeech.QUEUE_FLUSH, null, null)
            var s = ""
            for (i in classes.indices) {
                s += String.format("%s: %.1f%%\n", classes[i], confidences[i] * 100)
            }
            //confidence.text = s

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