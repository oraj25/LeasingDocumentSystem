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

    // =========================================================
    // AUTHENTICATION
    // =========================================================

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>


    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): Response<ResponseBody>


    // =========================================================
    // AGENTS
    // =========================================================

    @GET("api/agents")
    suspend fun getAgents():
            Response<List<Agent>>


    // =========================================================
    // AUDIT LOGS
    // =========================================================

    @GET("api/audit-logs")
    suspend fun getAuditLogs():
            Response<List<AuditLog>>


    // =========================================================
    // CUSTOMERS
    // =========================================================

    @GET("api/customers")
    suspend fun getCustomers():
            Response<List<Customer>>


    // =========================================================
    // DOCUMENT TYPES
    // =========================================================

    @GET("api/document-types")
    suspend fun getDocumentTypes():
            Response<List<DocumentType>>


    @GET("api/document-types/code/{typeCode}")
    suspend fun getDocumentTypeByCode(
        @Path("typeCode")
        typeCode: String
    ): Response<DocumentType>


    // =========================================================
    // DEVICES
    // =========================================================

    @GET("api/devices")
    suspend fun getDevices():
            Response<List<Device>>


    // =========================================================
    // SESSIONS
    // =========================================================

    @GET("api/sessions")
    suspend fun getSessions():
            Response<List<Session>>


    // =========================================================
    // DOCUMENTS
    // =========================================================

    @GET("api/documents")
    suspend fun getDocuments():
            Response<List<Document>>


    @GET("api/documents/my")
    suspend fun getMyDocuments():
            Response<List<Document>>


    // =========================================================
    // LEGACY FILE UPLOAD
    //
    // TEMPORARY ONLY.
    //
    // This exists so the current baseline UI continues to
    // compile while we build the secure camera workflow.
    //
    // The final Agent workflow WILL NOT call this endpoint.
    // =========================================================

    @Multipart
    @POST("api/documents/upload")
    suspend fun uploadDocument(
        @Part
        file: MultipartBody.Part,

        @Part("customerId")
        customerId: RequestBody,

        @Part("documentTypeId")
        documentTypeId: RequestBody,

        @Part("agentId")
        agentId: RequestBody? = null

    ): Response<ResponseBody>


    // =========================================================
    // DOCUMENT FILE
    // =========================================================

    @GET("api/documents/image/{fileName}")
    suspend fun getDocumentImage(
        @Path("fileName")
        fileName: String

    ): Response<ResponseBody>


    // =========================================================
    // DELETE DOCUMENT
    // =========================================================

    @DELETE("api/documents/{id}")
    suspend fun deleteDocument(
        @Path("id")
        documentId: Long

    ): Response<ResponseBody>


    // =========================================================
    // ALTERATION RESULTS
    // =========================================================

    @GET("api/alteration-results")
    suspend fun getAlterationResults():
            Response<List<AlterationResult>>


    // =========================================================
    // INTEGRITY RESULTS
    // =========================================================

    @GET("api/integrity-results")
    suspend fun getIntegrityResults():
            Response<List<IntegrityResult>>
}