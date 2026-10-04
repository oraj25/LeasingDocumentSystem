package com.leasingdocument.app.network

data class AlterationResult(
    val alterationResultId: Long?,
    val documentId: Long?,
    val riskScore: Int?,
    val riskLevel: String?,
    val analysisStatus: String?,
    val suspiciousRegionCount: Int?,
    val algorithmVersion: String?,
    val processedAt: String?
)