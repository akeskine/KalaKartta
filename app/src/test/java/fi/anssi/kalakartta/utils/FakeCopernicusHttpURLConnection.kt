package fi.anssi.kalakartta.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

internal class FakeCopernicusHttpURLConnection(
    url: URL,
    private val responseCodeValue: Int,
    private val responseBytes: ByteArray
) : HttpURLConnection(url) {
    private val requestBytes = ByteArrayOutputStream()

    val requestBody: ByteArray
        get() = requestBytes.toByteArray()

    override fun connect() {
        connected = true
    }

    override fun disconnect() {
        connected = false
    }

    override fun usingProxy(): Boolean = false

    override fun getOutputStream(): OutputStream = requestBytes

    override fun getInputStream(): InputStream = ByteArrayInputStream(responseBytes)

    override fun getErrorStream(): InputStream = ByteArrayInputStream(responseBytes)

    override fun getResponseCode(): Int = responseCodeValue
}