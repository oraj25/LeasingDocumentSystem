
package com.leasingdocument.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leasingdocument.app.network.AuditLog
import com.leasingdocument.app.network.AuthSession
import com.leasingdocument.app.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AuditPage {
    OVERVIEW, CATEGORY, DETAILS
}

private enum class AuditCategory(
    val title: String,
    val description: String
) {
    AUTHENTICATION(
        "Authentication & Sessions",
        "Logins, logouts and session activity"
    ),
    DOCUMENTS(
        "Document Operations",
        "Document uploads, submissions and updates"
    ),
    INTEGRITY(
        "Integrity & Security",
        "Hash verification, encryption and security events"
    ),
    ALTERATION(
        "Alteration Analysis",
        "OCR analysis, screening and manual reviews"
    ),
    ADMINISTRATION(
        "Administration",
        "Agent, customer, device and system management"
    ),
    OTHER(
        "Other Events",
        "Other recorded system actions"
    )
}

private fun categoryFor(action: String?): AuditCategory {
    val value = action.orEmpty()
        .uppercase(Locale.ROOT)

    return when {
        listOf(
            "LOGIN", "LOGOUT", "SESSION",
            "AUTH", "SIGN_IN", "SIGN_OUT"
        ).any { it in value } ->
            AuditCategory.AUTHENTICATION

        listOf(
            "ALTERATION", "ANALYSIS",
            "OCR", "MANUAL_REVIEW"
        ).any { it in value } ->
            AuditCategory.ALTERATION

        listOf(
            "INTEGRITY", "HASH", "ENCRYPT",
            "DECRYPT", "TAMPER", "SECURITY"
        ).any { it in value } ->
            AuditCategory.INTEGRITY

        listOf(
            "DOCUMENT", "UPLOAD", "CAPTURE",
            "DOWNLOAD", "FILE"
        ).any { it in value } ->
            AuditCategory.DOCUMENTS

        listOf(
            "ADMIN", "AGENT", "CUSTOMER",
            "DEVICE", "REGISTER",
            "USER", "CONFIG"
        ).any { it in value } ->
            AuditCategory.ADMINISTRATION

        else -> AuditCategory.OTHER
    }
}

private fun failedLog(log: AuditLog): Boolean {
    val status = log.eventStatus.orEmpty()
        .uppercase(Locale.ROOT)

    return status.contains("FAIL") ||
            status.contains("ERROR") ||
            status.contains("DENIED") ||
            status.contains("REJECT")
}

private fun statusDisplay(status: String?): String {
    return status
        ?.replace('_', ' ')
        ?.takeIf { it.isNotBlank() }
        ?: "UNKNOWN"
}

private fun displayTime(timestamp: String?): String {
    if (timestamp.isNullOrBlank()) return "Not recorded"

    return timestamp.replace('T', ' ')
        .substringBefore('.')
}

private fun auditDate(timestamp: String?): String {
    val value = timestamp.orEmpty()
    return if (value.length >= 10) {
        value.substring(0, 10)
    } else {
        "Unknown"
    }
}

@Composable
fun AuditLogsScreen() {

    if (AuthSession.role != "ADMIN") {
        Text(
            "Administrator access required",
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    var logs by remember {
        mutableStateOf<List<AuditLog>>(emptyList())
    }

    var page by remember {
        mutableStateOf(AuditPage.OVERVIEW)
    }

    var selectedCategory by remember {
        mutableStateOf<AuditCategory?>(null)
    }

    var selectedLog by remember {
        mutableStateOf<AuditLog?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf("")
    }

    var search by remember {
        mutableStateOf("")
    }

    var selectedStatus by remember {
        mutableStateOf("ALL")
    }

    suspend fun loadLogs() {
        loading = true
        error = ""

        try {
            val response =
                RetrofitClient.apiService.getAuditLogs()

            if (response.isSuccessful) {
                logs = response.body()
                    .orEmpty()
                    .sortedWith(
                        compareByDescending<AuditLog> {
                            it.createdAt.orEmpty()
                        }.thenByDescending {
                            it.logId ?: 0L
                        }
                    )
            } else {
                error = "Failed to load logs (HTTP ${response.code()})"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Cannot connect to server"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadLogs()
    }

    fun goBack() {
        when (page) {
            AuditPage.DETAILS -> {
                selectedLog = null
                page = AuditPage.CATEGORY
            }
            AuditPage.CATEGORY -> {
                selectedCategory = null
                search = ""
                selectedStatus = "ALL"
                page = AuditPage.OVERVIEW
            }
            AuditPage.OVERVIEW -> Unit
        }
    }

    BackHandler(page != AuditPage.OVERVIEW) {
        goBack()
    }

    val today = LocalDate.now().toString()
    val failedCount = logs.count(::failedLog)
    val todayCount = logs.count {
        auditDate(it.createdAt) == today
    }

    val categoryLogs = logs.filter {
        categoryFor(it.action) == selectedCategory
    }

    val filteredLogs = categoryLogs.filter { log ->

        val matchesStatus = when (selectedStatus) {
            "FAILED" -> failedLog(log)
            "OTHER" -> !failedLog(log)
            else -> true
        }

        val query = search.trim()

        val matchesSearch = query.isBlank() ||
                listOf(
                    log.action,
                    log.description,
                    log.eventStatus,
                    log.adminId?.toString(),
                    log.agentId?.toString(),
                    log.documentId?.toString(),
                    log.deviceId?.toString(),
                    log.ipAddress,
                    log.logId?.toString()
                ).any {
                    it.orEmpty().contains(query, ignoreCase = true)
                }

        matchesStatus && matchesSearch
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {

            if (page != AuditPage.OVERVIEW) {
                TextButton(onClick = ::goBack) {
                    Text("← Back")
                }
            }

            Text(
                "Audit Logs",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                when (page) {
                    AuditPage.OVERVIEW ->
                        "System activity overview"
                    AuditPage.CATEGORY ->
                        selectedCategory?.title.orEmpty()
                    AuditPage.DETAILS ->
                        "Log #${selectedLog?.logId}"
                },
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

            TextButton(
                enabled = !loading,
                onClick = {
                    // Force the existing effect to run by re-entering
                    // the overview; alternatively reopen this screen.
                    page = AuditPage.OVERVIEW
                    selectedLog = null
                    selectedCategory = null
                }
            ) {
                Text("Audit overview")
            }
        }

        // ==========================================
        // PAGE 1 — OVERVIEW
        // ==========================================

        when (page) {

            AuditPage.OVERVIEW -> {

                item {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        AuditStat(
                            "Total Logs",
                            logs.size.toString(),
                            Modifier.weight(1f)
                        )

                        AuditStat(
                            "Failed",
                            failedCount.toString(),
                            Modifier.weight(1f)
                        )

                        AuditStat(
                            "Today",
                            todayCount.toString(),
                            Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Text(
                        "Activity by category",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                // Simple native Compose bar chart.
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {

                            val maximum = AuditCategory.entries
                                .maxOfOrNull { category ->
                                    logs.count {
                                        categoryFor(it.action) == category
                                    }
                                }
                                ?.coerceAtLeast(1) ?: 1

                            AuditCategory.entries.forEach { category ->
                                val count = logs.count {
                                    categoryFor(it.action) == category
                                }

                                Text(
                                    "${category.title} · $count",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                LinearProgressIndicator(
                                    progress = {
                                        count.toFloat() / maximum
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Browse categories",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(AuditCategory.entries.toList()) { category ->

                    val categoryItems = logs.filter {
                        categoryFor(it.action) == category
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCategory = category
                                search = ""
                                selectedStatus = "ALL"
                                page = AuditPage.CATEGORY
                            },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(5.dp)
                        ) {

                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    category.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    categoryItems.size.toString(),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                category.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val failures =
                                categoryItems.count(::failedLog)

                            Text(
                                "${categoryItems.size} events • $failures failed",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                "View category →",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ==========================================
            // PAGE 2 — CATEGORY EVENTS
            // ==========================================

            AuditPage.CATEGORY -> {

                item {
                    Text(
                        "${categoryLogs.size} recorded events",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = {
                            Text("Search logs")
                        },
                        placeholder = {
                            Text("Action, description, ID or IP")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        listOf(
                            "ALL" to "All",
                            "FAILED" to "Failed",
                            "OTHER" to "Other"
                        ).forEach { (value, label) ->

                            FilterChip(
                                selected = selectedStatus == value,
                                onClick = {
                                    selectedStatus = value
                                },
                                label = {
                                    Text(label)
                                }
                            )
                        }
                    }

                    Text(
                        "Showing ${filteredLogs.size} of ${categoryLogs.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    AuditActivityChart(categoryLogs)
                }

                if (filteredLogs.isEmpty()) {
                    item {
                        Text("No logs match this filter.")
                    }
                }

                items(
                    filteredLogs,
                    key = { it.logId ?: -1L }
                ) { log ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedLog = log
                                page = AuditPage.DETAILS
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(5.dp)
                        ) {

                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    log.action
                                        ?.replace('_', ' ')
                                        ?: "Unknown action",
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )

                                AuditStatus(log.eventStatus)
                            }

                            Text(
                                log.description ?: "No description",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                displayTime(log.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                "View full log →",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // ==========================================
            // PAGE 3 — FULL LOG DETAILS
            // ==========================================

            AuditPage.DETAILS -> {

                selectedLog?.let { log ->

                    item {
                        Card {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(12.dp)
                            ) {

                                Text(
                                    log.action
                                        ?.replace('_', ' ')
                                        ?: "Unknown Action",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                AuditStatus(log.eventStatus)

                                HorizontalDivider()

                                Text(
                                    "Event description",
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    log.description
                                        ?: "No description recorded"
                                )
                            }
                        }
                    }

                    item {
                        AuditDetailSection(
                            title = "Event Information",
                            rows = listOf(
                                "Log ID" to log.logId?.toString(),
                                "Action" to log.action,
                                "Event status" to log.eventStatus,
                                "Category" to categoryFor(log.action).title,
                                "Date & time" to displayTime(log.createdAt)
                            )
                        )
                    }

                    item {
                        AuditDetailSection(
                            title = "User Information",
                            rows = listOf(
                                "Administrator ID" to log.adminId?.toString(),
                                "Agent ID" to log.agentId?.toString()
                            )
                        )
                    }

                    item {
                        AuditDetailSection(
                            title = "Related Records",
                            rows = listOf(
                                "Document ID" to log.documentId?.toString(),
                                "Device ID" to log.deviceId?.toString()
                            )
                        )
                    }

                    item {
                        AuditDetailSection(
                            title = "Network Information",
                            rows = listOf(
                                "IP Address" to log.ipAddress
                            )
                        )
                    }

                    item {
                        Text(
                            "This is the complete information available " +
                                    "in the current audit-log database record. " +
                                    "Missing values were not recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// DASHBOARD COMPONENTS
// ==========================================

@Composable
private fun AuditStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun AuditStatus(status: String?) {

    val failed = failedLog(
        AuditLog(
            logId = null,
            adminId = null,
            agentId = null,
            documentId = null,
            deviceId = null,
            action = null,
            eventStatus = status,
            description = null,
            ipAddress = null,
            createdAt = null
        )
    )

    val display = statusDisplay(status)

    val color = if (failed) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Text(
        display,
        color = color,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun AuditDetailSection(
    title: String,
    rows: List<Pair<String, String?>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            rows.forEach { (label, value) ->
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        value?.takeIf { it.isNotBlank() }
                            ?: "Not recorded"
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditActivityChart(logs: List<AuditLog>) {

    val dates = remember(logs) {
        val now = LocalDate.now()

        (6 downTo 0).map { daysAgo ->
            now.minusDays(daysAgo.toLong())
        }
    }

    val counts = dates.map { date ->
        logs.count {
            auditDate(it.createdAt) == date.toString()
        }
    }

    val maximum = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(9.dp)
        ) {

            Text(
                "Activity — last 7 days",
                style = MaterialTheme.typography.titleMedium
            )

            dates.forEachIndexed { index, date ->

                val label = date.format(
                    DateTimeFormatter.ofPattern(
                        "MMM d", Locale.ENGLISH
                    )
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        label,
                        modifier = Modifier.width(54.dp),
                        style = MaterialTheme.typography.labelSmall
                    )

                    LinearProgressIndicator(
                        progress = {
                            counts[index].toFloat() / maximum
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        counts[index].toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
