package com.jtyzz.inventoryapp

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.Image
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.lang.Integer.max
import java.lang.Integer.min
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.system.measureTimeMillis
import kotlin.time.measureTime

class MainActivity : AppCompatActivity(), LifecycleOwner {
    private lateinit var viewModel : MainViewModel

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 14

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
        )
    }

    private val executor: Executor by lazy { Executors.newSingleThreadExecutor() }

    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraX.LensFacing.BACK


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        viewModel.analyzedName.observe(this, Observer {
            name_tv.text = it
        })
        viewModel.analyzedNumber.observe(this, Observer {
            tracking_tv.text = it
        })

        btn_analyze_picture.setOnClickListener {
            savePictureToMemory()
//            startRecognizing(it)
        }

        requestPermissions()

    }

    private fun requestPermissions() {
        if (allPermissionsGranted()) {
            view_finder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startRecognizing(bitmap: Bitmap) {

        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().onDeviceTextRecognizer

        detector.processImage(image)
            .addOnSuccessListener {
                processResultText(it)
            }
            .addOnFailureListener {
                viewModel.setAnalyzedName("No text found")
            }
    }

    private fun processResultText(resultText: FirebaseVisionText) {
        Log.i("scan", resultText.text)
        val blockArray = arrayListOf<String>()
        if (resultText.textBlocks.size == 0) {
            viewModel.setAnalyzedName("No text found")
            return
        }
        for (block in resultText.textBlocks) {
            blockArray.add(block.text.toUpperCase(Locale.US))
        }
        for (i in blockArray) {

            if (i.contains("TRACKING #")) {
                val number = i.substringAfterLast(":")
//                val number = i.removeRange(0, 12)
                viewModel.setAnalyzedNumber(number)
            }
            if (i.contains("SHIP TO:")) {
                val nextIndex = blockArray.indexOf(i) + 1
                val nameArray = blockArray[nextIndex].split("\n")
                val name = nameArray[0]
                viewModel.setAnalyzedName(name)
            }
        }
    }

    private fun savePictureToMemory() {
        imageCapture?.takePicture(executor,
            object : ImageCapture.OnImageCapturedListener() {
                override fun onError(
                    error: ImageCapture.ImageCaptureError,
                    message: String, exc: Throwable?
                ) {
                    Toast.makeText(
                        applicationContext, "Uh oh!",
                        Toast.LENGTH_SHORT
                    ).show()
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

    private fun startCamera() {
        CameraX.unbindAll()

        val preview = createPreviewUseCase()

        preview.setOnPreviewOutputUpdateListener {

            val parent = view_finder.parent as ViewGroup
            parent.removeView(view_finder)
            parent.addView(view_finder, 0)

            view_finder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        imageCapture = createCaptureUseCase()
        CameraX.bindToLifecycle(this, preview, imageCapture)
    }

    private fun createPreviewUseCase(): Preview {
        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            setTargetRotation(view_finder.display.rotation)
            setTargetAspectRatio(AspectRatio.RATIO_4_3)

        }.build()

        return Preview(previewConfig)
    }

    private fun createCaptureUseCase(): ImageCapture {
        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .apply {
                setLensFacing(lensFacing)
                setTargetRotation(view_finder.display.rotation)
                setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            }
        return ImageCapture(imageCaptureConfig.build())
    }


    private fun updateTransform() {
        val matrix = Matrix()

        val centerX = view_finder.width / 2f
        val centerY = view_finder.height / 2f

        val rotationDegrees = when (view_finder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        view_finder.setTransform(matrix)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                view_finder.post { startCamera() }
            } else {
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}