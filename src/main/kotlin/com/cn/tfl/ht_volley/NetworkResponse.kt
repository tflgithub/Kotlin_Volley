package com.cn.tfl.ht_volley

import org.apache.http.HttpStatus
import java.util.*


/**
 * Created by Administrator on 2017/5/22.
 */

class NetworkResponse
/**
 * Creates a new network response.
 * @param statusCode the HTTP status code
 * *
 * @param data Response body
 * *
 * @param headers Headers returned with this response, or null for none
 * *
 * @param notModified True if the server returned a 304 and the data was already in cache
 * *
 * @param networkTimeMs Round-trip network time to receive network response
 */
@JvmOverloads constructor(
        /** The HTTP status code.  */
        val statusCode: Int,
        /** Raw data from this response.  */
        val data: ByteArray?,
        /** Response headers.  */
        val headers: Map<String, String>,
        /** True if the server returned a 304 (Not Modified).  */
        val notModified: Boolean,
        /** Network roundtrip time in milliseconds.  */
        val networkTimeMs: Long = 0) {

    constructor(data: ByteArray) : this(HttpStatus.SC_OK, data, Collections.emptyMap(), false, 0)

    constructor(data: ByteArray, headers: Map<String, String>) : this(HttpStatus.SC_OK, data, headers, false, 0)
}
