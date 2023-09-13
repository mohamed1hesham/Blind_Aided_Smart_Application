package com.example.basaapp

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageButton
import androidx.annotation.RequiresApi

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import android.speech.tts.TextToSpeech
import android.view.SurfaceView
import android.widget.ImageView
import android.widget.TextView
import com.example.basaapp.ml.LastEmotionsModel
import java.lang.Exception
import java.util.Locale

class EmotionsActivity : AppCompatActivity() {
    private lateinit var CapImage: ImageView
    private lateinit var tv_result: TextView
    private lateinit var captureButton: Button
    private lateinit var textToSpeech: TextToSpeech
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars())
        setContentView(R.layout.activity_emotions)
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

        OpenCVLoader.initDebug()
        CapImage = findViewById(R.id.capturedImg)
        tv_result = findViewById(R.id.resultProcess)
        captureButton = findViewById(R.id.takeImgBtn)

        captureButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED){
                takePicPreview.launch(null)
            }else{
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }




    }
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted){
            takePicPreview.launch(null)
        }else{
            Toast.makeText(this,"Permission Denied Try Again",Toast.LENGTH_SHORT).show()
        }
    }
    private val takePicPreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap->
        CapImage.setImageBitmap(bitmap)
        if (bitmap != null) {
            outputGenerator(bitmap)
        }

    }

    private fun outputGenerator(bitmap: Bitmap) {
        try {
            val faces = detectFaces(bitmap)
            if (faces.isEmpty()) {
                Toast.makeText(this, "No faces detected", Toast.LENGTH_SHORT).show()
                return
            }
            val model = LastEmotionsModel.newInstance(this)
            for (face in faces) {
                // Crop the face region from the original bitmap
                val croppedBitmap = Bitmap.createBitmap(bitmap, face.x, face.y, face.width, face.height)

                // Resize the input image to match the model input size (224x224)
                val resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, 224, 224, true)

                // Convert the Bitmap to a TensorFlow Lite compatible input array
                val finalImage = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4) // 4 bytes per float
                finalImage.order(ByteOrder.nativeOrder())
                val intValues = IntArray(224 * 224)
                resizedBitmap.getPixels(
                    intValues,
                    0,
                    resizedBitmap.width,
                    0,
                    0,
                    resizedBitmap.width,
                    resizedBitmap.height
                )
                var pixel = 0
                for (y in 0 until 224) {
                    for (x in 0 until 224) {
                        val value = intValues[pixel++]
                        // Normalize the pixel value to [0, 1]
                        val normalizedValue = (value and 0xFF) / 255.0f
                        // Set the pixel values in the ByteBuffer
                        finalImage.putFloat(normalizedValue)
                        finalImage.putFloat(normalizedValue)
                        finalImage.putFloat(normalizedValue)
                    }
                }
                // Prepare the ByteBuffer for reading
                finalImage.rewind()

                // Create a TensorBuffer object from the ByteBuffer
                val inputFeature0 =
                    TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
                inputFeature0.loadBuffer(finalImage)

                // Run model inference and get the result
                val outputs = model.process(inputFeature0)
                val outputFeature0 = outputs.outputFeature0AsTensorBuffer

                // Release model resources if no longer used
                model.close()

                // Get the predicted class label
                val classMap = mapOf(
                    0 to "Angry Face",
                    1 to "Disgust Face",
                    2 to "Fear Face",
                    3 to "Happy Face",
                    4 to "Neutral Face",
                    5 to "Sad Face",
                    6 to "Surprise Face"
                )

                val outputArray = outputFeature0.floatArray
                val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: -1
                val maxClass = classMap[maxIndex] ?: "Unknown Class"
                tv_result.text = maxClass
                // Speak out the result using TextToSpeech
                textToSpeech.speak(maxClass, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }catch (e: Exception){

        }
    }



    private fun detectFaces(bitmap: Bitmap): List<Rect> {
        val cascadeClassifier = CascadeClassifier(getCascadeClassifierPath())
        if (cascadeClassifier.empty()) {
            Log.e("TAG", "Failed to load cascade classifier")
            return emptyList()
        }

        val grayMat = Mat()
        val faces = MatOfRect()

        // Convert the Bitmap to a grayscale OpenCV Mat
        val tempMat = Mat(bitmap.width, bitmap.height, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, tempMat)
        Imgproc.cvtColor(tempMat, grayMat, Imgproc.COLOR_BGR2GRAY)
        // Detect faces
        cascadeClassifier.detectMultiScale(grayMat, faces)
        return faces.toList()
    }

    private fun getCascadeClassifierPath(): String {
        val cascadeDir = getDir("cascade", Context.MODE_PRIVATE)
        val cascadeFile = File(cascadeDir, "haarcascade_frontalface_default.xml")

        if (!cascadeFile.exists()) {
            try {
                val inputStream = resources.openRawResource(R.raw.haarcascade_frontalface_default)
                val outputStream = FileOutputStream(cascadeFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                inputStream.close()
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return cascadeFile.absolutePath
    }
    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }


}