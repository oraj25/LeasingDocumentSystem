
package com.leasingdocument.app.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leasingdocument.app.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.util.Locale

private enum class DocumentPage {
    AGENTS,
    CLIENTS,
    DOCUMENTS,
    DETAILS
}

@Composable
fun ManageDocumentsScreen() {

    if (AuthSession.role != "ADMIN") {
        Text(
            "Administrator access required",
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    val scope = rememberCoroutineScope()

    var agents by remember {
        mutableStateOf<List<Agent>>(emptyList())
    }

    var clients by remember {
        mutableStateOf<List<Customer>>(emptyList())
    }

    var documents by remember {
        mutableStateOf<List<Document>>(emptyList())
    }

    var types by remember {
        mutableStateOf<List<DocumentType>>(emptyList())
    }

    var integrityResults by remember {
        mutableStateOf<List<IntegrityResult>>(emptyList())
    }

    var page by remember {
        mutableStateOf(DocumentPage.AGENTS)
    }

    var selectedAgent by remember {
        mutableStateOf<Agent?>(null)
    }

    var selectedClient by remember {
        mutableStateOf<Customer?>(null)
    }

    var selectedDocument by remember {
        mutableStateOf<Document?>(null)
    }

    var documentImage by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var documentToDelete by remember {
        mutableStateOf<Document?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf("")
    }

    var notice by remember {
        mutableStateOf("")
    }

    suspend fun refreshData() {
        val api = RetrofitClient.apiService

        agents = api.getAgents().requireDocumentBody()
        clients = api.getCustomers().requireDocumentBody()
        documents = api.getDocuments().requireDocumentBody()
        types = api.getDocumentTypes().requireDocumentBody()
        integrityResults = api.getIntegrityResults()
            .requireDocumentBody()
    }

    fun goBack() {
        when (page) {
            DocumentPage.DETAILS -> {
                selectedDocument = null
                documentImage = null
                page = DocumentPage.DOCUMENTS
            }

            DocumentPage.DOCUMENTS -> {
                selectedClient = null
                page = DocumentPage.CLIENTS
            }

            DocumentPage.CLIENTS -> {
                selectedAgent = null
                page = DocumentPage.AGENTS
            }

            DocumentPage.AGENTS -> Unit
        }

        error = ""
    }

    BackHandler(
        enabled = page != DocumentPage.AGENTS && !loading
    ) {
        goBack()
    }

    fun execute(action: suspend () -> Unit) {
        if (loading) return

        scope.launch {
            loading = true
            error = ""

            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Operation failed"
            } finally {
                loading = false
            }
        }
    }

    suspend fun openDocument(doc: Document) {
        selectedDocument = doc
        documentImage = null
        page = DocumentPage.DETAILS

        val name = doc.fileName

        if (!name.isNullOrBlank()) {
            val response = RetrofitClient.apiService
                .getDocumentImage(name)
                .requireDocumentBody()

            val bytes = withContext(Dispatchers.IO) {
                response.use { it.bytes() }
            }

            try {
                documentImage = withContext(Dispatchers.Default) {
                    decodeManagedDocument(bytes)
                }
            } finally {
                bytes.fill(0)
            }
        }
    }

    LaunchedEffect(Unit) {
        execute { refreshData() }
    }

    // Existing delete behavior remains available.
    if (documentToDelete != null) {

        val target = documentToDelete

        AlertDialog(
            onDismissRequest = {
                documentToDelete = null
            },
            title = {
                Text("Delete document?")
            },
            text = {
                Text(
                    "Delete ${target?.fileName ?: "this document"}? " +
                            "This action may remove the document " +
                            "from the active system."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        documentToDelete = null

                        val id = target?.documentId
                        if (id != null) {
                            execute {
                                RetrofitClient.apiService
                                    .deleteDocument(id)
                                    .requireDocumentBody()

                                notice = "Document deleted successfully"
                                goBack()
                                refreshData()
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        documentToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val agentDocs = documents.filter {
        selectedAgent?.agentId != null &&
                it.agentId == selectedAgent?.agentId
    }

    val clientDocs = agentDocs.filter {
        selectedClient?.customerId != null &&
                it.customerId == selectedClient?.customerId
    }

    val agentClients = agentDocs
        .mapNotNull { it.customerId }
        .toSet()
        .mapNotNull { id ->
            clients.firstOrNull { it.customerId == id }
        }
        .sortedBy { it.fullName.orEmpty() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 32.dp
        )
    ) {

        item {
            if (page != DocumentPage.AGENTS) {
                TextButton(
                    enabled = !loading,
                    onClick = ::goBack
                ) {
                    Text("← Back")
                }
            }

            Text(
                "Manage Documents",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            val breadcrumb = when (page) {
                DocumentPage.AGENTS ->
                    "All agents"

                DocumentPage.CLIENTS ->
                    "${selectedAgent?.fullName.orEmpty()} / Clients"

                DocumentPage.DOCUMENTS ->
                    "${selectedAgent?.fullName.orEmpty()} / " +
                            selectedClient?.fullName.orEmpty()

                DocumentPage.DETAILS ->
                    "Document #${selectedDocument?.documentId}"
            }

            Text(
                breadcrumb,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (notice.isNotBlank()) {
                Text(notice)
            }

            OutlinedButton(
                enabled = !loading,
                onClick = {
                    execute { refreshData() }
                }
            ) {
                Text("Refresh")
            }
        }

        // ==================================================
        // PAGE 1 — AGENTS
        // ==================================================

        when (page) {

            DocumentPage.AGENTS -> {

                item {
                    Text(
                        "Agents",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "${agents.size} registered agents",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(
                    agents.sortedBy { it.fullName.orEmpty() },
                    key = { it.agentId ?: -1L }
                ) { agent ->

                    val agentDocuments = documents.filter {
                        it.agentId == agent.agentId
                    }

                    val customerCount = agentDocuments
                        .mapNotNull { it.customerId }
                        .toSet()
                        .size

                    DocumentNavigationCard(
                        title = agent.fullName
                            ?: "Agent #${agent.agentId}",
                        subtitle = agent.email.orEmpty(),
                        details = "$customerCount clients • " +
                                "${agentDocuments.size} documents",
                        enabled = !loading
                    ) {
                        selectedAgent = agent
                        selectedClient = null
                        page = DocumentPage.CLIENTS
                    }
                }
            }

            // ==================================================
            // PAGE 2 — CLIENTS
            // ==================================================

            DocumentPage.CLIENTS -> {

                item {
                    Text(
                        "Clients of ${selectedAgent?.fullName.orEmpty()}",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(
                    agentClients,
                    key = { it.customerId ?: -1L }
                ) { client ->

                    val count = agentDocs.count {
                        it.customerId == client.customerId
                    }

                    DocumentNavigationCard(
                        title = client.fullName
                            ?: "Client #${client.customerId}",
                        subtitle = "NIC: ${client.nic.displayMeta()}",
                        details = "$count submitted documents",
                        enabled = !loading
                    ) {
                        selectedClient = client
                        page = DocumentPage.DOCUMENTS
                    }
                }

                if (agentClients.isEmpty()) {
                    item {
                        Text("No clients with submitted documents.")
                    }
                }
            }

            // ==================================================
            // PAGE 3 — CLIENT DOCUMENTS
            // ==================================================

            DocumentPage.DOCUMENTS -> {

                item {
                    Text(
                        "Documents",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "${clientDocs.size} submissions for " +
                                selectedClient?.fullName.orEmpty()
                    )
                }

                items(
                    clientDocs.sortedByDescending {
                        it.documentId ?: 0L
                    },
                    key = { it.documentId ?: -1L }
                ) { doc ->

                    val type = types.firstOrNull {
                        it.documentTypeId == doc.documentTypeId
                    }

                    val integrity = integrityResults
                        .filter { it.documentId == doc.documentId }
                        .maxByOrNull {
                            it.integrityResultId ?: 0L
                        }

                    DocumentNavigationCard(
                        title = type?.typeName
                            ?: "Document #${doc.documentId}",
                        subtitle = doc.fileName.displayMeta(),
                        details =
                            "Uploaded: ${doc.uploadedAt.displayMeta()} " +
                                    "• Integrity: ${
                                        integrity?.verificationResult.displayMeta()
                                    }",
                        enabled = !loading
                    ) {
                        execute { openDocument(doc) }
                    }
                }

                if (clientDocs.isEmpty()) {
                    item {
                        Text("No documents found for this client.")
                    }
                }
            }

            // ==================================================
            // PAGE 4 — FULL DOCUMENT DETAILS
            // ==================================================

            DocumentPage.DETAILS -> {

                val doc = selectedDocument

                if (doc != null) {

                    val type = types.firstOrNull {
                        it.documentTypeId == doc.documentTypeId
                    }

                    val integrity = integrityResults
                        .filter { it.documentId == doc.documentId }
                        .maxByOrNull {
                            it.integrityResultId ?: 0L
                        }

                    // ORIGINAL IMAGE
                    item {
                        MetadataSection(
                            title = "Original Submitted Document"
                        ) {
                            Text(
                                type?.typeName
                                    ?: "Document #${doc.documentId}",
                                style = MaterialTheme.typography.titleMedium
                            )

                            documentImage?.let { image ->
                                Image(
                                    bitmap = image.asImageBitmap(),
                                    contentDescription = "Original document image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 420.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } ?: Text(
                                if (loading) "Loading image..."
                                else "Image unavailable"
                            )

                            Text(
                                "This image was retrieved using the " +
                                        "existing secure document endpoint.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // CUSTOMER INFORMATION
                    item {
                        MetadataSection(
                            title = "Customer Information"
                        ) {
                            MetadataRow(
                                "Full name",
                                selectedClient?.fullName
                            )
                            MetadataRow(
                                "NIC",
                                selectedClient?.nic
                            )
                            MetadataRow(
                                "Phone",
                                selectedClient?.phone
                            )
                            MetadataRow(
                                "Email",
                                selectedClient?.email
                            )
                            MetadataRow(
                                "Address",
                                selectedClient?.address
                            )
                            MetadataRow(
                                "Customer ID",
                                doc.customerId?.toString()
                            )
                        }
                    }

                    // AGENT AND DOCUMENT IDENTIFICATION
                    item {
                        MetadataSection(
                            title = "Submission Information"
                        ) {
                            MetadataRow(
                                "Submitted by",
                                selectedAgent?.fullName
                            )
                            MetadataRow(
                                "Agent ID",
                                doc.agentId?.toString()
                            )
                            MetadataRow(
                                "Device ID",
                                doc.deviceId?.toString()
                            )
                            MetadataRow(
                                "Document ID",
                                doc.documentId?.toString()
                            )
                            MetadataRow(
                                "Document type",
                                type?.typeName
                            )
                            MetadataRow(
                                "Original filename",
                                doc.fileName
                            )
                            MetadataRow(
                                "Capture source",
                                doc.captureSource
                            )
                            MetadataRow(
                                "Capture status",
                                doc.captureStatus
                            )
                            MetadataRow(
                                "Document status",
                                doc.status
                            )
                        }
                    }

                    // DATE, TIME AND LOCATION
                    item {
                        MetadataSection(
                            title = "Capture Time & Location"
                        ) {
                            MetadataRow(
                                "Captured at",
                                doc.captureDateTime
                            )
                            MetadataRow(
                                "Uploaded at",
                                doc.uploadedAt
                            )
                            MetadataRow(
                                "Location",
                                doc.captureLocation
                            )
                            MetadataRow(
                                "Latitude",
                                doc.captureLatitude?.toString()
                            )
                            MetadataRow(
                                "Longitude",
                                doc.captureLongitude?.toString()
                            )

                            if (
                                doc.captureLatitude != null &&
                                doc.captureLongitude != null
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        // Coordinates are displayed above.
                                        // Map opening can be added separately.
                                    },
                                    enabled = false
                                ) {
                                    Text("Coordinates recorded")
                                }
                            }

                            Text(
                                "Location and capture time are submitted " +
                                        "metadata; they are not independent " +
                                        "proof of physical presence.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // FILE INFORMATION
                    item {
                        MetadataSection(
                            title = "File Information"
                        ) {
                            MetadataRow(
                                "Filename",
                                doc.fileName
                            )
                            MetadataRow(
                                "File size",
                                readableFileSize(doc.fileSizeBytes)
                            )
                            MetadataRow(
                                "MIME type",
                                doc.mimeType
                            )
                            MetadataRow(
                                "Width",
                                doc.imageWidth?.let { "$it px" }
                            )
                            MetadataRow(
                                "Height",
                                doc.imageHeight?.let { "$it px" }
                            )
                            MetadataRow(
                                "Resolution",
                                if (
                                    doc.imageWidth != null &&
                                    doc.imageHeight != null
                                ) {
                                    "${doc.imageWidth} × ${doc.imageHeight}"
                                } else null
                            )
                            MetadataRow(
                                "Image quality",
                                doc.imageQuality
                            )
                        }
                    }

                    // CAPTURE QUALITY
                    item {
                        MetadataSection(
                            title = "Camera Quality Checks"
                        ) {
                            MetadataRow(
                                "Overall quality",
                                doc.qualityStatus
                            )
                            MetadataRow(
                                "Blur check",
                                qualityLabel(doc.blurPassed)
                            )
                            MetadataRow(
                                "Blur score",
                                doc.blurScore?.toString()
                            )
                            MetadataRow(
                                "Brightness check",
                                qualityLabel(doc.brightnessPassed)
                            )
                            MetadataRow(
                                "Brightness score",
                                doc.brightnessScore?.toString()
                            )
                            MetadataRow(
                                "Resolution check",
                                qualityLabel(doc.resolutionPassed)
                            )
                        }
                    }

                    // HASH VERIFICATION
                    item {
                        MetadataSection(
                            title = "SHA-256 Integrity Verification"
                        ) {

                            Text(
                                "Original Mobile Hash",
                                fontWeight = FontWeight.SemiBold
                            )

                            CopyableHash(
                                integrity?.sha256Hash
                            )

                            HorizontalDivider()

                            Text(
                                "Backend Generated Hash",
                                fontWeight = FontWeight.SemiBold
                            )

                            CopyableHash(
                                integrity?.backendSha256Hash
                            )

                            HorizontalDivider()

                            val originalHash = integrity?.sha256Hash
                            val backendHash =
                                integrity?.backendSha256Hash

                            val validHashes =
                                originalHash.isValidSha256() &&
                                        backendHash.isValidSha256()

                            val hashesMatch =
                                validHashes &&
                                        originalHash.equals(
                                            backendHash,
                                            ignoreCase = true
                                        )

                            val statusText = when {
                                !validHashes ->
                                    "Comparison unavailable"

                                hashesMatch ->
                                    "Hashes match"

                                else ->
                                    "Hash mismatch — investigation required"
                            }

                            Text(
                                statusText,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    !validHashes ->
                                        MaterialTheme.colorScheme.onSurfaceVariant

                                    hashesMatch ->
                                        MaterialTheme.colorScheme.primary

                                    else ->
                                        MaterialTheme.colorScheme.error
                                }
                            )

                            Text(
                                "The original hash is submitted from " +
                                        "the device; the backend hash is " +
                                        "calculated from received image bytes.",
                                style = MaterialTheme.typography.bodySmall
                            )

                            MetadataRow(
                                "Initial verification",
                                integrity?.initialVerificationResult
                            )

                            MetadataRow(
                                "Verification result",
                                integrity?.verificationResult
                            )

                            Text(
                                "Final SHA-256 Hash",
                                fontWeight = FontWeight.SemiBold
                            )

                            CopyableHash(
                                integrity?.finalSha256Hash
                            )

                            MetadataRow(
                                "Final verification",
                                integrity?.finalVerificationResult
                            )

                            MetadataRow(
                                "Last verified",
                                integrity?.finalVerifiedAt
                            )

                            MetadataRow(
                                "Integrity processed",
                                integrity?.processedAt
                            )

                            Text(
                                "Matching hashes indicate byte-level " +
                                        "agreement. They do not prove that " +
                                        "the contents of the image are authentic.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ENCRYPTION
                    item {
                        MetadataSection(
                            title = "Secure Storage & Encryption"
                        ) {
                            MetadataRow(
                                "Encryption algorithm",
                                integrity?.encryptionAlgorithm
                            )
                            MetadataRow(
                                "Storage status",
                                integrity?.storageStatus
                            )
                            MetadataRow(
                                "Key version",
                                integrity?.encryptionKeyVersion
                            )
                            MetadataRow(
                                "Encrypted at",
                                integrity?.encryptedAt
                            )
                            MetadataRow(
                                "Stored document path",
                                doc.filePath
                            )
                            Text(
                                "Encryption keys and sensitive encryption " +
                                        "parameters are intentionally not displayed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // PROCESSING
                    item {
                        MetadataSection(
                            title = "Processing & Verification"
                        ) {
                            MetadataRow(
                                "Processing status",
                                doc.processingStatus
                            )
                            MetadataRow(
                                "Document verification",
                                doc.verificationStatus
                            )
                            MetadataRow(
                                "Capture status",
                                doc.captureStatus
                            )
                            MetadataRow(
                                "Database document ID",
                                doc.documentId?.toString()
                            )
                            MetadataRow(
                                "Integrity record ID",
                                integrity?.integrityResultId?.toString()
                            )
                        }
                    }

                    // DELETE — PRESERVE OLD FEATURE
                    item {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                            onClick = {
                                documentToDelete = doc
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor =
                                    MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete Document")
                        }
                    }
                }
            }
        }
    }
}

// ==================================================
// REUSABLE USER-FRIENDLY COMPONENTS
// ==================================================

@Composable
private fun DocumentNavigationCard(
    title: String,
    subtitle: String,
    details: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                details,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "View details →",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MetadataSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            content()
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            value.displayMeta(),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CopyableHash(
    value: String?
) {
    val clipboard = LocalClipboardManager.current

    if (value.isNullOrBlank()) {
        Text(
            "Not available",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodySmall
        )

        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(value))
            }
        ) {
            Text("Copy full hash")
        }
    }
}

// ==================================================
// DATA FORMATTING
// ==================================================

private fun String?.displayMeta(): String =
    this?.takeIf { it.isNotBlank() } ?: "Not available"

private fun String?.isValidSha256(): Boolean =
    this != null &&
            this.matches(Regex("[a-fA-F0-9]{64}"))

private fun qualityLabel(value: Boolean?): String =
    when (value) {
        true -> "Passed"
        false -> "Failed"
        null -> "Not recorded"
    }

private fun readableFileSize(bytes: Long?): String {
    if (bytes == null) return "Not available"

    return when {
        bytes < 1024 ->
            "$bytes bytes"

        bytes < 1024L * 1024L ->
            String.format(
                Locale.US,
                "%.1f KB",
                bytes / 1024.0
            )

        else ->
            String.format(
                Locale.US,
                "%.2f MB",
                bytes / (1024.0 * 1024.0)
            )
    }
}

// ==================================================
// EXISTING API RESPONSE HANDLING
// ==================================================

private fun <T> Response<T>.requireDocumentBody(): T {
    if (!isSuccessful) {
        error("Server returned HTTP ${code()}")
    }

    return requireNotNull(body()) {
        "Server returned an empty response"
    }
}

// ==================================================
// SAFE DOCUMENT PREVIEW
// ==================================================

private fun decodeManagedDocument(
    bytes: ByteArray
): Bitmap {

    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        bounds
    )

    require(
        bounds.outWidth > 0 &&
                bounds.outHeight > 0 &&
                bounds.outWidth.toLong() *
                bounds.outHeight <= 24_000_000L
    ) {
        "Unsupported document image dimensions"
    }

    val sample = BitmapFactory.Options().apply {
        inSampleSize = when {
            bounds.outWidth > 2500 ||
                    bounds.outHeight > 2500 -> 4

            bounds.outWidth > 1300 ||
                    bounds.outHeight > 1300 -> 2

            else -> 1
        }
    }

    return requireNotNull(
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            sample
        )
    ) {
        "Unable to display document image"
    }
}
