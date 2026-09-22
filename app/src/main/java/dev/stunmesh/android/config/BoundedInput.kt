package dev.stunmesh.android.config

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun InputStream.readBounded(max: Int = StrictDocument.MAX_BYTES): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        require(out.size() + n <= max) { "Input too large" }
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
