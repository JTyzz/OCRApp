package com.jtyzz.inventoryapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class ScanViewModel: ViewModel() {

    private val executor: Executor by lazy { Executors.newSingleThreadExecutor() }

    val analyzedName : LiveData<String>
        get() = _analyzedName
    private val _analyzedName = MutableLiveData<String>()

    val analyzedNumber : LiveData<String>
        get() = _analyzedNumber
    private val _analyzedNumber = MutableLiveData<String>()

    val roadTypes : LiveData<Array<String>>
        get() = _roadTypes
    private val _roadTypes = MutableLiveData<Array<String>>()

    fun mSetAnalyzedName(name: String){
        _analyzedName.postValue(name)
    }

    fun mSetAnalyzedNumber(number: String){
        _analyzedNumber.postValue(number)
    }

    fun mSetRoadTypes(types: Array<String>){
        _roadTypes.postValue(types)
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
                mSetAnalyzedName("No text found")
            }
    }

    private fun processResultText(resultText: FirebaseVisionText) {
        Log.i("scan", resultText.text)
        val blockArray = arrayListOf<String>()
        val lineArray = arrayListOf<String>()
        if (resultText.textBlocks.size == 0) {
            mSetAnalyzedName("No text found")
            return
        }
        for (block in resultText.textBlocks) {
            blockArray.add(block.text.toUpperCase(Locale.US))
            for (line in block.lines){
                lineArray.add(line.text.toUpperCase(Locale.US))
            }

        }
        nameScan(lineArray)
        for (i in blockArray) {
            if (i.contains("TRACKING #")) {
                val number = i.substringAfterLast(":")
                mSetAnalyzedNumber(number)
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

    fun nameScan(list: ArrayList<String>){
        val resultString = ""
        for(i in list){
            val chars = listOf("1", "2", "3", "4","5","6","7","8","9","0")
            if (i.findAnyOf(roadTypes.value!!.toList(), 0, true) != null && Character.isDigit(i[0])){
                Log.d("debug", "${i.findAnyOf(roadTypes.value!!.toList())}")
                val pIndex = list.indexOf(i) - 1
                val lineList = list[pIndex].split("\\s".toRegex())
                val name = lineList.takeLast(2).toString()
                Log.d("debug", "name: $name")
                resultString.plus(name)
            }
        }
        mSetAnalyzedName(resultString)
    }
}