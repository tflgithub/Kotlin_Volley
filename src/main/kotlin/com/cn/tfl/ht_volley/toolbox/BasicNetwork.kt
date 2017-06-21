package com.cn.tfl.ht_volley.toolbox

import android.os.SystemClock
import com.cn.tfl.ht_volley.*
import org.apache.http.Header
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.impl.cookie.DateUtils
import java.io.IOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.util.*


/**
 * Created by Happiness on 2017/5/22.
 */
//通过{@link HttpStack}执行排序请求的网络。
class BasicNetwork : Network {

    companion object {

        val SLOW_REQUEST_THRESHOLD_MS = 3000

        val DEFAULT_POOL_SIZE = 4096
    }

    var mHttpStack: HttpStack? = null

    var mPool: ByteArrayPool? = null

    constructor(httpStack: HttpStack, pool: ByteArrayPool) {
        this.mHttpStack = httpStack
        this.mPool = pool
    }

    @JvmOverloads constructor(httpStack: HttpStack) : this(httpStack, ByteArrayPool(DEFAULT_POOL_SIZE))

    override fun performRequest(request: Request<*>): NetworkResponse {
        var requestStart = SystemClock.elapsedRealtime()
        while (true) {
            var httpResponse: HttpResponse? = null
            var responseContents: ByteArray? = null
            var responseHeaders = Collections.emptyMap<String, String>()
            try {
                val headers = HashMap<String, String>()
                addCacheHeaders(headers, request.getCacheEntry())
                httpResponse = mHttpStack!!.performRequest(request, headers)
                val statusLine = httpResponse.statusLine
                val statusCode = statusLine.statusCode

                responseHeaders = convertHeaders(httpResponse.allHeaders)

                // 处理缓存验证。
                if (statusCode === HttpStatus.SC_NOT_MODIFIED) {

                    val entry = request.getCacheEntry() ?: return NetworkResponse(HttpStatus.SC_NOT_MODIFIED, null,
                            responseHeaders, true,
                            SystemClock.elapsedRealtime() - requestStart)

                    // HTTP 304响应没有所有标题字段。 我们必须使用来自缓存条目的标题字段以及来自响应的新字段。
                    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5
                    entry.responseHeaders.putAll(responseHeaders)
                    return NetworkResponse(HttpStatus.SC_NOT_MODIFIED, entry.data!!,
                            entry.responseHeaders, true,
                            SystemClock.elapsedRealtime() - requestStart)
                }
                //一些回应如204s没有内容。 我们必须检查。
                if (httpResponse.entity != null) {
                    responseContents =  entityToBytes(httpResponse.entity)//httpResponse.entity.content.readBytes()
                } else {
                    //添加0字节响应作为一种诚实地表示a的方式无内容请求。
                    responseContents = ByteArray(0)
                }

                val requestLifetime = SystemClock.elapsedRealtime() - requestStart
                //如果请求缓慢,记录日志
                // logSlowRequests(requestLifetime, request, responseContents, statusLine)

                if (statusCode < 200 || statusCode > 299) {
                    throw IOException()
                }
                return NetworkResponse(statusCode, responseContents, responseHeaders, false,
                        SystemClock.elapsedRealtime() - requestStart)
            } catch (e: SocketTimeoutException) {
                attemptRetryOnException(request, TimeoutError())
            } catch (e: MalformedURLException) {
                throw RuntimeException("Bad URL " + request.getUrl(), e)
            } catch (e: IOException) {
                val statusCode: Int
                if (httpResponse != null) {
                    statusCode = httpResponse.statusLine.statusCode
                } else {
                    throw NoConnectionError(e)
                }
                val networkResponse: NetworkResponse
                if (responseContents != null) {
                    networkResponse = NetworkResponse(statusCode, responseContents,
                            responseHeaders, false, SystemClock.elapsedRealtime() - requestStart)
                    if (statusCode === HttpStatus.SC_UNAUTHORIZED || statusCode === HttpStatus.SC_FORBIDDEN) {
                        attemptRetryOnException(request, AuthFailureError(networkResponse))
                    } else if (statusCode in 400..499) {
                        // 不要重试其他客户端错误。
                        throw ClientError(networkResponse)
                    } else if (statusCode in 500..599) {
                        if (request.shouldRetryServerErrors()) {
                            attemptRetryOnException(request, ServerError(networkResponse))
                        } else {
                            throw ServerError(networkResponse)
                        }
                    } else {
                        // 3XX？ 没有理由重试。
                        throw ServerError(networkResponse)
                    }
                } else {
                    attemptRetryOnException(request, NetworkError())
                }
            }
        }
    }


    private fun addCacheHeaders(headers: MutableMap<String, String>, entry: Cache.Entry?) {
        if (entry == null) {
            return
        }
        if (entry!!.etag != null) {
            headers.put("If-None-Match", entry!!.etag!!)
        }
        if (entry!!.lastModified!! > 0) {
            val refTime = Date(entry.lastModified)
            headers.put("If-Modified-Since", DateUtils.formatDate(refTime))
        }
    }

    /**
     * 转化响应头为 Map</String>, String>.
     */
    private fun convertHeaders(headers: Array<Header>): Map<String, String> {
        val result = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
        for (i in headers.indices) {
            result.put(headers[i].name, headers[i].value)
        }
        return result
    }


    /**将HttpEntity的内容读入一个字节。 */
    @Throws(IOException::class, ServerError::class)
    private fun entityToBytes(entity: HttpEntity): ByteArray {
        val bytes = PoolingByteArrayOutputStream(mPool!!, entity.contentLength.toInt())
        var buffer: ByteArray? = null
        try {
            val `in` = entity.content ?: throw ServerError()
            buffer = mPool!!.getBuf(1024)
            while ((`in`.read(buffer)) != -1) {
                bytes.write(buffer, 0, `in`.read(buffer))
            }
            return bytes.toByteArray()

        } finally {
            try {
                // 关闭InputStream并通过“消耗内容”释放资源。
                entity.consumeContent()
            } catch (e: IOException) {
                //如果上面有一个例外，可能会导致该实体处于无效状态。
            }
            mPool!!.returnBuf(buffer)
            bytes.close()
        }
    }

    /**
     *请求的重试策略，抛出超时异常。
     * @param request The request to use.
     */
    @Throws(VolleyError::class)
    private fun attemptRetryOnException(request: Request<*>,
                                        exception: VolleyError) {
        val retryPolicy = request.getRetryPolicy()
        try {
            retryPolicy!!.retry(exception)
        } catch (e: VolleyError) {
            throw e
        }
    }
}
