package vn.homepanel

import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import vn.homepanel.ha.HaFailure
import vn.homepanel.ha.HaSession

class CameraStreamTest {
    private fun jpeg(vararg body: Int) = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + body.map { it.toByte() }.toByteArray() + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    @Test fun framesAreSplitAtJpegMarkersIgnoringMultipartHeaders() {
        val a = jpeg(1, 2, 3); val b = jpeg(4)
        val stream = Buffer().writeUtf8("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: 7\r\n\r\n").write(a)
            .writeUtf8("\r\n--frame\r\nContent-Type: image/jpeg\r\n\r\n").write(b).writeUtf8("\r\n--frame")
        assertArrayEquals(a, HaSession.nextJpeg(stream, 1000))
        assertArrayEquals(b, HaSession.nextJpeg(stream, 1000))
        assertNull(HaSession.nextJpeg(stream, 1000))
    }

    @Test(expected = HaFailure::class) fun oversizedFrameIsRejected() {
        HaSession.nextJpeg(Buffer().write(jpeg(*IntArray(50))), 20)
    }
}
