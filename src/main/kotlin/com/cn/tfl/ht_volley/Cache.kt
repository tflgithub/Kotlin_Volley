package com.cn.tfl.ht_volley

import java.util.*

/**
 * Created by Happiness on 2017/5/23.
 *
 * 缓存接口
 */
interface Cache {

    /**
     * 检索缓存
     * @param key
     * @return Entry
     */
    fun get(key: String): Entry?

    /**
     * 添加缓存
     * @param key
     * @param entry
     */
    fun put(key: String, entry: Entry)

    /**
     * 初始化方法
     */
    fun initialize()

    /**
     * 使缓存中的条目无效。
     * @param key Cache key
     * *
     * @param fullExpire True to fully expire the entry, false to soft expire
     */
    fun invalidate(key: String, fullExpire: Boolean)

    /**
     * 删除缓存
     * @param key
     */
    fun remove(key: String)

    /**
     * 清空缓存
     */
    fun clear()

    //缓存实体
    class Entry {

        //缓存返回的数据
        var data: ByteArray? = null

        //用于标识缓存的一致性
        var etag: String? = null

        //服务器返回的时间
        var serverDate: Long? = 0

        //服务最后修改时间
        var lastModified: Long = 0

        //TTL记录
        var ttl: Long? = 0

        //软TTL
        var softTtl: Long? = 0

        //服务器响应头集合
        var responseHeaders = Collections.emptyMap<String, String>()

        //判断是否过期
        fun isExpired(): Boolean {
            return this.ttl!! < System.currentTimeMillis()
        }

        //是否从原始数据源刷新
        fun refreshNeeded(): Boolean {
            return this.softTtl!! < System.currentTimeMillis()
        }
    }
}