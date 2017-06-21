package com.cn.tfl.ht_volley.toolbox

import com.cn.tfl.ht_volley.AuthFailureError
import com.cn.tfl.ht_volley.Request
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.ProtocolVersion
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicHttpResponse
import org.apache.http.message.BasicStatusLine
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory


/**
 * Created by Happiness on 2017/5/24.
 * 基于HttpURLConnection
 */
class HurlStack : HttpStack {

    companion object {

        val HEADER_CONTENT_TYPE = "Content-Type"
    }

    private var mUrlReWriter: UrlReWriter? = null
    private var mSslSocketFactory: SSLSocketFactory? = null

    // 用于在使用前转换URL的界面。
    interface UrlReWriter {

        fun reWriteUrl(originalUrl: String): String
    }

    constructor(urlReWriter: UrlReWriter?, sslSocketFactory: SSLSocketFactory?) {
        this.mUrlReWriter = urlReWriter
        this.mSslSocketFactory = sslSocketFactory
    }

    constructor(urlReWriter: UrlReWriter?) : this(urlReWriter, null)

    constructor() : this(null)

    @Throws(IOException::class, AuthFailureError::class)
    override fun performRequest(request: Request<*>, additionalHeaders: Map<String, String>): HttpResponse {
        var url = request.getUrl()
        val map = HashMap<String, String>()
        map.putAll(request.getHeaders())
        map.putAll(additionalHeaders)
        if(mUrlReWriter!=null) {
            var rewritten = mUrlReWriter!!.reWriteUrl(url)
            url = rewritten
        }
        val parsedUrl = URL(url)
        val connection = openConnection(parsedUrl, request)
        for (headerName in map.keys) {
            connection.addRequestProperty(headerName, map[headerName])
        }
        setConnectionParametersForRequest(connection, request)
        // 使用HttpURLConnection的数据初始化HttpResponse。
        val protocolVersion = ProtocolVersion("HTTP", 1, 1)
        val responseCode = connection.responseCode
        if (responseCode === -1) {
            //如果无法检索到响应代码，则由getResponseCode（）返回-1 向呼叫者发出连接发生错误的信号。
            throw IOException("Could not retrieve response code from HttpUrlConnection.")
        }
        val responseStatus = BasicStatusLine(protocolVersion,
                connection.responseCode, connection.responseMessage)
        val response = BasicHttpResponse(responseStatus)
        if (hasResponseBody(request.getMethod(), responseStatus.statusCode)) {
            response.entity = entityFromConnection(connection)
        }
        for ((key, value) in connection.headerFields) {
            if (key != null) {
                val h = BasicHeader(key, value[0])
                response.addHeader(h)
            }
        }
        return response
    }


    /**
     * 检查响应消息是否包含正文。
     * @see [RFC 7230 section 3.3](https://tools.ietf.org/html/rfc7230.section-3.3)

     * @param requestMethod request method
     * *
     * @param responseCode response status code
     * *
     * @return whether the response has a body
     */
    private fun hasResponseBody(requestMethod: Int, responseCode: Int): Boolean {
        return requestMethod != Request.Method.HEAD
                && !(HttpStatus.SC_CONTINUE <= responseCode && responseCode < HttpStatus.SC_OK)
                && responseCode != HttpStatus.SC_NO_CONTENT
                && responseCode != HttpStatus.SC_NOT_MODIFIED
    }

    /**
     * 从给定的[HttpURLConnection]初始化[HttpEntity]。
     * @param connection
     * *
     * @return an HttpEntity populated with data from `connection`.
     */
    private fun entityFromConnection(connection: HttpURLConnection): HttpEntity {
        val entity = BasicHttpEntity()
        var inputStream: InputStream
        try {
            inputStream = connection.inputStream
        } catch (ioe: IOException) {
            inputStream = connection.errorStream
        }

        entity.content = inputStream
        entity.contentLength = connection.contentLength.toLong()
        entity.setContentEncoding(connection.contentEncoding)
        entity.setContentType(connection.contentType)
        return entity
    }

    /**
     * 用参数打开{@link HttpURLConnection}。
     * @param url
     * @return an open connection
     * @throws IOException
     */
    @Throws(IOException::class, AuthFailureError::class)
    fun setConnectionParametersForRequest(connection: HttpURLConnection,
                                          request: Request<*>) {
        when (request.getMethod()) {
            Request.Method.DEPRECATED_GET_OR_POST -> {
                //这是为了向后兼容而需要处理的已弃用的方式。 如果请求的主体为空，则假设请求是GET。否则，假定请求是POST。
                val postBody = request.getBody()
                if (postBody != null) {
                    //准备输出 没有必要明确设置Content-Length，因为这是由HttpURLConnection使用准备的输出流的大小来处理的。
                    connection.doOutput = true
                    connection.requestMethod = "POST"
                    connection.addRequestProperty(HEADER_CONTENT_TYPE,
                            request.getBodyContentType())
                    val out = DataOutputStream(connection.outputStream)
                    out.write(postBody)
                    out.close()
                }
            }
            Request.Method.GET ->
                //不需要设置请求方法，因为连接默认为GET，但在这里是显式的。
                connection.requestMethod = "GET"
            Request.Method.DELETE -> connection.requestMethod = "DELETE"
            Request.Method.POST -> {
                connection.requestMethod = "POST"
                addBodyIfExists(connection, request)
            }
            Request.Method.PUT -> {
                connection.requestMethod = "PUT"
                addBodyIfExists(connection, request)
            }
            Request.Method.HEAD -> connection.requestMethod = "HEAD"
            Request.Method.OPTIONS -> connection.requestMethod = "OPTIONS"
            Request.Method.TRACE -> connection.requestMethod = "TRACE"
            Request.Method.PATCH -> {
                connection.requestMethod = "PATCH"
                addBodyIfExists(connection, request)
            }
            else -> throw IllegalStateException("Unknown method type.")
        }
    }

    /**
     * 为指定的“url”创建一个[HttpURLConnection]。
     */
    @Throws(IOException::class)
    fun createConnection(url: URL): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        // androidM版本的解决方法HttpURLConnection不遵守HttpURLConnection.setFollowRedirects（）属性。
        // https://code.google.com/p/android/issues/detail?id=194495
        connection.instanceFollowRedirects = HttpURLConnection.getFollowRedirects()
        return connection
    }

    /**
     * 用参数打开[HttpURLConnection]。
     * @param url
     * *
     * @return an open connection
     * *
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun openConnection(url: URL, request: Request<*>): HttpURLConnection {
        val connection = createConnection(url)

        val timeoutMs = request.getTimeoutMs()
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.useCaches = false
        connection.doInput = true
        //（如果有）为HTTPS的使用调用者提供的自定义SslSocketFactory
        if ("https" == url.protocol && mSslSocketFactory != null) {
            (connection as HttpsURLConnection).sslSocketFactory = mSslSocketFactory
        }
        return connection
    }

    @Throws(IOException::class, AuthFailureError::class)
    private fun addBodyIfExists(connection: HttpURLConnection, request: Request<*>) {
        val body = request.getBody()
        if (body != null) {
            connection.doOutput = true
            connection.addRequestProperty(HEADER_CONTENT_TYPE, request.getBodyContentType())
            val out = DataOutputStream(connection.outputStream)
            out.write(body)
            out.close()
        }
    }
}