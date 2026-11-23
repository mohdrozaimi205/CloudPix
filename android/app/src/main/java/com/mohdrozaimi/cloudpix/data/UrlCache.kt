package com.mohdrozaimi.cloudpix.data

import com.mohdrozaimi.cloudpix.Graph
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Cache URL bertandatangan; permintaan thumbnail digabung (maks 200 satu panggilan). */
object UrlCache {
    private val cache = ConcurrentHashMap<String, SignedUrl>()
    private val pending = mutableMapOf<String, CompletableDeferred<String?>>()
    private val lock = Mutex()
    private var job: Job? = null

    private fun fresh(s: SignedUrl?) = s != null && s.expiresAt - System.currentTimeMillis() > 120_000

    suspend fun thumb(id: String): String? {
        cache["t:$id"]?.takeIf(::fresh)?.let { return it.url }
        val d = lock.withLock {
            val d = pending.getOrPut(id) { CompletableDeferred() }
            if (job?.isActive != true) job = Graph.scope.launch { delay(40); flush() }
            d
        }
        return d.await()
    }

    private suspend fun flush() {
        val batch = lock.withLock { pending.toMap().also { pending.clear() } }
        batch.keys.chunked(200).forEach { ids ->
            val r = runCatching { Graph.api.mediaUrls(ids, "thumb") }.getOrNull()
            ids.forEach { id ->
                val s = r?.get(id)
                if (s != null) cache["t:$id"] = s
                batch[id]?.complete(s?.url)
            }
        }
        lock.withLock { if (pending.isNotEmpty()) job = Graph.scope.launch { flush() } }
    }

    suspend fun original(id: String): String? {
        cache["o:$id"]?.takeIf(::fresh)?.let { return it.url }
        val s = Graph.api.mediaUrls(listOf(id), "original")[id] ?: return null
        cache["o:$id"] = s
        return s.url
    }

    suspend fun downloads(ids: List<String>): Map<String, String> =
        ids.chunked(50).flatMap { Graph.api.mediaUrls(it, "download").mapNotNull { (k, v) -> v.url?.let { k to it } } }.toMap()
}
