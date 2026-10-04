package com.leasingdocument.app.network

data class AuditLog(
    val logId: Long?,
    val adminId: Long?,
    val agentId: Long?,
    val action: String?,
    val description: String?,
    val ipAddress: String?,
    val createdAt: String?
)