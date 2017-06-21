package com.cn.tfl.ht_volley.toolbox

import com.cn.tfl.ht_volley.NetworkResponse
import com.cn.tfl.ht_volley.Request
import com.cn.tfl.ht_volley.Response
import java.io.UnsupportedEncodingException


abstract class JsonRequest<T> : Request<T> {

    private val mRequestBody: String?
    private val mListener: Response.Listener<T>

    constructor(method: Int, url: String, mRequestBody: String?, mListener: Response.Listener<T>, errorListener: Response.ErrorListener) : super(method, url, errorListener) {
        this.mRequestBody = mRequestBody
        this.mListener = mListener
    }

    /**
     * Deprecated constructor for a JsonRequest which defaults to GET unless [.getPostBody]
     * or [.getPostParams] is overridden (which defaults to POST).

     */

    constructor(url: String, requestBody: String, listener: Response.Listener<T>,
                errorListener: Response.ErrorListener) : this(Method.DEPRECATED_GET_OR_POST, url, requestBody, listener, errorListener) {
    }

    abstract override fun parseNetworkResponse(response: NetworkResponse): Response<T>



    fun getPostBody(): ByteArray? {
        return getBody()
    }

    fun getPostBodyContentType(): String? {
        return PROTOCOL_CONTENT_TYPE
    }

    override fun getBody(): ByteArray? {
        try {
            return mRequestBody?.toByteArray()
        } catch (uee: UnsupportedEncodingException) {

            return null
        }

    }

    companion object {
        /** Default charset for JSON request.  */
        protected val PROTOCOL_CHARSET = "utf-8"

        /** Content type for request.  */
        private val PROTOCOL_CONTENT_TYPE = String.format("application/json; charset=%s", PROTOCOL_CHARSET)
    }
}


