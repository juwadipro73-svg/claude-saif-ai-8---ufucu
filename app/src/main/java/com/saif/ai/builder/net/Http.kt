package com.saif.ai.builder.net

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * Chhota dependency-free HTTP helper (HttpURLConnection based).
 * Non-2xx par exception nahi phenkta — code check caller karta hai.
 */
object Http {

    private const val USER_AGENT = "SaifAI-Builder-Android/1.0"

    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        connectTimeoutMs: Int = 20_000,
        readTimeoutMs: Int = 45_000,
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                if (!headers.keys.any { it.equals("Content-Type", true) }) {
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { out -> out.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResponse(connection.responseCode, text)
        } finally {
            connection.disconnect()
        }
    }

    /** multipart/form-data POST — APK upload ke liye. */
    fun uploadMultipart(
        url: String,
        file: File,
        fileFieldName: String = "file",
        fields: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val boundary = "----SaifAI${System.currentTimeMillis()}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            connection.outputStream.buffered().use { out ->
                fun writePart(text: String) = out.write(text.toByteArray(Charsets.UTF_8))

                fields.forEach { (key, value) ->
                    writePart("--$boundary\r\n")
                    writePart("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
                    writePart("$value\r\n")
                }
                writePart("--$boundary\r\n")
                writePart(
                    "Content-Disposition: form-data; name=\"$fileFieldName\"; " +
                        "filename=\"${file.name}\"\r\n"
                )
                writePart("Content-Type: application/vnd.android.package-archive\r\n\r\n")
                file.inputStream().use { input -> input.copyTo(out) }
                writePart("\r\n--$boundary--\r\n")
            }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResponse(connection.responseCode, text)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * File download (Google Drive uc-link support ke saath).
     * Bade APK par Drive ek HTML "virus scan" page deta hai — us form ka
     * confirm URL nikaal kar dobara try karte hain.
     */
    fun downloadFile(url: String, destination: File, maxBytes: Long = 250L * 1024 * 1024): File {
        var currentUrl = url
        var attempt = 0

        while (attempt < 4) {
            attempt++
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SaifAI-Builder")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Redirect without Location (HTTP $code)")
                    currentUrl = if (location.startsWith("http")) location
                    else URL(URL(currentUrl), location).toString()
                    continue
                }
                if (code !in 200..299) {
                    throw IOException("APK download failed (HTTP $code)")
                }
                val contentType = connection.contentType.orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) {
                    val html = connection.inputStream.bufferedReader().use { it.readText() }
                    val next = extractDriveConfirmUrl(html)
                        ?: throw IOException("Drive ne HTML page bheja, APK link resolve nahi hua.")
                    currentUrl = next
                    continue
                }
                destination.parentFile?.mkdirs()
                var total = 0L
                connection.inputStream.use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > maxBytes) throw IOException("APK 250 MB se bada hai.")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (total == 0L) throw IOException("APK download empty aaya.")
                return destination
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("APK download ke liye redirects khatam ho gaye.")
    }

    private fun extractDriveConfirmUrl(html: String): String? {
        val action = Regex("action=\"([^\"]+)\"")
            .find(html)?.groupValues?.get(1)
            ?: Regex("href=\"(https://drive\\.google\\.com/uc\\?export=download[^\"]+)\"")
                .find(html)?.groupValues?.get(1)
            ?: return null
        val clean = action.replace("&amp;", "&")
        return if (clean.startsWith("http")) clean
        else "https://drive.google.com$clean"
    }
}
