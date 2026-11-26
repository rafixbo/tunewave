package com.rafixbo.tunewave.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

/** OkHttp-backed NewPipe Downloader. No login, no cookies, no API key —
 *  plain unauthenticated requests with a normal Android user-agent. */
class OkHttpDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) : Downloader() {

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val builder = okhttp3.Request.Builder().url(request.url())
        for ((name, values) in request.headers()) {
            for (value in values) builder.addHeader(name, value)
        }
        val data = request.dataToSend()
        val body = if (data != null && method != "GET" && method != "HEAD") {
            data.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
        } else {
            null
        }
        builder.method(method, body)
        val call = client.newCall(builder.build())
        val response = call.execute()
        val responseBody = try {
            response.body?.string() ?: ""
        } catch (e: IOException) {
            ""
        }
        val headers = mutableMapOf<String, List<String>>()
        for (name in response.headers.names()) {
            headers[name] = response.headers.values(name)
        }
        return Response(
            response.code,
            response.message,
            headers,
            responseBody,
            request.url(),
        )
    }
}
