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


}