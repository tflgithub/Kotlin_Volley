package com.cn.tfl.ht_volley.toolbox

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Created by Happiness on 2017/5/24.
 *
 * 使用byte []缓冲区池的{@link java.io.ByteArrayOutputStream}的一个变体，而不是总是将它们分配到新的内存中，从而节省堆流。
 */
class PoolingByteArrayOutputStream : ByteArrayOutputStream {


    companion object {
        /**
         * 如果[.PoolingByteArrayOutputStream]构造函数被调用，那就是初始化底层字节数组的默认大小。
         */
        private val DEFAULT_SIZE = 256
    }

    private var mPool: ByteArrayPool? = null

    constructor(pool: ByteArrayPool, size: Int) {
        this.mPool = pool
        buf = mPool!!.getBuf(Math.max(size, DEFAULT_SIZE))
    }

    constructor(pool: ByteArrayPool) : this(pool, DEFAULT_SIZE)

    /**
     *确保在给定数量的附加字节的缓冲区中有足够的空间。
     */
    private fun expand(i: Int) {
        /* 如果不扩展它,缓冲区可以处理@i多个字节 */
        if (count + i <= buf.size) {
            return
        }
        val newbuf = mPool!!.getBuf((count + i) * 2)
        System.arraycopy(buf, 0, newbuf, 0, count)
        mPool!!.returnBuf(buf)
        buf = newbuf
    }

    @Synchronized
    override fun write(oneByte: Int) {
        expand(1)
        super.write(oneByte)
    }

    @Synchronized override fun write(buffer: ByteArray, offset: Int, len: Int) {
        expand(len)
        super.write(buffer, offset, len)
    }

    @Throws(IOException::class)
    override fun close() {
        mPool!!.returnBuf(buf)
        buf = null
        super.close()
    }

    override fun flush() {
        mPool!!.returnBuf(buf)
        super.flush()
    }

}