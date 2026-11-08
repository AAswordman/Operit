package com.k2fsa.sherpa.onnx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OfflineTtsCallbackTest {
    @Test
    fun callbackExposesJniSignatureAndPreservesCancellation() {
        val samples = floatArrayOf(0.25f, -0.5f)
        var keepGenerating = true
        val callback = OfflineTtsCallback { received ->
            assertSame(samples, received)
            if (keepGenerating) 1 else 0
        }
        // 原生层查找的是具类型的方法和装箱 Integer 返回值。
        val invoke = callback.javaClass.getMethod("invoke", FloatArray::class.java)
        assertEquals(Integer::class.java, invoke.returnType)
        assertEquals(1, invoke.invoke(callback, samples))
        keepGenerating = false
        assertEquals(0, invoke.invoke(callback, samples))
    }
}
