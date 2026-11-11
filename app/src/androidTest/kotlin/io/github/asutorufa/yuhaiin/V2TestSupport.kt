package io.github.asutorufa.yuhaiin

import java.net.HttpURLConnection
import java.net.URL
import io.github.asutorufa.yuhaiin.Constants

/** Calls the core's Android platform API with the WebView's session, as the Web UI does. */
fun platformApi(activity: MainActivity, method: String, path: String, body: String? = null): Pair<Int, String> {
    val session = activity.session.value
    check(session.ready) { "core session is not ready" }
    val conn =
        URL("http://127.0.0.1:${session.port}/android/v1/$path").openConnection() as HttpURLConnection
    return try {
        conn.requestMethod = method
        conn.connectTimeout = 3000
        conn.readTimeout = 10000
        conn.setRequestProperty("Cookie", "${Constants.PLATFORM_COOKIE_NAME}=${session.token}")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Yuhaiin-Platform-Request", "1")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val text =
            (if (code < 400) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        code to text
    } finally {
        conn.disconnect()
    }
}
