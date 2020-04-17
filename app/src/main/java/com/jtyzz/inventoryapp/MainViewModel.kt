package com.jtyzz.inventoryapp

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import java.io.File

class MainViewModel: ViewModel() {
    val bitmapStore : LiveData<Bitmap>
        get() = _bitmapStore
    private val _bitmapStore = MutableLiveData<Bitmap>()

    val analyzedText : LiveData<String>
        get() = _analyzedText
    private val _analyzedText = MutableLiveData<String>()

    fun storeBitmap(bitmap: Bitmap){
        _bitmapStore.postValue(bitmap)
    }

    fun setAnalyzedText(visionText: FirebaseVisionText){
        _analyzedText.postValue(visionText.text)
    }


    fun bitmapAnalyze(bitmap: Bitmap){
        val fbVisionImg = FirebaseVisionImage.fromBitmap(bitmap)
        val fbVisionTxtDetect = FirebaseVision.getInstance().onDeviceTextRecognizer

        fbVisionTxtDetect.processImage(fbVisionImg).addOnSuccessListener {
            setAnalyzedText(it)
            Log.d("debug", "Text found")
        }
            .addOnFailureListener {
                Log.d("debug", "No text found")
            }
    }


}