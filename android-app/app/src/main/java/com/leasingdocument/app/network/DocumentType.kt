package com.leasingdocument.app.network

data class DocumentType(
    val documentTypeId: Long?,
    val typeName: String?,
    val description: String?,
    val requiredFlag: Boolean?
)