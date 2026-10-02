package com.example.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Searches Nyaa (RSS) and SubsPlease (JSON API) for anime releases with a magnet link. */
object AnimeSearchRepository {
    private const val NYAA_URL = "https://nyaa.si/"
    private const val SUBSPLEASE_API_URL = "https://subsplease.org/api/"
    private const val NYAA_CATEGORY_ENGLISH_TRANSLATED = "1_2"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) RemSubs/1.3"
    private const val SUBSPLEASE_CACHE_MS = 5 * 60_000L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    private class CachedResponse(val query: String, val body: String, val fetchedAtMs: Long)

    /** SubsPlease returns every resolution at once, so switching 480p/720p/1080p needs no new request. */
    @Volatile private var subsPleaseCache: CachedResponse? = null

    suspend fun search(query: String, source: AnimeSource, quality: VideoQuality): List<AnimeSearchResult> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val results = when (source) {
            AnimeSource.SUBSPLEASE -> {
                val body = cachedSubsPlease(term) ?: fetch(subsPleaseUrl(term)).also {
                    subsPleaseCache = CachedResponse(term, it, System.currentTimeMillis())
                }
                parseOrThrow(source) { SubsPleaseParser.parse(body, quality) }
            }
            AnimeSource.NYAA -> {
                val body = fetch(nyaaUrl(term, quality))
                // The query already asks for the resolution; drop titles that name a different one
                parseOrThrow(source) { NyaaRssParser.parse(body) }.filter { it.quality == null || it.quality == quality }
            }
        }
        // Result ids are list keys in the UI and must be unique
        return results.distinctBy { it.id }
    }

    private fun cachedSubsPlease(term: String): String? {
        val cached = subsPleaseCache ?: return null
        val fresh = System.currentTimeMillis() - cached.fetchedAtMs < SUBSPLEASE_CACHE_MS
        return cached.body.takeIf { fresh && cached.query.equals(term, ignoreCase = true) }
    }

    private fun subsPleaseUrl(term: String): HttpUrl = SUBSPLEASE_API_URL.toHttpUrl().newBuilder()
        .addQueryParameter("f", "search")
        .addQueryParameter("tz", "UTC")
        .addQueryParameter("s", term)
        .build()

    private fun nyaaUrl(term: String, quality: VideoQuality): HttpUrl = NYAA_URL.toHttpUrl().newBuilder()
        .addQueryParameter("page", "rss")
        .addQueryParameter("q", "$term ${quality.label}")
        .addQueryParameter("c", NYAA_CATEGORY_ENGLISH_TRANSLATED)
        .addQueryParameter("f", "0")
        .build()

    private inline fun parseOrThrow(source: AnimeSource, parse: () -> List<AnimeSearchResult>): List<AnimeSearchResult> {
        return try {
            parse()
        } catch (e: Exception) {
            throw AnimeSearchException("${source.displayName} yanıtı okunamadı. Site biçimi değişmiş olabilir.", e)
        }
    }

    private suspend fun fetch(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, application/rss+xml, text/xml, */*")
            .build()
        val response = try {
            client.newCall(request).await()
        } catch (e: UnknownHostException) {
            throw AnimeSearchException(
                "${url.host} adresine ulaşılamadı. İnternet bağlantını kontrol et; bağlantı varsa site DNS ile engellenmiş olabilir.",
                e
            )
        } catch (e: SocketTimeoutException) {
            throw AnimeSearchException("${url.host} zamanında yanıt vermedi. Biraz sonra tekrar dene.", e)
        } catch (e: IOException) {
            throw AnimeSearchException("${url.host} bağlantı hatası: ${e.message ?: e.javaClass.simpleName}", e)
        }
        response.use {
            if (!it.isSuccessful) throw AnimeSearchException(httpErrorMessage(url.host, it.code))
            it.body?.string() ?: throw AnimeSearchException("${url.host} boş yanıt döndürdü.")
        }
    }

    private fun httpErrorMessage(host: String, code: Int): String = when (code) {
        403, 503 -> "$host isteği geri çevirdi (HTTP $code, muhtemelen bot koruması). Biraz sonra tekrar dene."
        429 -> "$host çok fazla istek aldı (HTTP 429). Birkaç saniye bekleyip tekrar dene."
        in 500..599 -> "$host şu an hata veriyor (HTTP $code)."
        else -> "$host beklenmeyen yanıt verdi (HTTP $code)."
    }

    /** Suspends until the call finishes; cancelling the coroutine cancels the HTTP request. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        })
    }
}
