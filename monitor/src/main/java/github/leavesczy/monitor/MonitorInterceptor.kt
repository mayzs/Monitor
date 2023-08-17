package github.leavesczy.monitor

import android.app.Application
import android.content.Context
import android.net.Uri
import github.leavesczy.monitor.db.MonitorDatabase
import github.leavesczy.monitor.db.MonitorHttp
import github.leavesczy.monitor.db.MonitorHttpHeader
import github.leavesczy.monitor.provider.ContextProvider
import github.leavesczy.monitor.provider.NotificationProvider
import github.leavesczy.monitor.utils.ResponseUtils
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.promisesBody
import okio.Buffer

/**
 * @Author: leavesCZY
 * @Date: 2020/10/20 18:26
 * @Desc:
 * @Github：https://github.com/leavesCZY
 */
class MonitorInterceptor(context: Context) : Interceptor {

    init {
        ContextProvider.inject(context = context.applicationContext as Application)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var monitorHttp = buildMonitorHttp(request = request)
        monitorHttp = insert(monitorHttp = monitorHttp)
        NotificationProvider.show(monitorHttp = monitorHttp)
        val response: Response
        try {
            response = chain.proceed(request)
            try {
                monitorHttp = processResponse(
                    response = response,
                    monitorHttp = monitorHttp
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        } catch (e: Throwable) {
            monitorHttp = monitorHttp.copy(error = e.toString())
            throw e
        } finally {
            try {
                update(monitorHttp = monitorHttp)
                NotificationProvider.show(monitorHttp = monitorHttp)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        return response
    }

    private fun buildMonitorHttp(request: Request): MonitorHttp {
        val requestDate = System.currentTimeMillis()
        val requestBody = request.body
        val url = request.url.toString()
        val uri = Uri.parse(url)
        val host = uri.host ?: ""
        val path = (uri.path ?: "") + if (uri.query.isNullOrBlank()) {
            ""
        } else {
            "?" + uri.query
        }
        val scheme = uri.scheme ?: ""
        val method = request.method
        val requestHeaders = request.headers.map {
            MonitorHttpHeader(name = it.first, value = it.second)
        }
        val mRequestBody =
            if (requestBody != null && ResponseUtils.bodyHasSupportedEncoding(request.headers)) {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                if (ResponseUtils.isProbablyUtf8(buffer)) {
                    val charset =
                        requestBody.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                    val content = buffer.readString(charset)
                    content
                } else {
                    ""
                }
            } else {
                ""
            }
        val requestContentLength = requestBody?.contentLength() ?: 0
        val requestContentType = requestBody?.contentType()?.toString() ?: ""
        return MonitorHttp(
            id = 0L,
            url = url,
            host = host,
            path = path,
            scheme = scheme,
            requestDate = requestDate,
            method = method,
            requestHeaders = requestHeaders,
            requestContentLength = requestContentLength,
            requestContentType = requestContentType,
            requestBody = mRequestBody,
            protocol = "",
            responseHeaders = emptyList(),
            responseBody = "",
            responseContentType = "",
            responseContentLength = 0L,
            responseDate = 0L,
            responseTlsVersion = "",
            responseCipherSuite = "",
            responseMessage = "",
            error = null
        )
    }

    private fun processResponse(
        response: Response,
        monitorHttp: MonitorHttp
    ): MonitorHttp {
        val requestHeaders = response.request.headers.map {
            MonitorHttpHeader(name = it.first, value = it.second)
        }
        val responseHeaders = response.headers.map {
            MonitorHttpHeader(name = it.first, value = it.second)
        }
        val responseBody = response.body
        val responseContentType: String
        var responseContentLength = 0L
        var mResponseBody = "(encoded body omitted)"
        if (responseBody != null) {
            responseContentType = responseBody.contentType()?.toString() ?: ""
            responseContentLength = responseBody.contentLength()
            if (response.promisesBody()) {
                val encodingIsSupported = ResponseUtils.bodyHasSupportedEncoding(response.headers)
                if (encodingIsSupported) {
                    val buffer = ResponseUtils.getNativeSource(response)
                    responseContentLength = buffer.size
                    if (ResponseUtils.isProbablyUtf8(buffer)) {
                        if (responseBody.contentLength() != 0L) {
                            val charset = responseBody.contentType()?.charset(Charsets.UTF_8)
                                ?: Charsets.UTF_8
                            mResponseBody = buffer.clone().readString(charset)
                        }
                    }
                }
            }
        } else {
            responseContentType = ""
        }
        return monitorHttp.copy(
            requestDate = response.sentRequestAtMillis,
            responseDate = response.receivedResponseAtMillis,
            protocol = response.protocol.toString(),
            responseCode = response.code,
            responseMessage = response.message,
            responseTlsVersion = response.handshake?.tlsVersion?.javaName ?: "",
            responseCipherSuite = response.handshake?.cipherSuite?.javaName ?: "",
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders,
            responseContentType = responseContentType,
            responseContentLength = responseContentLength,
            responseBody = mResponseBody
        )
    }

    private fun insert(monitorHttp: MonitorHttp): MonitorHttp {
        val id = MonitorDatabase.instance.monitorDao.insert(model = monitorHttp)
        return monitorHttp.copy(id = id)
    }

    private fun update(monitorHttp: MonitorHttp) {
        MonitorDatabase.instance.monitorDao.update(model = monitorHttp)
    }

}