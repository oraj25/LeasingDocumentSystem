package com.leasingdocument.app.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): Response<ResponseBody>

    @GET("api/agents")
    suspend fun getAgents(): Response<List<Agent>>

    @GET("api/audit-logs")
    suspend fun getAuditLogs(): Response<List<AuditLog>>

    @GET("api/customers")
    suspend fun getCustomers(): Response<List<Customer>>

    @GET("api/document-types")
    suspend fun getDocumentTypes(): Response<List<DocumentType>>

    @GET("api/devices")
    suspend fun getDevices(): Response<List<Device>>

    @GET("api/sessions")
    suspend fun getSessions(): Response<List<Session>>

    @GET("api/documents")
    suspend fun getDocuments(): Response<List<Document>>

    @GET("api/documents/my")
    suspend fun getMyDocuments(): Response<List<Document>>

    @Multipart
    @POST("api/documents/upload")
    suspend fun uploadDocument(
        @Part file: MultipartBody.Part,
        @Part("customerId") customerId: RequestBody,
        @Part("documentTypeId") documentTypeId: RequestBody,
        @Part("agentId") agentId: RequestBody? = null
    ): Response<ResponseBody>

    @GET("api/documents/image/{fileName}")
    suspend fun getDocumentImage(
        @Path("fileName") fileName: String
    ): Response<ResponseBody>

    @DELETE("api/documents/{id}")
    suspend fun deleteDocument(
        @Path("id") documentId: Long
    ): Response<ResponseBody>

    @GET("api/alteration-results")
    suspend fun getAlterationResults(): Response<List<AlterationResult>>

    @GET("api/integrity-results")
    suspend fun getIntegrityResults(): Response<List<IntegrityResult>>
}