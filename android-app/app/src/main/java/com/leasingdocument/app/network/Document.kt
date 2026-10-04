package com.leasingdocument.app.network

data class Document(
    val documentId: Long?,
    val customerId: Long?,
    val agentId: Long?,
    val documentTypeId: Long?,
    val fileName: String?,
    val filePath: String?,
    val captureDateTime: String?,
    val captureLocation: String?,
    val imageQuality: String?,
    val captureStatus: String?,
    val verificationStatus: String?,
    val status: String?
)