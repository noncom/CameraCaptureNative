package com.example.cameracapturenative

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.opengl.GLES30
import android.os.Build
import android.os.Bundle
import android.os.HandlerThread
import android.renderscript.RenderScript
import android.util.Log
import android.util.Size
import android.view.Surface
import com.unity3d.player.UnityPlayerActivity
import java.nio.IntBuffer

class CameraPluginActivity : UnityPlayerActivity() {

    val TAG = "CameraPluginActivity"

    var isUpdate = false

    var size = Size(640, 480)

    val maxImages = 4
    val conversionFramerate = 60

    lateinit var cameraDevice: CameraDevice
    lateinit var captureSession: CameraCaptureSession
    lateinit var previewSurface: Surface
    lateinit var imagePreviewReader: ImageReader
    lateinit var renderScript: RenderScript

    lateinit var conversionScript: YuvToRgb

    var handlerThread : HandlerThread? = null

    companion object {

        @JvmField var INSTANCE : CameraPluginActivity? = null

        val TAG = "CameraPluginActivity"

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("NativeCameraPlugin")
        }
    }

    init {
        INSTANCE = this
    }

    /** --------------------------------------------------------------------------------------------
     *     JNI Calls
     * -------------------------------------------------------------------------------------------*/

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    external fun nativeInit()

    external fun nativeRelease()

    /** --------------------------------------------------------------------------------------------
     *     Android Lifecycle
     * -------------------------------------------------------------------------------------------*/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)

        // Example of a call to a native method
        //sample_text.text = stringFromJNI()

        nativeInit()
        renderScript = RenderScript.create(this)
    }

    override fun onPause() {
        handlerThread?.let { thread ->
            thread.quitSafely()
            thread.join()
            handlerThread = null
        }
        stopCamera()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        handlerThread = HandlerThread(TAG)
        handlerThread?.start()
        startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        nativeRelease()
        INSTANCE = null
    }

    /** --------------------------------------------------------------------------------------------
     *     Texture Update
     * -------------------------------------------------------------------------------------------*/

    /** called from NDK to update the texture in Unity */

    fun requestJavaRendering(texturePointer: Int) {
        LogD("!!!!!!!!!!!!! request java rendering A")
        if (!isUpdate) {
            return
        }

        val imageBuffer = conversionScript.getOutputBuffer()

        LogD("!!!!!!!!!!!!! request java rendering B, ${imageBuffer.size}")

        if (imageBuffer.size > 1) {
            LogD("!!!!!!!!!!!!! request java rendering C, pointer = ${Integer.toHexString(texturePointer)}")
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texturePointer)
            GLES30.glTexSubImage2D(
                    GLES30.GL_TEXTURE_2D, 0, 0, 0,
                    size.width, size.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
                    IntBuffer.wrap(imageBuffer))
        }
    }

    /** --------------------------------------------------------------------------------------------
     *     Sync with Unity
     * -------------------------------------------------------------------------------------------*/

    /** called from Unity when it's ready to work with the texture */
    fun enablePreviewUpdater(update: Boolean) {
        isUpdate = update
    }

    /** --------------------------------------------------------------------------------------------
     *     Camera and Session Callbacks
     * -------------------------------------------------------------------------------------------*/

    val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            LogD("------------ CAMERA STATE OPENED CALLBACK A $camera")
            cameraDevice = camera
            setupCameraDevice()
            LogD("------------ CAMERA STATE OPENED CALLBACK B")
        }

        override fun onDisconnected(camera: CameraDevice) {
            LogW("NativeCamera: camera disconnected")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            LogE("NativeCamera: camera error=${error}")
        }
    }

    val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            try {
                LogD("------------ SESSION STATE CALLBACK A $session")
                session.setRepeatingRequest(createCaptureRequest(), null, null)
                LogD("------------ SESSION STATE CALLBACK B")
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            LogE("NativeCamera Session: configuration failed")
        }
    }

    /** --------------------------------------------------------------------------------------------
     *     Camera Management
     * -------------------------------------------------------------------------------------------*/

    fun setupCameraDevice() {
        try {
            if (previewSurface != null) {
                cameraDevice.createCaptureSession(mutableListOf(previewSurface), sessionStateCallback, null)
            } else {
                LogE("Failed creating preview surface")
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun createCaptureRequest(): CaptureRequest {
        LogD("------------ CREATE CAPTURE REQUEST A")
        val builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        LogD("------------ CREATE CAPTURE REQUEST B")
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        LogD("------------ CREATE CAPTURE REQUEST C")
        builder.addTarget(previewSurface)
        val request = builder.build()
        LogD("------------ CREATE CAPTURE REQUEST D $request")
        return request
    }

    fun startCamera() {
        var manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            LogD("==========[START CAMERA] --- A ")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            LogD("==========[START CAMERA] --- B ")

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                var chosenCamera = getCamera(manager)

                LogD("==========[START CAMERA] --- C-1 $chosenCamera")

                manager.openCamera(chosenCamera, cameraStateCallback, null)

                LogD("==========[START CAMERA] --- C-2 $chosenCamera")
                imagePreviewReader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, maxImages)

                LogD("==========[START CAMERA] --- C-3 $chosenCamera")
                conversionScript = YuvToRgb(renderScript, size, conversionFramerate)
                LogD("==========[START CAMERA] --- C-4 $chosenCamera")
                conversionScript.setOutputSurface(imagePreviewReader.surface)
                LogD("==========[START CAMERA] --- C-5 $chosenCamera")
                previewSurface = conversionScript.getInputSurface()

                LogD("==========[START CAMERA] --- D ")
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun stopCamera() {
        try {
            captureSession.abortCaptures()
            captureSession.close()
            val image = imagePreviewReader.acquireLatestImage()
            image?.close()
        } catch(e: Exception) {
            e.printStackTrace()
        } finally {
            imagePreviewReader?.close()
        }

        cameraDevice?.close()
    }

    fun getCamera(manager: CameraManager): String {
        return manager.cameraIdList.first { cameraId ->
            manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            //manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }


    /** --------------------------------------------------------------------------------------------
     *     Debug
     * -------------------------------------------------------------------------------------------*/

    val isDebug = false

    fun LogD(text: String) {
        if(isDebug) {
            Log.d(TAG, text)
        }
    }

    fun LogW(text: String) {
        if(isDebug) {
            Log.w(TAG, text)
        }
    }

    fun LogE(text: String) {
        if(isDebug) {
            Log.e(TAG, text)
        }
    }


}
