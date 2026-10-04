package com.leasingdocument.app.network

data class IntegrityResult(
    val integrityResultId: Long?,
    val documentId: Long?,
    val sha256Hash: String?,
    val verificationResult: String?,
    val processedAt: String?
)