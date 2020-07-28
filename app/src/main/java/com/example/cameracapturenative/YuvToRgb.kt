package com.example.cameracapturenative

import android.graphics.ImageFormat
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.Type
import android.util.Log

import android.util.Size
import android.view.Surface

class YuvToRgb(val rs: RenderScript, val dimensions: Size, val msFrame: Int) : Allocation.OnBufferAvailableListener {

    lateinit var inputAllocation: Allocation
    lateinit var outputAllocation: Allocation
    lateinit var outputAllocationInt: Allocation

    lateinit var outBufferInt: IntArray

    val scriptYuv2Rgb: ScriptC_yuv2rgb

    var msLast = 0L

    init {
        Log.d(CameraPluginActivity.TAG, " -------======== CONVERSION INIT A")
        createAllocations()
        inputAllocation.setOnBufferAvailableListener(this)
        Log.d(CameraPluginActivity.TAG, " -------======== CONVERSION INIT B")
        scriptYuv2Rgb = ScriptC_yuv2rgb(rs)
        Log.d(CameraPluginActivity.TAG, " -------======== CONVERSION INIT C")
        scriptYuv2Rgb._gCurrentFrame = inputAllocation
        Log.d(CameraPluginActivity.TAG, " -------======== CONVERSION INIT D")
        scriptYuv2Rgb._gIntFrame = outputAllocationInt
        Log.d(CameraPluginActivity.TAG, " -------======== CONVERSION INIT E")
    }

    fun createAllocations() {
        Log.d(CameraPluginActivity.TAG, " -------======== CREATE ALLOCATIONS A")
        val w = dimensions.width
        val h = dimensions.height

        outBufferInt = IntArray(w * h)

        val builder = Type.Builder(rs, Element.YUV(rs)).setX(w).setY(h).setYuvFormat(ImageFormat.YUV_420_888)
        inputAllocation = Allocation.createTyped(rs, builder.create(), Allocation.USAGE_IO_INPUT or Allocation.USAGE_SCRIPT)

        val rgbType = Type.createXY(rs, Element.RGBA_8888(rs), w, h)
        val intType = Type.createXY(rs, Element.U32(rs), w, h)

        outputAllocation = Allocation.createTyped(rs, rgbType, Allocation.USAGE_IO_OUTPUT or Allocation.USAGE_SCRIPT)
        outputAllocationInt = Allocation.createTyped(rs, intType, Allocation.USAGE_SCRIPT)

        Log.d(CameraPluginActivity.TAG, " -------======== CREATE ALLOCATIONS B")
    }

    fun setOutputSurface(output: Surface) {
        outputAllocation.surface = output
    }

    override fun onBufferAvailable(a: Allocation) {
        Log.d(CameraPluginActivity.TAG, "-------------- ON BUFFER AVAIL -A, a = $a")
        inputAllocation.ioReceive()

        Log.d(CameraPluginActivity.TAG, "-------------- ON BUFFER AVAIL A, a = $a")

        val msCurrent = System.currentTimeMillis()

        Log.d(CameraPluginActivity.TAG, "-------------- ON BUFFER AVAIL B, cms = $msCurrent, is = ${(msCurrent - msLast)} >= $msFrame, is = ${msCurrent - msLast >= msFrame}")

        if(msCurrent - msLast >= msFrame) {
            Log.d(CameraPluginActivity.TAG, "-------------- ON BUFFER AVAIL C")
            scriptYuv2Rgb.forEach_yuv2rgbFrames(outputAllocation)
            outputAllocationInt.copyTo(outBufferInt)
            Log.d(CameraPluginActivity.TAG, "-------------- ON BUFFER AVAIL D")
            msLast = msCurrent
        }
    }

    fun getOutputBuffer() = outBufferInt
    fun getInputSurface() = inputAllocation.surface

}