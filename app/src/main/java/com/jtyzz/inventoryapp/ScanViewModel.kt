package com.jtyzz.inventoryapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.Image
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import java.io.File
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class ScanViewModel: ViewModel() {

    private val executor: Executor by lazy { Executors.newSingleThreadExecutor() }

    val bitmapStore : LiveData<Bitmap>
        get() = _bitmapStore
    private val _bitmapStore = MutableLiveData<Bitmap>()

    val analyzedName : LiveData<String>
        get() = _analyzedName
    private val _analyzedName = MutableLiveData<String>()

    val analyzedNumber : LiveData<String>
        get() = _analyzedNumber
    private val _analyzedNumber = MutableLiveData<String>()

    fun storeBitmap(bitmap: Bitmap){
        _bitmapStore.postValue(bitmap)
    }

    fun setAnalyzedName(name: String){
        _analyzedName.postValue(name)
    }

    fun setAnalyzedNumber(number: String){
        _analyzedNumber.postValue(number)
    }

    fun savePictureToMemory(imageCapture: ImageCapture){
        imageCapture.takePicture(executor,
        object : ImageCapture.OnImageCapturedListener(){
            override fun onError(
                error: ImageCapture.ImageCaptureError,
                message: String, exc: Throwable?
            ) {
                Log.d("debug", "ImageNotTaken: $message")
            }

            override fun onCaptureSuccess(
                imageProxy: ImageProxy?,
                rotationDegrees: Int
            ) {
                imageProxy?.image?.let {
                    val bitmap = rotateImage(
                        imageToBitmap(it),
                        rotationDegrees.toFloat()
                    )
                    startRecognizing(bitmap)
                }
                super.onCaptureSuccess(imageProxy, rotationDegrees)
            }
        })
    }

    private fun startRecognizing(bitmap: Bitmap) {

        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().onDeviceTextRecognizer

        detector.processImage(image)
            .addOnSuccessListener {
                processResultText(it)
            }
            .addOnFailureListener {
                setAnalyzedName("No text found")
            }
    }

    private fun processResultText(resultText: FirebaseVisionText) {
        Log.i("scan", resultText.text)
        val blockArray = arrayListOf<String>()
        if (resultText.textBlocks.size == 0) {
            setAnalyzedName("No text found")
            return
        }
        for (block in resultText.textBlocks) {
            blockArray.add(block.text.toUpperCase(Locale.US))
        }
        for (i in blockArray) {

            if (i.contains("TRACKING #")) {
                val number = i.substringAfterLast(":")
                setAnalyzedNumber(number)
            }
            if (i.contains("SHIP TO:")) {
                val nextIndex = blockArray.indexOf(i) + 1
                val nameArray = blockArray[nextIndex].split("\n")
                val name = nameArray[0]
                setAnalyzedName(name)
            }
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
    }
}