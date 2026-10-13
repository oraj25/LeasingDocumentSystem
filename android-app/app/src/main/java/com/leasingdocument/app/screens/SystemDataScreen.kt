
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leasingdocument.app.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response
import java.util.Locale

private enum class SystemPage {
    OVERVIEW, CATEGORY, DETAILS
}

private enum class SystemCategory(
    val title: String,
    val description: String
) {
    CUSTOMERS(
        "Customers",
        "Registered customer information"
    ),
    DOCUMENT_TYPES(
        "Document Types",
        "Supported document categories and requirements"
    ),
    DEVICES(
        "Devices",
        "Registered devices and agent assignments"
    ),
    SESSIONS(
        "Sessions",
        "Administrator and agent login sessions"
    )
}

private data class SystemRecord(
    val key: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val fields: List<Pair<String, String?>>
)

@Composable
fun SystemDataScreen() {

    if (AuthSession.role != "ADMIN") {
        Text(
            "Administrator access required",
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    val scope = rememberCoroutineScope()

    var customers by remember {
        mutableStateOf<List<Customer>>(emptyList())
    }

    var types by remember {
        mutableStateOf<List<DocumentType>>(emptyList())
    }

    var devices by remember {
        mutableStateOf<List<Device>>(emptyList())
    }

    var sessions by remember {
        mutableStateOf<List<Session>>(emptyList())
    }

    var agents by remember {
        mutableStateOf<List<Agent>>(emptyList())
    }

    var page by remember {
        mutableStateOf(SystemPage.OVERVIEW)
    }

    var category by remember {
        mutableStateOf<SystemCategory?>(null)
    }

    var selectedRecord by remember {
        mutableStateOf<SystemRecord?>(null)
    }

    var search by remember {
        mutableStateOf("")
    }

    var statusFilter by remember {
        mutableStateOf("ALL")
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf("")
    }

    suspend fun refresh() {
        loading = true
        error = ""

        try {
            val api = RetrofitClient.apiService

            customers = api.getCustomers().systemBody()
            types = api.getDocumentTypes().systemBody()
            devices = api.getDevices().systemBody()
            sessions = api.getSessions().systemBody()
            agents = api.getAgents().systemBody()

        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Failed to load system data"
        }
    }

    fun goBack() {
        when (page) {
            SystemPage.DETAILS -> {
                selectedRecord = null
                page = SystemPage.CATEGORY
            }

            SystemPage.CATEGORY -> {
                category = null
                search = ""
                statusFilter = "ALL"
                page = SystemPage.OVERVIEW
            }

            SystemPage.OVERVIEW -> Unit
        }
    }

    BackHandler(
        enabled = page != SystemPage.OVERVIEW && !loading
    ) {
        goBack()
    }

    // Build the category records using real API data.
    val allRecords = remember(
        customers, types, devices, sessions, agents, category
    ) {
        when (category) {

            SystemCategory.CUSTOMERS ->
                customers.map { customer ->
                    SystemRecord(
                        key = "customer-${customer.customerId}",
                        title = customer.fullName
                            ?: "Customer #${customer.customerId}",
                        subtitle = "NIC: ${customer.nic.orEmpty()}",
                        status = "REGISTERED",
                        fields = listOf(
                            "Customer ID" to customer.customerId?.toString(),
                            "Full name" to customer.fullName,
                            "NIC number" to customer.nic,
                            "Phone" to customer.phone,
                            "Email address" to customer.email,
                            "Address" to customer.address,
                            "Registered at" to customer.createdAt
                        )
                    )
                }

            SystemCategory.DOCUMENT_TYPES ->
                types.map { type ->
                    SystemRecord(
                        key = "type-${type.documentTypeId}",
                        title = type.typeName
                            ?: "Document Type #${type.documentTypeId}",
                        subtitle = type.typeCode.orEmpty(),
                        status = type.status ?: "UNKNOWN",
                        fields = listOf(
                            "Document type ID" to
                                    type.documentTypeId?.toString(),
                            "Type code" to type.typeCode,
                            "Type name" to type.typeName,
                            "Description" to type.description,
                            "Required document" to
                                    yesNo(type.requiredFlag),
                            "Alteration analysis enabled" to
                                    yesNo(type.analysisEnabled),
                            "Status" to type.status
                        )
                    )
                }

            SystemCategory.DEVICES ->
                devices.map { device ->

                    val assignedAgent = agents.firstOrNull {
                        it.agentId == device.assignedAgentId
                    }

                    SystemRecord(
                        key = "device-${device.deviceId}",
                        title = device.deviceNumber
                            ?: "Device #${device.deviceId}",
                        subtitle = device.deviceType
                            ?: "Device type unavailable",
                        status = device.status ?: "UNKNOWN",
                        fields = listOf(
                            "Device ID" to device.deviceId?.toString(),
                            "Device number" to device.deviceNumber,
                            "Device type" to device.deviceType,
                            "Device identifier" to device.deviceIdentifier,
                            "Identifier type" to device.identifierType,
                            "IMEI number" to device.imeiNumber,
                            "Assigned agent" to assignedAgent?.fullName,
                            "Assigned agent ID" to
                                    device.assignedAgentId?.toString(),
                            "Operating system version" to device.osVersion,
                            "Status" to device.status,
                            "Assigned date" to device.assignedDate,
                            "Last seen" to device.lastSeenAt
                        )
                    )
                }

            SystemCategory.SESSIONS ->
                sessions.map { session ->

                    val agent = agents.firstOrNull {
                        it.agentId == session.agentId
                    }

                    val actor = when {
                        session.adminId != null ->
                            "Administrator #${session.adminId}"

                        session.agentId != null ->
                            agent?.fullName
                                ?: "Agent #${session.agentId}"

                        else -> "Unknown user"
                    }

                    SystemRecord(
                        key = "session-${session.sessionId}",
                        title = "Session #${session.sessionId}",
                        subtitle = actor,
                        status = session.status ?: "UNKNOWN",
                        fields = listOf(
                            "Session ID" to session.sessionId?.toString(),
                            "User" to actor,
                            "Administrator ID" to
                                    session.adminId?.toString(),
                            "Agent ID" to session.agentId?.toString(),
                            "Device ID" to session.deviceId?.toString(),
                            "Login time" to session.loginTime,
                            "Logout time" to session.logoutTime,
                            "Status" to session.status
                        )
                    )
                }

            null -> emptyList()
        }
    }

    val statusOptions = listOf("ALL") +
            allRecords.map { it.status.uppercase(Locale.ROOT) }
                .distinct()
                .sorted()

    val filteredRecords = allRecords.filter { record ->
        val statusMatches =
            statusFilter == "ALL" ||
                    record.status.equals(
                        statusFilter, ignoreCase = true
                    )

        val query = search.trim()

        val textMatches = query.isBlank() ||
                record.title.contains(query, true) ||
                record.subtitle.contains(query, true) ||
                record.fields.any { (label, value) ->
                    label.contains(query, true) ||
                            value.orEmpty().contains(query, true)
                }

        statusMatches && textMatches
    }

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

            if (page != SystemPage.OVERVIEW) {
                TextButton(
                    enabled = !loading,
                    onClick = ::goBack
                ) {
                    Text("← Back")
                }
            }

            Text(
                "System Data",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                when (page) {
                    SystemPage.OVERVIEW ->
                        "System records and operational information"

                    SystemPage.CATEGORY ->
                        category?.title.orEmpty()

                    SystemPage.DETAILS ->
                        selectedRecord?.title.orEmpty()
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

            OutlinedButton(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        try {
                            refresh()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message
                                ?: "Refresh failed"
                        }
                    }
                }
            ) {
                Text("Refresh data")
            }
        }

        // ======================================
        // OVERVIEW PAGE
        // ======================================

        when (page) {

            SystemPage.OVERVIEW -> {

                item {
                    Text(
                        "Database Overview",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                item {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        SystemMetric(
                            "Customers",
                            customers.size.toString(),
                            Modifier.weight(1f)
                        )

                        SystemMetric(
                            "Document Types",
                            types.size.toString(),
                            Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        SystemMetric(
                            "Devices",
                            devices.size.toString(),
                            Modifier.weight(1f)
                        )

                        SystemMetric(
                            "Sessions",
                            sessions.size.toString(),
                            Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Text(
                        "Operational Summary",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {

                            SystemKeyValue(
                                "Active devices",
                                devices.count {
                                    it.status.equals(
                                        "ACTIVE", true
                                    )
                                }.toString()
                            )

                            HorizontalDivider()

                            SystemKeyValue(
                                "Active sessions",
                                sessions.count {
                                    it.status.equals(
                                        "ACTIVE", true
                                    )
                                }.toString()
                            )

                            HorizontalDivider()

                            SystemKeyValue(
                                "Analysis-enabled document types",
                                types.count {
                                    it.analysisEnabled == true
                                }.toString()
                            )
                        }
                    }
                }

                item {
                    Text(
                        "Document Type Configuration",
                        style = MaterialTheme.typography.titleMedium
                    )

                    val enabled = types.count {
                        it.analysisEnabled == true
                    }

                    SystemBar(
                        "Analysis enabled",
                        enabled,
                        types.size.coerceAtLeast(1)
                    )

                    SystemBar(
                        "Analysis disabled / not configured",
                        types.size - enabled,
                        types.size.coerceAtLeast(1)
                    )
                }

                item {
                    Text(
                        "Browse Categories",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                items(
                    SystemCategory.entries.toList()
                ) { selected ->

                    val count = when (selected) {
                        SystemCategory.CUSTOMERS -> customers.size
                        SystemCategory.DOCUMENT_TYPES -> types.size
                        SystemCategory.DEVICES -> devices.size
                        SystemCategory.SESSIONS -> sessions.size
                    }

                    SystemNavigationCard(
                        title = selected.title,
                        subtitle = selected.description,
                        count = count
                    ) {
                        category = selected
                        page = SystemPage.CATEGORY
                        search = ""
                        statusFilter = "ALL"
                    }
                }
            }

            // ======================================
            // CATEGORY PAGE
            // ======================================

            SystemPage.CATEGORY -> {

                item {
                    Text(
                        "${allRecords.size} records",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search records") },
                        placeholder = {
                            Text("Name, ID, status or other value")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    var expanded by remember {
                        mutableStateOf(false)
                    }

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true }
                        ) {
                            Text("Status: $statusFilter")
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = {
                                expanded = false
                            }
                        ) {
                            statusOptions.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status) },
                                    onClick = {
                                        statusFilter = status
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Status Distribution",
                                style = MaterialTheme.typography.titleMedium
                            )

                            val groups = allRecords
                                .groupBy { it.status.uppercase(Locale.ROOT) }

                            if (groups.isEmpty()) {
                                Text("No data available")
                            } else {
                                groups.toSortedMap()
                                    .forEach { (status, group) ->
                                        SystemBar(
                                            status,
                                            group.size,
                                            allRecords.size.coerceAtLeast(1)
                                        )
                                    }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Showing ${filteredRecords.size} of " +
                                "${allRecords.size} records",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (filteredRecords.isEmpty()) {
                    item {
                        Text("No matching records found.")
                    }
                }

                items(
                    filteredRecords,
                    key = { it.key }
                ) { record ->

                    SystemRecordCard(record) {
                        selectedRecord = record
                        page = SystemPage.DETAILS
                    }
                }
            }

            // ======================================
            // FULL RECORD DETAILS
            // ======================================

            SystemPage.DETAILS -> {

                selectedRecord?.let { record ->

                    item {

                        Card {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(10.dp)
                            ) {

                                Text(
                                    record.title,
                                    style =
                                        MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    record.subtitle,
                                    style =
                                        MaterialTheme.typography.bodyMedium
                                )

                                Text(
                                    "Status: ${record.status}",
                                    color =
                                        MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    item {
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(12.dp)
                            ) {

                                Text(
                                    "${category?.title} — Full Details",
                                    style =
                                        MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                HorizontalDivider()

                                record.fields.forEachIndexed {
                                        index, pair ->

                                    SystemKeyValue(
                                        pair.first,
                                        pair.second.orEmpty()
                                            .ifBlank { "Not recorded" }
                                    )

                                    if (
                                        index < record.fields.lastIndex
                                    ) {
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "These values are retrieved from the " +
                                    "existing Spring Boot database API. " +
                                    "Fields not recorded in the database " +
                                    "are shown as Not recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ======================================
// REUSABLE COMPONENTS
// ======================================

@Composable
private fun SystemMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                label,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SystemNavigationCard(
    title: String,
    subtitle: String,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    count.toString(),
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "View records →",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SystemRecordCard(
    record: SystemRecord,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {
            Text(
                record.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                record.subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                "Status: ${record.status}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                "View full details →",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SystemKeyValue(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )

        Text(
            value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SystemBar(
    label: String,
    count: Int,
    maximum: Int
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {
        Row(
            horizontalArrangement =
                Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                count.toString(),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LinearProgressIndicator(
            progress = {
                (count.toFloat() /
                        maximum.coerceAtLeast(1)).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun yesNo(value: Boolean?): String =
    when (value) {
        true -> "Yes"
        false -> "No"
        null -> "Not recorded"
    }

private fun <T> Response<T>.systemBody(): T {
    if (!isSuccessful) {
        error("System API returned HTTP ${code()}")
    }

    return requireNotNull(body()) {
        "System API returned an empty response"
    }
}
