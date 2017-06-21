package com.cn.tfl.ht_volley.toolbox

import com.cn.tfl.ht_volley.Cache
import com.cn.tfl.ht_volley.NetworkResponse
import org.apache.http.impl.cookie.DateParseException
import org.apache.http.impl.cookie.DateUtils
import org.apache.http.protocol.HTTP


/**
 * Created by Happiness on 2017/5/24.
 */
class HttpHeaderParser {
    /**
     * Extracts a [Cache.Entry] from a [NetworkResponse].

     * @param response The network response to parse headers from
     * *
     * @return a cache entry for the given response, or null if the response is not cacheable.
     *
     *
     *
     */

    companion object {
        fun parseCacheHeaders(response: NetworkResponse): Cache.Entry? {
            val now = System.currentTimeMillis()

            val headers = response.headers

            var serverDate: Long = 0
            var lastModified: Long = 0
            var serverExpires: Long = 0
            var softExpire: Long = 0
            var finalExpire: Long = 0
            var maxAge: Long = 0
            var staleWhileRevalidate: Long = 0
            var hasCacheControl = false
            var mustRevalidate = false

            var serverEtag: String? = null
            var headerValue: String?

            headerValue = headers["Date"]
            if (headerValue != null) {
                serverDate = parseDateAsEpoch(headerValue)
            }

            headerValue = headers["Cache-Control"]
            if (headerValue != null) {
                hasCacheControl = true
                val tokens = headerValue.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                tokens.indices
                        .map { i -> tokens[i].trim { it <= ' ' } }
                        .forEach {
                            if (it == "no-cache" || it == "no-store") {
                               // return null
                            } else if (it.startsWith("max-age=")) {
                                try {
                                    maxAge = java.lang.Long.parseLong(it.substring(8))
                                } catch (e: Exception) {
                                }

                            } else if (it.startsWith("stale-while-revalidate=")) {
                                try {
                                    staleWhileRevalidate = java.lang.Long.parseLong(it.substring(23))
                                } catch (e: Exception) {
                                }

                            } else if (it == "must-revalidate" || it == "proxy-revalidate") {
                                mustRevalidate = true
                            }
                        }
            }

            headerValue = headers["Expires"]
            if (headerValue != null) {
                serverExpires = parseDateAsEpoch(headerValue)
            }

            headerValue = headers["Last-Modified"]
            if (headerValue != null) {
                lastModified = parseDateAsEpoch(headerValue)
            }

            serverEtag = headers["ETag"]

            // Cache-Control takes precedence over an Expires header, even if both exist and Expires
            // is more restrictive.
            if (hasCacheControl) {
                softExpire = now + maxAge * 1000
                finalExpire = if (mustRevalidate)
                    softExpire
                else
                    softExpire + staleWhileRevalidate * 1000
            } else if (serverDate > 0 && serverExpires >= serverDate) {
                // Default semantic for Expire header in HTTP specification is softExpire.
                softExpire = now + (serverExpires - serverDate)
                finalExpire = softExpire
            }

            val entry = Cache.Entry()
            entry.data = response.data
            entry.etag = serverEtag
            entry.softTtl = softExpire
            entry.ttl = finalExpire
            entry.serverDate = serverDate
            entry.lastModified = lastModified
            entry.responseHeaders = headers

            return entry
        }

        /**
         * Parse date in RFC1123 format, and return its value as epoch
         */
        fun parseDateAsEpoch(dateStr: String): Long {
            try {
                // Parse date in RFC1123 format if this header contains one
                return DateUtils.parseDate(dateStr).time
            } catch (e: DateParseException) {
                // Date in invalid format, fallback to 0
                return 0
            }

        }

        /**
         * Retrieve a charset from headers

         * @param headers An [java.util.Map] of headers
         * *
         * @param defaultCharset Charset to return if none can be found
         * *
         * @return Returns the charset specified in the Content-Type of this header,
         * * or the defaultCharset if none can be found.
         */
        fun parseCharset(headers: Map<String, String>, defaultCharset: String): String {
            val contentType = headers[HTTP.CONTENT_TYPE]
            if (contentType != null) {
                val params = contentType.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                (1..params.size - 1)
                        .map { i -> params[i].trim { it <= ' ' }.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray() }
                        .filter { it.size == 2 && it[0] == "charset" }
                        .forEach { return it[1] }
            }

            return defaultCharset
        }

        /**
         * Returns the charset specified in the Content-Type of this header,
         * or the HTTP default (ISO-8859-1) if none can be found.
         */
        fun parseCharset(headers: Map<String, String>): String {
            return parseCharset(headers, HTTP.DEFAULT_CONTENT_CHARSET)
        }
    }
}