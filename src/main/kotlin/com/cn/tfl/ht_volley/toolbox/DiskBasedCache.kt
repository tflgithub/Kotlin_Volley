package com.cn.tfl.ht_volley.toolbox

import android.os.SystemClock
import com.cn.tfl.ht_volley.Cache
import java.io.*
import java.util.*


/**
 * Created by Happiness on 2017/5/24.
 * 将文件直接缓存到指定目录中的硬盘上的缓存实现。 默认磁盘使用大小为5MB，但可配置。
 */
class DiskBasedCache : Cache {

    companion object {

        /** 默认最大磁盘使用量（以字节为单位）。  */
        private val DEFAULT_DISK_USAGE_BYTES = 5 * 1024 * 1024

        /** 缓存高水位百分比 */
        private val HYSTERESIS_FACTOR = 0.9f

        /** 当前版本的缓存文件格式的魔术编号。  */
        private val CACHE_MAGIC = 0x20150306


        @Throws(IOException::class)
        fun writeInt(os: OutputStream, n: Int) {
            os.write(n shr 0 and 0xff)
            os.write(n shr 8 and 0xff)
            os.write(n shr 16 and 0xff)
            os.write(n shr 24 and 0xff)
        }

        @Throws(IOException::class)
        private fun read(`is`: InputStream): Int {
            val b = `is`.read()
            if (b == -1) {
                throw EOFException()
            }
            return b
        }

        @Throws(IOException::class)
        fun readInt(`is`: InputStream): Int {
            var n = 0
            n = n or (read(`is`) shl 0)
            n = n or (read(`is`) shl 8)
            n = n or (read(`is`) shl 16)
            n = n or (read(`is`) shl 24)
            return n
        }

        @Throws(IOException::class)
        fun writeLong(os: OutputStream, n: Long) {
            os.write(n.ushr(0).toByte().toInt())
            os.write(n.ushr(8).toByte().toInt())
            os.write(n.ushr(16).toByte().toInt())
            os.write(n.ushr(24).toByte().toInt())
            os.write(n.ushr(32).toByte().toInt())
            os.write(n.ushr(40).toByte().toInt())
            os.write(n.ushr(48).toByte().toInt())
            os.write(n.ushr(56).toByte().toInt())
        }

        @Throws(IOException::class)
        fun readLong(`is`: InputStream): Long {
            var n: Long = 0
            n = n or ((read(`is`) and 0xFF shl 0).toLong())
            n = n or (read(`is`) and 0xFF shl 8).toLong()
            n = n or ((read(`is`) and 0xFF shl 16).toLong())
            n = n or (read(`is`) and 0xFF shl 24).toLong()
            n = n or (read(`is`) and 0xFF shl 32).toLong()
            n = n or (read(`is`) and 0xFF shl 40).toLong()
            n = n or (read(`is`) and 0xFF shl 48).toLong()
            n = n or (read(`is`) and 0xFF shl 56).toLong()
            return n
        }

        @Throws(IOException::class)
        fun writeString(os: OutputStream, s: String) {
            val b = s.toByteArray(charset("UTF-8"))
            writeLong(os, b.size.toLong())
            os.write(b, 0, b.size)
        }

        @Throws(IOException::class)
        fun readString(`is`: InputStream): String {
            val n = readLong(`is`).toInt()
            val b = streamToBytes(`is`, n)
            return b.toString()
        }

        @Throws(IOException::class)
        fun writeStringStringMap(map: Map<String, String>?, os: OutputStream) {
            if (map != null) {
                writeInt(os, map.size)
                for ((key, value) in map) {
                    writeString(os, key)
                    writeString(os, value)
                }
            } else {
                writeInt(os, 0)
            }
        }

        @Throws(IOException::class)
        fun readStringStringMap(`is`: InputStream): Map<String, String> {
            val size = readInt(`is`)
            val result = if (size == 0) {
                Collections.emptyMap<String, String>()
            } else
                HashMap<String, String>(size)
            for (i in 0..size - 1) {
                val key = readString(`is`).intern()
                val value = readString(`is`).intern()
                result.put(key, value)
            }
            return result
        }

        /**
         * Reads the contents of an InputStream into a byte[].
         */
        @Throws(IOException::class)
        private fun streamToBytes(`in`: InputStream, length: Int): ByteArray {
            val bytes = ByteArray(length)
            var pos = 0
            while (pos < length && (`in`.read(bytes, pos, length - pos)) != -1) {
                pos += `in`.read(bytes, pos, length - pos)
            }
            if (pos != length) {
                throw IOException("Expected $length bytes, read $pos bytes")
            }
            return bytes
        }


        /**
         * Handles holding onto the cache headers for an entry.
         */
        // Visible for testing.
        class CacheHeader {
            /** The size of the data identified by this CacheHeader. (This is not
             * serialized to disk.  */
            var size: Long = 0

            /** The key that identifies the cache entry.  */
            var key: String? = null

            /** ETag for cache coherence.  */
            var etag: String? = null

            /** Date of this response as reported by the server.  */
            var serverDate: Long = 0

            /** The last modified date for the requested object.  */
            var lastModified: Long = 0

            /** TTL for this record.  */
            var ttl: Long = 0

            /** Soft TTL for this record.  */
            var softTtl: Long = 0

            /** Headers from the response resulting in this cache entry.  */
            var responseHeaders: MutableMap<String, String>? = null

            /**
             * Reads the header off of an InputStream and returns a CacheHeader object.
             * @param is The InputStream to read from.
             * *
             * @throws IOException
             */
            companion object {
                @Throws(IOException::class)
                fun readHeader(`is`: InputStream): CacheHeader {
                    val entry = CacheHeader()
                    val magic = readInt(`is`)
                    if (magic != CACHE_MAGIC) {
                        // don't bother deleting, it'll get pruned eventually
                        throw IOException()
                    }
                    entry.key = readString(`is`)
                    entry.etag = readString(`is`)
                    if (entry.etag == "") {
                        entry.etag = null
                    }
                    entry.serverDate = readLong(`is`)
                    entry.lastModified = readLong(`is`)
                    entry.ttl = readLong(`is`)
                    entry.softTtl = readLong(`is`)
                    entry.responseHeaders = readStringStringMap(`is`) as MutableMap<String, String>

                    return entry
                }
            }

            constructor()

            /**
             * Instantiates a new CacheHeader object
             * @param key The key that identifies the cache entry
             * *
             * @param entry The cache entry.
             */
            constructor(key: String, entry: Cache.Entry) {
                this.key = key
                this.size = entry.data!!.size.toLong()
                this.etag = entry.etag
                this.serverDate = entry.serverDate!!
                this.lastModified = entry.lastModified
                this.ttl = entry.ttl!!
                this.softTtl = entry.softTtl!!
                this.responseHeaders = entry.responseHeaders
            }

            /**
             * Creates a cache entry for the specified data.
             */
            fun toCacheEntry(data: ByteArray): Cache.Entry {
                val e = Cache.Entry()
                e.data = data
                e.etag = etag
                e.serverDate = serverDate
                e.lastModified = lastModified
                e.ttl = ttl
                e.softTtl = softTtl
                e.responseHeaders = responseHeaders
                return e
            }


            /**
             * Writes the contents of this CacheHeader to the specified OutputStream.
             */
            fun writeHeader(os: OutputStream): Boolean {
                try {
                    writeInt(os, CACHE_MAGIC)
                    writeString(os, key!!)
                    writeString(os, (if (etag == null) "" else etag)!!)
                    writeLong(os, serverDate)
                    writeLong(os, lastModified)
                    writeLong(os, ttl)
                    writeLong(os, softTtl)
                    writeStringStringMap(responseHeaders, os)
                    os.flush()
                    return true
                } catch (e: IOException) {
                    return false
                }
            }
        }


        class CountingInputStream(`in`: InputStream) : FilterInputStream(`in`) {
            var bytesRead = 0

            @Throws(IOException::class)
            override fun read(): Int {
                val result = super.read()
                if (result != -1) {
                    bytesRead++
                }
                return result
            }

            @Throws(IOException::class)
            override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
                val result = super.read(buffer, offset, count)
                if (result != -1) {
                    bytesRead += result
                }
                return result
            }
        }

    }


    /** 键的映射，CacheHeader对  */
    private var mEntries = LinkedHashMap<String, CacheHeader>(16, .75f, true)

    /** 缓存当前使用的空间总数（以字节为单位）。  */
    private var mTotalSize: Long = 0

    /** 用于缓存的根目录。  */
    private var mRootDirectory: File? = null

    /** 缓存的最大大小（以字节为单位）。  */
    private var mMaxCacheSizeInBytes: Int = 0


    //在指定的目录中构造DiskBasedCache的实例。
    @JvmOverloads constructor(rootDirectory: File, maxCacheSizeInBytes: Int) {
        this.mRootDirectory = rootDirectory
        mMaxCacheSizeInBytes = maxCacheSizeInBytes
    }

    //使用默认的最大缓存大小为5MB，在指定的目录中构造DiskBasedCache的实例。
    constructor(rootDirectory: File) : this(rootDirectory, DEFAULT_DISK_USAGE_BYTES)

    @Synchronized override fun get(key: String): Cache.Entry? {

        //如果条目不存在，返回。
        val entry = mEntries[key] ?: return null
        val file = getFileForKey(key)
        var cis: CountingInputStream? = null

        try {
            cis = CountingInputStream(BufferedInputStream(FileInputStream(file)))
            CacheHeader.readHeader(cis) // eat header
            var data = streamToBytes(cis, (file.length() - cis!!.bytesRead) as Int)
            return entry!!.toCacheEntry(data!!)
        } catch (e: IOException) {
            // VolleyLog.d("%s: %s", file.getAbsolutePath(), e.toString())
            remove(key)
            return null
        } catch (e: NegativeArraySizeException) {
            //  VolleyLog.d("%s: %s", file.getAbsolutePath(), e.toString())
            remove(key)
            return null
        } finally {
            if (cis != null) {
                try {
                    cis!!.close()
                } catch (ioe: IOException) {
                    return null
                }
            }
        }
    }

    @Synchronized override fun put(key: String, entry: Cache.Entry) {
        pruneIfNeeded(entry.data!!.size)
        val file = getFileForKey(key)
        try {
            val fos = BufferedOutputStream(FileOutputStream(file))
            val e = CacheHeader(key, entry)
            val success = e.writeHeader(fos)
            if (!success) {
                fos.close()
                // VolleyLog.d("Failed to write header for %s", file.absolutePath)
                throw IOException()
            }
            fos.write(entry.data)
            fos.close()
            putEntry(key, e)
            return
        } catch (e: IOException) {
        }

        val deleted = file.delete()
        if (!deleted) {
            //VolleyLog.d("Could not clean up file %s", file.absolutePath)
        }
    }

    //通过扫描当前在指定的根目录中的所有文件来初始化DiskBasedCache。 根据需要创建根目录。
    @Synchronized override fun initialize() {
        if (!mRootDirectory!!.exists()) {
            if (!mRootDirectory!!.mkdirs()) {
                //VolleyLog.e("Unable to create cache dir %s", mRootDirectory.getAbsolutePath())
            }
            return
        }

        val files = mRootDirectory!!.listFiles() ?: return
        for (file in files) {
            var fis: BufferedInputStream? = null
            try {
                fis = BufferedInputStream(FileInputStream(file))
                val entry = CacheHeader.readHeader(fis)
                entry.size = file!!.length()
                putEntry(entry.key!!, entry)
            } catch (e: IOException) {
                file?.delete()
            } finally {
                try {
                    if (fis != null) {
                        fis.close()
                    }
                } catch (ignored: IOException) {
                }
            }
        }
    }

    @Synchronized override fun remove(key: String) {
        val deleted = getFileForKey(key).delete()
        removeEntry(key)
        if (!deleted) {
//           VolleyLog.d("Could not delete cache entry for key=%s, filename=%s",
//                   key, getFilenameForKey(key))
        }
    }

    @Synchronized override fun clear() {
        val files = mRootDirectory!!.listFiles()
        if (files != null) {
            for (file in files) {
                file.delete()
            }
        }
        mEntries.clear()
        mTotalSize = 0
        //VolleyLog.d("Cache cleared.")
    }

    @Synchronized override fun invalidate(key: String, fullExpire: Boolean) {
        val entry = get(key)
        if (entry != null) {
            entry.softTtl = 0
            if (fullExpire) {
                entry.ttl = 0
            }
            put(key, entry)
        }
    }


    /**
     * 将具有指定键的条目放入缓存。
     * @param key The key to identify the entry by.
     * *
     * @param entry The entry to cache.
     */
    private fun putEntry(key: String, entry: CacheHeader) {
        if (!mEntries.containsKey(key)) {
            mTotalSize += entry.size
        } else {
            val oldEntry = mEntries[key]
            mTotalSize += entry.size - oldEntry!!.size
        }
        mEntries.put(key, entry)
    }

    /**
     * 从缓存中删除由“key”标识的条目。
     */
    private fun removeEntry(key: String) {
        val entry = mEntries[key]
        if (entry != null) {
            mTotalSize -= entry.size
            mEntries.remove(key)
        }
    }

    /**
     *返回给定缓存密钥的文件对象。
     */
    fun getFileForKey(key: String): File {
        return File(mRootDirectory, getFilenameForKey(key))
    }

    /**
     * 为指定的缓存键创建伪唯一文件名。
     * @param key The key to generate a file name for.
     * *
     * @return A pseudo-unique filename.
     */
    private fun getFilenameForKey(key: String): String {
        val firstHalfLength = key.length / 2
        var localFilename = key.substring(0, firstHalfLength).hashCode().toString()
        localFilename += key.substring(firstHalfLength).hashCode().toString()
        return localFilename
    }

    /**
     * 调整缓存以适应指定的字节数。
     * @param neededSpace The amount of bytes we are trying to fit into the cache.
     */
    private fun pruneIfNeeded(neededSpace: Int) {
        if (mTotalSize + neededSpace < mMaxCacheSizeInBytes) {
            return
        }

        val before = mTotalSize
        var prunedFiles = 0
        val startTime = SystemClock.elapsedRealtime()

        val iterator = mEntries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val e = entry.value
            val deleted = getFileForKey(e.key!!).delete()
            if (deleted) {
                mTotalSize -= e.size
            } else {
//                VolleyLog.d("Could not delete cache entry for key=%s, filename=%s",
//                        e.key, getFilenameForKey(e.key!!))
            }
            iterator.remove()
            prunedFiles++

            if (mTotalSize + neededSpace < mMaxCacheSizeInBytes * HYSTERESIS_FACTOR) {
                break
            }
        }

//        if (VolleyLog.DEBUG) {
//            VolleyLog.v("pruned %d files, %d bytes, %d ms",
//                    prunedFiles, mTotalSize - before, SystemClock.elapsedRealtime() - startTime)
//        }
    }


}