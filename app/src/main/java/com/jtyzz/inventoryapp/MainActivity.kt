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
import java.io.File
import java.lang.Integer.max
import java.lang.Integer.min
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.time.measureTime

// This is an arbitrary number we are using to keep track of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts.
class MainActivity : AppCompatActivity(), LifecycleOwner {
    private lateinit var viewModel: MainViewModel

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

        btn_analyze_picture.setOnClickListener {
            startRecognizing(it)
        }

        requestPermissions()
        setClickListeners()
    }


    private fun setClickListeners() {
        btn_take_picture.setOnClickListener { takePicture() }
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

    private fun startRecognizing(v: View) {
        if (preview_image.drawable != null) {
            picture_tv.text = ""
            v.isEnabled = false
            val bitmap = (preview_image.drawable as BitmapDrawable).bitmap
            val image = FirebaseVisionImage.fromBitmap(bitmap)
            val detector = FirebaseVision.getInstance().onDeviceTextRecognizer

            detector.processImage(image)
                .addOnSuccessListener {
                    v.isEnabled = true
                    processResultText(it)
                }
                .addOnFailureListener {
                    v.isEnabled = true
                    picture_tv.text = "Failed"
                }
        } else {
            Toast.makeText(this, "Select an image first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processResultText(resultText: FirebaseVisionText) {
        if (resultText.textBlocks.size == 0) {
            picture_tv.text = "No text found"
            return
        }
        for (block in resultText.textBlocks) {
            val blockText = block.text
            picture_tv.append(blockText + "\n")
        }
    }

    private fun takePicture() {
        disableActions()
        savePictureToMemory()
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
                        runOnUiThread {
                            preview_image.visibility = View.VISIBLE
                            preview_image.setImageBitmap(bitmap)
                            enableActions()
                        }
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

        }.build()

        return Preview(previewConfig)
    }

    private fun createCaptureUseCase(): ImageCapture {
        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .apply {
                setLensFacing(lensFacing)
                setTargetRotation(view_finder.display.rotation)
                setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
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

    private fun disableActions() {
        btn_take_picture.isClickable = false
    }

    private fun enableActions() {
        btn_take_picture.isClickable = true
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