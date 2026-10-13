
package com.leasingdocument.app.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leasingdocument.app.network.*
import com.leasingdocument.app.ocr.DocumentOcrProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import java.security.MessageDigest

private enum class AdminPage {
    AGENTS, CLIENTS, DOCUMENTS, DETAILS
}

private const val CURRENT_ANALYSIS_VERSION = "document-analysis-6"

@Composable
fun AlterationAnalysisScreen() {

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
    var results by remember {
        mutableStateOf<List<AlterationResult>>(emptyList())
    }

    var page by remember {
        mutableStateOf(AdminPage.AGENTS)
    }

    var agent by remember { mutableStateOf<Agent?>(null) }
    var client by remember { mutableStateOf<Customer?>(null) }
    var document by remember { mutableStateOf<Document?>(null) }

    var selectedResult by remember {
        mutableStateOf<AlterationResult?>(null)
    }

    var report by remember {
        mutableStateOf<JSONObject?>(null)
    }

    var sourceImage by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var highlightedImage by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var referenceId by remember {
        mutableStateOf<Long?>(null)
    }

    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var reviewNotes by remember { mutableStateOf("") }
    var confirmedReview by remember { mutableStateOf(false) }

    fun goBack() {
        when (page) {
            AdminPage.DETAILS -> {
                page = AdminPage.DOCUMENTS
                document = null
                report = null
                sourceImage = null
                highlightedImage = null
            }
            AdminPage.DOCUMENTS -> {
                page = AdminPage.CLIENTS
                client = null
            }
            AdminPage.CLIENTS -> {
                page = AdminPage.AGENTS
                agent = null
            }
            AdminPage.AGENTS -> Unit
        }
        errorMessage = ""
    }

    BackHandler(
        enabled = page != AdminPage.AGENTS && !busy
    ) {
        goBack()
    }

    suspend fun refresh() {
        val api = RetrofitClient.apiService

        agents = api.getAgents().requiredBody()
        clients = api.getCustomers().requiredBody()
        documents = api.getDocuments().requiredBody()
        types = api.getDocumentTypes().requiredBody()
        results = api.getAlterationResults().requiredBody()
    }

    suspend fun loadReport(result: AlterationResult) {
        selectedResult = result
        report = null
        highlightedImage = null

        if (
            result.analysisStatus !in listOf(
                "COMPLETED", "INCOMPLETE"
            )
        ) return

        val body = RetrofitClient.apiService.getAnalysisReport(
            requireNotNull(result.alterationResultId)
        ).requiredBody()

        val parsed = withContext(Dispatchers.IO) {
            body.use {
                JSONObject(it.string())
            }
        }

        report = parsed

        highlightedImage = withContext(Dispatchers.Default) {
            parsed.optString("highlightedImageBase64")
                .takeIf { it.isNotBlank() }
                ?.let(::decodeDashboardBitmap)
        }
    }

    suspend fun openDocument(doc: Document) {
        document = doc

        selectedResult = results
            .filter { it.documentId == doc.documentId }
            .maxByOrNull { it.alterationResultId ?: 0L }

        report = null
        sourceImage = null
        highlightedImage = null
        referenceId = null
        reviewNotes = ""
        confirmedReview = false
        page = AdminPage.DETAILS

        val fileName = requireNotNull(doc.fileName) {
            "This document has no stored image filename"
        }

        val body = RetrofitClient.apiService
            .getDocumentImage(fileName)
            .requiredBody()

        val bytes = withContext(Dispatchers.IO) {
            body.use { it.bytes() }
        }

        try {
            sourceImage = withContext(Dispatchers.Default) {
                decodeDashboardBitmap(bytes)
            }
        } finally {
            bytes.fill(0)
        }

        selectedResult?.let {
            loadReport(it)
        }
    }

    suspend fun runAnalysis(doc: Document) {
        val fileName = requireNotNull(doc.fileName) {
            "Missing document filename"
        }

        val body = RetrofitClient.apiService
            .getDocumentImage(fileName)
            .requiredBody()

        val bytes = withContext(Dispatchers.IO) {
            body.use { it.bytes() }
        }

        try {
            val ocrJson = DocumentOcrProcessor.extract(bytes)

            val digest = withContext(Dispatchers.Default) {
                MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") {
                        "%02x".format(it.toInt() and 255)
                    }
            }

            val newResult = RetrofitClient.apiService.analyzeDocument(
                requireNotNull(doc.documentId),
                AnalysisRequest(
                    digest,
                    ocrJson,
                    referenceId
                )
            ).requiredBody()

            refresh()
            loadReport(newResult)

        } finally {
            bytes.fill(0)
        }
    }

    fun execute(action: suspend () -> Unit) {
        if (busy) return

        scope.launch {
            busy = true
            errorMessage = ""

            try {
                action()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                errorMessage = e.message ?: "Request failed"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        execute { refresh() }
    }

    val agentDocuments = documents.filter {
        it.agentId != null && it.agentId == agent?.agentId
    }

    val clientDocuments = agentDocuments.filter {
        it.customerId != null &&
                it.customerId == client?.customerId
    }

    val uniqueClients = agentDocuments
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
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {

        item {
            if (page != AdminPage.AGENTS) {
                TextButton(
                    enabled = !busy,
                    onClick = ::goBack
                ) {
                    Text("← Back")
                }
            }

            Text(
                "Document Administration",
                style = MaterialTheme.typography.headlineSmall
            )

            val location = when (page) {
                AdminPage.AGENTS -> "Agents"
                AdminPage.CLIENTS ->
                    "${agent?.fullName.orEmpty()} / Clients"
                AdminPage.DOCUMENTS ->
                    "${agent?.fullName.orEmpty()} / " +
                            "${client?.fullName.orEmpty()} / Documents"
                AdminPage.DETAILS ->
                    "Document #${document?.documentId} / Details"
            }

            Text(
                location,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (errorMessage.isNotBlank()) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedButton(
                enabled = !busy,
                onClick = {
                    execute { refresh() }
                }
            ) {
                Text("Refresh")
            }
        }

        when (page) {

            // ============================================
            // PAGE 1 — AGENTS
            // ============================================

            AdminPage.AGENTS -> {

                item {
                    Text(
                        "Select an agent",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(
                    agents.filter { a ->
                        documents.any { it.agentId == a.agentId }
                    },
                    key = { it.agentId ?: -1L }
                ) { a ->

                    val count = documents.count {
                        it.agentId == a.agentId
                    }

                    AdminNavCard(
                        a.fullName ?: "Agent #${a.agentId}",
                        "${a.email.orEmpty()} • $count documents",
                        !busy
                    ) {
                        agent = a
                        page = AdminPage.CLIENTS
                    }
                }

                if (
                    agents.none { a ->
                        documents.any { it.agentId == a.agentId }
                    }
                ) {
                    item {
                        Text("No agent submissions found.")
                    }
                }
            }

            // ============================================
            // PAGE 2 — CLIENTS
            // ============================================

            AdminPage.CLIENTS -> {

                item {
                    Text(
                        "Clients submitted by this agent",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(
                    uniqueClients,
                    key = { it.customerId ?: -1L }
                ) { c ->

                    val count = agentDocuments.count {
                        it.customerId == c.customerId
                    }

                    AdminNavCard(
                        c.fullName ?: "Client #${c.customerId}",
                        "NIC: ${c.nic ?: "Not entered"} • " +
                                "$count documents",
                        !busy
                    ) {
                        client = c
                        page = AdminPage.DOCUMENTS
                    }
                }

                if (uniqueClients.isEmpty()) {
                    item {
                        Text(
                            "No registered clients found for this agent's documents."
                        )
                    }
                }
            }

            // ============================================
            // PAGE 3 — DOCUMENTS
            // ============================================

            AdminPage.DOCUMENTS -> {

                item {
                    Text(
                        "Documents for ${client?.fullName.orEmpty()}",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(
                    clientDocuments.sortedByDescending {
                        it.documentId ?: 0L
                    },
                    key = { it.documentId ?: -1L }
                ) { doc ->

                    val type = types.firstOrNull {
                        it.documentTypeId == doc.documentTypeId
                    }

                    val latest = results
                        .filter { it.documentId == doc.documentId }
                        .maxByOrNull {
                            it.alterationResultId ?: 0L
                        }

                    AdminNavCard(
                        type?.typeName ?: "Document #${doc.documentId}",
                        "#${doc.documentId} • " +
                                "${doc.uploadedAt ?: "Date unavailable"} • " +
                                "${latest?.riskLevel ?: latest?.analysisStatus ?: "Not analysed"}",
                        !busy
                    ) {
                        execute { openDocument(doc) }
                    }
                }

                if (clientDocuments.isEmpty()) {
                    item {
                        Text(
                            "This agent has no documents for this client."
                        )
                    }
                }
            }

            // ============================================
            // PAGE 4 — DOCUMENT DETAILS
            // ============================================

            AdminPage.DETAILS -> {

                val doc = document

                if (doc != null) {

                    item {

                        Text(
                            "Client details",
                            style = MaterialTheme.typography.titleLarge
                        )

                        DashboardTable(
                            listOf(
                                Triple(
                                    "Name",
                                    client?.fullName.orEmpty(),
                                    "Registered"
                                ),
                                Triple(
                                    "NIC",
                                    client?.nic.orEmpty(),
                                    "Registered"
                                ),
                                Triple(
                                    "Phone",
                                    client?.phone.orEmpty(),
                                    "Registered"
                                ),
                                Triple(
                                    "Email",
                                    client?.email.orEmpty(),
                                    "Registered"
                                ),
                                Triple(
                                    "Address",
                                    client?.address.orEmpty(),
                                    "Registered"
                                ),
                                Triple(
                                    "Submitted by",
                                    agent?.fullName.orEmpty(),
                                    "Agent"
                                )
                            ),
                            "Field",
                            "Value",
                            "Source"
                        )
                    }

                    item {

                        val type = types.firstOrNull {
                            it.documentTypeId == doc.documentTypeId
                        }

                        Text(
                            "Document details",
                            style = MaterialTheme.typography.titleLarge
                        )

                        DashboardTable(
                            listOf(
                                Triple(
                                    "Document",
                                    "#${doc.documentId}",
                                    ""
                                ),
                                Triple(
                                    "Type",
                                    type?.typeName ?: "Unknown",
                                    ""
                                ),
                                Triple(
                                    "Captured",
                                    doc.captureDateTime ?: "Unknown",
                                    ""
                                ),
                                Triple(
                                    "Quality",
                                    doc.qualityStatus ?: "Unknown",
                                    ""
                                ),
                                Triple(
                                    "Processing",
                                    doc.processingStatus ?: "Unknown",
                                    ""
                                )
                            ),
                            "Property",
                            "Value",
                            ""
                        )
                    }

                    item {
                        Text(
                            "Submitted original image",
                            style = MaterialTheme.typography.titleLarge
                        )

                        sourceImage?.let {
                            DashboardImage(it)
                        } ?: Text("Image not loaded")
                    }

                    item {

                        val latest = selectedResult

                        Text(
                            "Alteration analysis",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            "Status: ${latest?.analysisStatus ?: "Not analysed"}"
                        )

                        Text(
                            "Risk: ${latest?.riskLevel ?: "Not available"} • " +
                                    "Score: ${latest?.riskScore ?: "Not available"}"
                        )

                        Text(
                            "Results are review indicators, not proof of authenticity."
                        )

                        val typeCode = types.firstOrNull {
                            it.documentTypeId == doc.documentTypeId
                        }?.typeCode

                        val references =
                            if (
                                typeCode in setOf(
                                    "SALARY_SLIP",
                                    "UTILITY_BILL"
                                )
                            ) {
                                emptyList()
                            } else {
                                documents.filter { other ->
                                    other.documentId != null &&
                                            doc.documentId != null &&
                                            other.documentId < doc.documentId &&
                                            other.customerId == doc.customerId &&
                                            other.documentTypeId == doc.documentTypeId &&
                                            other.captureSource == "LIVE_CAMERA" &&
                                            other.qualityStatus == "PASSED" &&
                                            other.blurPassed == true &&
                                            other.brightnessPassed == true &&
                                            other.resolutionPassed == true &&
                                            results.filter {
                                                it.documentId == other.documentId
                                            }.maxByOrNull {
                                                it.alterationResultId ?: 0L
                                            }?.let {
                                                it.analysisStatus == "COMPLETED" &&
                                                        it.finalDecision == "APPROVED" &&
                                                        it.reviewStatus == "REVIEWED" &&
                                                        it.algorithmVersion in setOf(
                                                    "document-analysis-4",
                                                    "document-analysis-5",
                                                    CURRENT_ANALYSIS_VERSION
                                                )
                                            } == true
                                }
                            }

                        var expanded by remember(doc.documentId) {
                            mutableStateOf(false)
                        }

                        if (references.isNotEmpty()) {

                            Box {

                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = { expanded = true }
                                ) {
                                    Text(
                                        referenceId?.let {
                                            "Reference: document #$it"
                                        } ?: "No reference (single-image screening)"
                                    )
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = {
                                        expanded = false
                                    }
                                ) {

                                    DropdownMenuItem(
                                        text = { Text("No reference") },
                                        onClick = {
                                            referenceId = null
                                            expanded = false
                                        }
                                    )

                                    references.forEach { r ->

                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "Approved document #${r.documentId}"
                                                )
                                            },
                                            onClick = {
                                                referenceId = r.documentId
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            enabled = !busy &&
                                    doc.captureSource == "LIVE_CAMERA" &&
                                    doc.qualityStatus == "PASSED" &&
                                    doc.blurPassed == true &&
                                    doc.brightnessPassed == true &&
                                    doc.resolutionPassed == true,
                            onClick = {
                                execute {
                                    runAnalysis(doc)
                                }
                            }
                        ) {
                            Text(
                                if (latest == null)
                                    "Run OCR and alteration detection"
                                else
                                    "Run new OCR and alteration detection"
                            )
                        }

                        if (
                            latest != null &&
                            report == null &&
                            latest.analysisStatus in setOf(
                                "COMPLETED",
                                "INCOMPLETE"
                            )
                        ) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    execute {
                                        loadReport(latest)
                                    }
                                }
                            ) {
                                Text("Load existing report")
                            }
                        }
                    }

                    // ====================================
                    // OCR + CUSTOMER COMPARISON
                    // ====================================

                    report?.let { data ->

                        item {

                            Text(
                                "Registered information vs extracted OCR",
                                style = MaterialTheme.typography.titleLarge
                            )

                            DashboardComparisonTable(client, data)
                        }

                        item {

                            Text(
                                "Extracted document fields",
                                style = MaterialTheme.typography.titleLarge
                            )

                            val fields = data
                                .optJSONObject("ocr")
                                ?.optJSONObject("fields")

                            if (fields != null) {

                                val rows = mutableListOf<
                                        Triple<String, String, String>
                                        >()

                                val keys = fields.keys()

                                while (keys.hasNext()) {
                                    val key = keys.next()

                                    rows.add(
                                        Triple(
                                            key,
                                            jsonCellValue(fields.opt(key)),
                                            "OCR candidate"
                                        )
                                    )
                                }

                                DashboardTable(
                                    rows,
                                    "Field",
                                    "Extracted",
                                    "Source"
                                )

                            } else {
                                Text("No OCR fields returned")
                            }
                        }

                        // ================================
                        // ALTERATION FINDINGS
                        // ================================

                        item {

                            Text(
                                "Alteration findings",
                                style = MaterialTheme.typography.titleLarge
                            )

                            highlightedImage?.let {
                                DashboardImage(it)
                            }

                            val findings = data
                                .optJSONObject("risk_details")
                                ?.optJSONArray("findings")

                            if (
                                findings == null ||
                                findings.length() == 0
                            ) {
                                Text(
                                    "No localized findings reported. " +
                                            "This does not verify authenticity."
                                )
                            } else {

                                val rows = (
                                        0 until minOf(
                                            findings.length(), 30
                                        )
                                        ).mapNotNull { i ->

                                        findings.optJSONObject(i)?.let { f ->
                                            Triple(
                                                f.optString(
                                                    "region", "Unknown"
                                                ),
                                                f.optString(
                                                    "reason",
                                                    f.optString("code")
                                                ),
                                                f.optString("score")
                                            )
                                        }
                                    }

                                DashboardTable(
                                    rows,
                                    "Region",
                                    "Finding",
                                    "Score"
                                )
                            }
                        }

                        // ================================
                        // MANUAL ADMIN REVIEW
                        // ================================

                        item {

                            Text(
                                "Administrator review",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text(
                                "Decision: ${
                                    selectedResult?.finalDecision
                                        ?: "Not recorded"
                                }"
                            )

                            if (
                                selectedResult?.finalDecision == null &&
                                selectedResult?.analysisStatus in setOf(
                                    "COMPLETED", "INCOMPLETE"
                                )
                            ) {

                                OutlinedTextField(
                                    value = reviewNotes,
                                    onValueChange = {
                                        if (it.length <= 1000) {
                                            reviewNotes = it
                                        }
                                    },
                                    label = {
                                        Text("Review notes")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {

                                    Checkbox(
                                        checked = confirmedReview,
                                        onCheckedChange = {
                                            confirmedReview = it
                                        }
                                    )

                                    Text(
                                        "I reviewed the image and OCR evidence"
                                    )
                                }

                                val status = selectedResult?.analysisStatus

                                val onlyReferral =
                                    status == "INCOMPLETE" ||
                                            selectedResult?.riskLevel == "HIGH" ||
                                            selectedResult?.algorithmVersion !=
                                            CURRENT_ANALYSIS_VERSION ||
                                            data.optString(
                                                "verificationStatus"
                                            ) != "REFERENCE_COMPARED"

                                val choices = if (onlyReferral) {
                                    listOf("REFERRED_FOR_INVESTIGATION")
                                } else {
                                    listOf(
                                        "APPROVED",
                                        "REJECTED",
                                        "REFERRED_FOR_INVESTIGATION"
                                    )
                                }

                                choices.forEach { decision ->

                                    Button(
                                        enabled = !busy &&
                                                confirmedReview &&
                                                reviewNotes.isNotBlank(),
                                        onClick = {

                                            execute {

                                                val updated =
                                                    RetrofitClient.apiService
                                                        .reviewAnalysis(
                                                            requireNotNull(
                                                                selectedResult
                                                                    ?.alterationResultId
                                                            ),
                                                            AnalysisReviewRequest(
                                                                decision,
                                                                reviewNotes
                                                            )
                                                        )
                                                        .requiredBody()

                                                selectedResult = updated
                                                refresh()
                                            }
                                        }
                                    ) {

                                        Text(
                                            decision.replace('_', ' ')
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
        }
    }
}

// =====================================================
// UI HELPERS
// =====================================================

@Composable
private fun AdminNavCard(
    title: String,
    subtitle: String,
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
        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {

            Text(
                title,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "Open →",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DashboardImage(bitmap: Bitmap) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Captured document",
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun DashboardComparisonTable(
    customer: Customer?,
    data: JSONObject
) {
    val check = data.optJSONObject("customerCrossCheck")
    val fields = check?.optJSONObject("fields")

    val nic = fields?.optJSONObject("nic")
    val name = fields?.optJSONObject("fullName")

    val rows = listOf(
        Triple(
            "NIC",
            customer?.nic ?: "Not registered",
            compareText(nic)
        ),
        Triple(
            "Full name",
            customer?.fullName ?: "Not registered",
            compareText(name)
        )
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        Text(
            "Customer comparison: ${
                check?.optString("status") ?: "NOT AVAILABLE"
            }"
        )

        DashboardTable(
            rows,
            "Field",
            "Registered",
            "OCR / comparison"
        )

        if (check == null) {
            Text(
                "This report has no customer cross-check. " +
                        "Run new analysis.",
                color = MaterialTheme.colorScheme.error
            )
        }

        Text(
            "OCR can be incorrect. A match is not proof of identity.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun compareText(field: JSONObject?): String {
    if (field == null) return "Not assessed"

    val candidates =
        field.optJSONArray("extractedCandidates")

    val extracted =
        if (
            candidates == null ||
            candidates.length() == 0
        ) {
            "Not extracted"
        } else {
            (0 until candidates.length()).joinToString("; ") {
                candidates.optString(it)
            }
        }

    return "$extracted — ${field.optString("status", "NOT_CHECKED")}"
}

@Composable
private fun DashboardTable(
    rows: List<Triple<String, String, String>>,
    col1: String,
    col2: String,
    col3: String
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        DashboardTableRow(
            col1, col2, col3, true
        )

        rows.forEachIndexed { index, row ->

            if (index > 0) {
                HorizontalDivider()
            }

            DashboardTableRow(
                row.first,
                row.second,
                row.third,
                false
            )
        }
    }
}

@Composable
private fun DashboardTableRow(
    a: String,
    b: String,
    c: String,
    header: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        Text(
            a.ifBlank { "—" },
            modifier = Modifier.weight(1f),
            fontWeight =
                if (header) FontWeight.Bold
                else FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            b.ifBlank { "—" },
            modifier = Modifier.weight(1.25f),
            fontWeight =
                if (header) FontWeight.Bold
                else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            c.ifBlank { "—" },
            modifier = Modifier.weight(1.2f),
            fontWeight =
                if (header) FontWeight.Bold
                else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun jsonCellValue(value: Any?): String =
    when (value) {

        null, JSONObject.NULL ->
            "Not extracted"

        is JSONArray ->
            (0 until value.length()).joinToString("; ") {
                value.optString(it)
            }

        is JSONObject ->
            value.toString()

        else ->
            value.toString()
    }

private suspend fun <T> Response<T>.requiredBody(): T {

    if (!isSuccessful) {

        val raw = errorBody()
            ?.use { it.string() }
            .orEmpty()

        val detail = runCatching {
            val json = JSONObject(raw)

            json.optString("detail").ifBlank {
                json.optString("message")
            }
        }.getOrDefault("")

        error(
            detail.ifBlank {
                "Server returned HTTP ${code()}"
            }
        )
    }

    return requireNotNull(body()) {
        "Server returned an empty response"
    }
}

private fun decodeDashboardBitmap(
    encoded: String
): Bitmap =
    decodeDashboardBitmap(
        Base64.decode(encoded, Base64.DEFAULT)
    )

private fun decodeDashboardBitmap(
    bytes: ByteArray
): Bitmap {

    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size, options
    )

    require(
        options.outWidth > 0 &&
                options.outHeight > 0 &&
                options.outWidth.toLong() *
                options.outHeight <= 12_000_000L
    ) {
        "Invalid document image"
    }

    return requireNotNull(
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size
        )
    ) {
        "Unable to read document image"
    }
}
