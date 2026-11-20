package com.example.tt.network.dtk

import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class DtkParseRequest(val url: String)

/** REST v5 của DTK. `data` để dạng JsonElement vì 200 (Content) và 202 (task_id) khác shape. */
interface DtkApi {

    @POST("api/v1/parse")
    suspend fun parse(
        @Query("wait") wait: Int,
        @Body body: DtkParseRequest
    ): Response<DtkEnvelope<JsonElement>>

    @GET("api/v1/tasks/{taskId}")
    suspend fun task(@Path("taskId") taskId: String): DtkEnvelope<JsonElement>

    @GET("api/v1/auth/me")
    suspend fun authMe(): DtkEnvelope<JsonElement>
}

/** Retrofit client được tạo lại khi người đổi Base URL/key trong màn cấu hình. */
object DtkApiFactory {

    private var cachedSignature: String? = null
    private var cachedApi: DtkApi? = null

    @Synchronized
    fun get(config: DtkConfig.Config): DtkApi {
        cachedApi?.let { if (cachedSignature == config.signature) return it }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // ?wait=20 giữ connection ~20s → read timeout phải lớn hơn.
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-API-Key", config.apiKey)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

        val api = Retrofit.Builder()
            .baseUrl(config.baseUrl) // đã có '/' cuối nhờ DtkConfig.normalizeBaseUrl
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DtkApi::class.java)

        cachedApi = api
        cachedSignature = config.signature
        return api
    }

    @Synchronized
    fun reset() {
        cachedApi = null
        cachedSignature = null
    }
}
