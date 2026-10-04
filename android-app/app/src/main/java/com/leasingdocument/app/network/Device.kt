package com.leasingdocument.app.network

data class Device(
    val deviceId: Long?,
    val imeiNumber: String?,
    val deviceNumber: String?,
    val deviceType: String?,
    val osVersion: String?,
    val status: String?,
    val assignedDate: String?
)