package org.example.slippy.foundation

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** The one way to the service. Failures are values: [ApiException] or [NetworkException]. */
object NetworkClient {
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    @PublishedApi
    internal val client: HttpClient by lazy {
        // The engine comes from the platform's dependency (OkHttp, Darwin, Java, Js)
        HttpClient {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(Logging) {
                level = LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) = getPlatform().log(message)
                }
            }
        }
    }

    @PublishedApi
    internal fun url(path: String) = "${Configuration.csUrl}/${path.trimStart('/')}"

    /**
     * POSTs [request] as JSON, and decodes [Res] on 2xx. Two type parameters, because the web
     * targets have no reflection to find the serializer of a request typed `Any`.
     * A [Res] of [Unit] accepts any body.
     */
    suspend inline fun <reified Req, reified Res> postJson(
        path: String,
        request: Req? = null,
        idToken: String? = null,
    ): Result<Res> = call {
        val response = client.post(url(path)) {
            if (idToken != null) bearerAuth(idToken)
            contentType(ContentType.Application.Json)
            if (request != null) setBody(request) else setBody("{}")
        }
        if (!response.status.isSuccess()) throw apiException(response)
        if (Res::class == Unit::class) Unit as Res else response.body<Res>()
    }

    /** A GET that answers bytes, such as a map tile. */
    suspend fun getBytes(path: String, idToken: String? = null): Result<ByteArray> = call {
        val response = client.get(url(path)) {
            if (idToken != null) bearerAuth(idToken)
        }
        if (!response.status.isSuccess()) throw apiException(response)
        response.body<ByteArray>()
    }

    @PublishedApi
    internal suspend fun apiException(response: HttpResponse): ApiException {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val error = runCatching { json.decodeFromString<ErrorBody>(text) }.getOrNull()
        return ApiException(
            code = error?.code ?: ERROR_CODE_GENERAL,
            message = error?.errorMessage ?: "HTTP ${response.status.value}",
            status = response.status.value,
        )
    }

    @PublishedApi
    internal suspend inline fun <T> call(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        log("Service error ${e.status}, code ${e.code}: ${e.message}")
        Result.failure(e)
    } catch (e: Throwable) {
        log("Network failure: $e")
        Result.failure(NetworkException(e))
    }
}
