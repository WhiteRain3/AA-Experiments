package io.github.highwindbr.aaxp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Application
import android.net.Uri
import android.content.Intent
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.highwindbr.aaxp.core.*
import io.github.highwindbr.aaxp.discovery.*
import io.github.highwindbr.aaxp.resolver.DynamicProfile
import io.github.highwindbr.aaxp.scanner.ArchiveInputResolver
import io.github.highwindbr.aaxp.scanner.InstalledAaScanner
import io.github.highwindbr.aaxp.scanner.ManifestCatalog
import io.github.highwindbr.aaxp.service.ServiceBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.OutputStreamWriter
import java.io.BufferedWriter

data class ManagerUiState(
    val loading: Boolean = true,
    val aa: AaInstallation = AaInstallation(false),
    val framework: FrameworkState = FrameworkState(),
    val discovered: List<DiscoveredIdentifier> = emptyList(),
    val catalog: List<DiscoveredIdentifier> = emptyList(),
    val catalogTotal: Int = 0,
    val selectedDetails: DiscoveredIdentifier? = null,
    val detailsLoading: Boolean = false,
    val loadedEditableCount: Int = 0,
    val dynamicOverrides: Map<String, TypedOverride> = emptyMap(),
    val suspendedOverrides: List<SuspendedOverride> = emptyList(),
    val profilePublished: Boolean = false,
    val probeTargetKey: String? = null,
    val experimentalSnapshot: ExperimentalBooleanSnapshot? = null,
    val experimentalProfileReady: Boolean = false,
    val advancedRecords: List<AdvancedOverrideRecord> = emptyList(),
    val advancedCandidates: List<DiscoveredIdentifier> = emptyList(),
    val advancedPublishedCount: Int = 0,
    val changes: CatalogChangeSummary? = null,
    val catalogDeltas: List<EditableCatalogDelta> = emptyList(),
    val hiddenSuspendedKeys: Set<String> = emptySet(),
    val resolverRun: ResolverRunSummary? = null,
    val deepResolvePending: Int = 0,
    val deepResolveRunning: Boolean = false,
    val consumerTraceKey: String? = null,
    val videoTimingTraceEnabled: Boolean = false,
    val mediaStartupTraceEnabled: Boolean = false,
    val mediaStartupAutoEnabled: Boolean = false,
    val mediaSemanticAnalysisRunning: Boolean = false,
    val mediaSemanticReport: String? = null,
    val mediaLabArmed: Boolean = false,
    val mediaLabRoute: String = "A",
    val mediaLabRouteAAvailable: Boolean = false,
    val mediaLabRouteBAvailable: Boolean = false,
    val mediaLabAutoCapable: Boolean = false,
    val mediaLabAutoEnabled: Boolean = false,
    val scanProgress: AnalysisProgress? = null,
    val saveState: SaveState = SaveState.IDLE,
    val saveTargetKey: String? = null,
    val message: String? = null
)

enum class ExportScope { EDITABLE, MODIFIED, ALL_DISCOVERIES }
enum class ExportFormat { JSON, CSV }
data class CatalogExportRequest(val scope: ExportScope, val format: ExportFormat, val includeMethods: Boolean, val includeDefaults: Boolean, val includeOverrides: Boolean) {
    val fileName: String get() = "aa-catalog-${scope.name.lowercase()}.${format.name.lowercase()}"
}
private data class DeepResolveOutcome(val saved: Int, val resolved: Int, val inconclusive: Int, val cancelled: Boolean)
private data class OverrideRefreshOutcome(val active: Map<String, TypedOverride>, val suspended: List<SuspendedOverride>, val published: Boolean, val error: Throwable?, val experimental: ExperimentalBooleanSnapshot?, val experimentalReady: Boolean, val advanced: List<AdvancedOverrideRecord>, val advancedPublishedCount: Int, val videoTimingTraceEnabled: Boolean, val mediaStartupTraceEnabled: Boolean, val mediaStartupAutoEnabled: Boolean, val mediaLabArmed: Boolean, val mediaLabRoute: String,
    val mediaLabAutoCapable: Boolean, val mediaLabAutoEnabled: Boolean,
    val mediaLabRouteAAvailable: Boolean, val mediaLabRouteBAvailable: Boolean)

private const val AUTO_RESTART_PREF = "auto_restart_after_change"
private const val HEAD_UNIT_AFTER_RESTART_PREF = "head_unit_after_restart"

class ManagerViewModel(application: Application) : AndroidViewModel(application) {
    /** True after a look change was saved and Android Auto has not been restarted since. */
    var changesWaiting by androidx.compose.runtime.mutableStateOf(false)
        private set

    private val mutable = MutableStateFlow(ManagerUiState())
    private val database = CatalogDatabase(application)
    private val hiddenPreferences = application.getSharedPreferences("suspended_override_visibility", Context.MODE_PRIVATE)
    private val probePreferences = application.getSharedPreferences("read_only_getter_probe", Context.MODE_PRIVATE)
    private val mediaLabPreferences = application.getSharedPreferences("media_startup_pf6_lab", Context.MODE_PRIVATE)
    // All profile computations, writes and build-state handoffs share one coordinator.
    private val profileRefreshMutex = ProfileRefreshGate()
    private val profileMutationCoordinator = ProfileMutationCoordinator(profileRefreshMutex)
    // Only one expensive catalog/DEX operation may own the foreground service.
    private val analysisOperationGate = AnalysisOperationGate()
    private val catalogExportGate = AnalysisOperationGate()
    val state: StateFlow<ManagerUiState> = mutable
    @Volatile private var deepResolveCancelRequested = false
    // A slower query must never replace results from a newer screen request.
    private val advancedCandidatesGate = LatestRequestGate()
    private val catalogSearchGate = LatestRequestGate()
    private val detailsGate = LatestRequestGate()

    init {
        viewModelScope.launch {
            ServiceBridge.state.collect { framework ->
                mutable.value = mutable.value.copy(framework = framework)
                refreshOverridesAndProfile()
            }
        }
        refreshInstallation()
    }

    fun refreshInstallation() = viewModelScope.launch {
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Another catalog analysis is already running.")
            return@launch
        }
        try {
            keepAnalysisAlive(true)
            mutable.value = mutable.value.copy(loading = true, scanProgress = null, mediaSemanticReport = null,
                message = "Loading installed Android Auto catalog…")
            val aa = withContext(Dispatchers.IO) {
                File(getApplication<Application>().filesDir, "imports").deleteRecursively()
                InstalledAaScanner(getApplication()).scan(::reportScanProgress)
            }
            if (mutable.value.scanProgress != null) reportScanProgress(AnalysisProgress(AnalysisStage.LOADING_EXPERIMENTS, detail = "Loading editable experiments"))
            val page = withContext(Dispatchers.IO) { aa.buildFingerprintSha256?.let { database.editablePage(it, Int.MAX_VALUE, 0) }.orEmpty() }
            val previous = withContext(Dispatchers.IO) {
                aa.buildFingerprintSha256?.let { database.previousSummary(it) }
            }
            val deltas = withContext(Dispatchers.IO) {
                previous?.let { compareEditableCatalogs(database.editablePage(it.baseSha256, Int.MAX_VALUE, 0), page) }.orEmpty()
            }
            val changes = previous?.let { deltaSummary(deltas) }
            val resolverRun = withContext(Dispatchers.IO) { aa.buildFingerprintSha256?.let(database::resolverRunSummary) }
            val deepPending = withContext(Dispatchers.IO) { aa.buildFingerprintSha256?.let(database::actionableDeepResolveCount) ?: 0 }
            val hidden = hiddenKeysForBuild(aa.buildFingerprintSha256)
            profileRefreshMutex.withLock {
                mutable.value = mutable.value.copy(loading = false, scanProgress = null, aa = aa, discovered = page,
                    loadedEditableCount = page.size, changes = changes, catalogDeltas = deltas,
                    hiddenSuspendedKeys = hidden, resolverRun = resolverRun, deepResolvePending = deepPending, message = null)
            }
            refreshOverridesAndProfile()
        } catch (cancelled: CancellationException) {
            mutable.update { it.copy(loading = false, scanProgress = null) }
            throw cancelled
        } catch (failure: Exception) {
            mutable.update { it.copy(loading = false, scanProgress = null,
                message = "Installed catalog scan failed: ${failure.javaClass.simpleName}: ${failure.message}") }
        } finally {
            try { keepAnalysisAlive(false) } finally { analysisOperationGate.finish() }
        }
    }

    /**
     * Query Advanced candidates lazily, only after explicit Diagnostics opt-in.
     * The full read-only catalog is never copied into the Experiments page.
     * Reject results for builds which changed during the asynchronous query.
     */
    fun loadAdvancedCandidates() = viewModelScope.launch {
        val request = advancedCandidatesGate.begin()
        val build = mutable.value.aa.buildFingerprintSha256
        if (build == null || !mutable.value.aa.runtimeTarget) {
            mutable.value = mutable.value.copy(advancedCandidates = emptyList())
            return@launch
        }
        val candidates = withContext(Dispatchers.IO) {
            database.advancedCandidateKeys(build).mapNotNull { key ->
                database.details(build, key)?.takeIf {
                    AdvancedOverridePolicy.issue(it) == null
                }
            }
        }
        if (advancedCandidatesGate.accepts(request) && mutable.value.aa.buildFingerprintSha256 == build &&
            mutable.value.aa.runtimeTarget)
            mutable.value = mutable.value.copy(advancedCandidates = candidates)
    }

    fun clearAdvancedCandidates() {
        advancedCandidatesGate.invalidate()
        if (mutable.value.advancedCandidates.isNotEmpty())
            mutable.value = mutable.value.copy(advancedCandidates = emptyList())
    }

    fun searchCatalog(query: CatalogQuery, append: Boolean = false) = viewModelScope.launch {
        val request = catalogSearchGate.begin()
        val hash = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = withContext(Dispatchers.IO) { database.search(hash, query) }
        if (!catalogSearchGate.accepts(request) || mutable.value.aa.buildFingerprintSha256 != hash) return@launch
        mutable.value = mutable.value.copy(catalog = if (append) (mutable.value.catalog + result.items).distinctBy { it.key } else result.items, catalogTotal = result.total)
    }

    fun loadDetails(key: String) = viewModelScope.launch {
        val request = detailsGate.begin()
        val hash = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        mutable.value = mutable.value.copy(detailsLoading = true, selectedDetails = null)
        val value = withContext(Dispatchers.IO) { database.details(hash, key) }
        if (!detailsGate.accepts(request) || mutable.value.aa.buildFingerprintSha256 != hash) return@launch
        mutable.value = mutable.value.copy(detailsLoading = false, selectedDetails = value)
    }

    fun clearDetails() {
        detailsGate.invalidate()
        mutable.value = mutable.value.copy(selectedDetails = null, detailsLoading = false)
    }

    /** Bounded, direct-to-document export: no 50k-row objects in the manager UI. */
    fun exportCatalogToUri(destination: Uri, request: CatalogExportRequest) = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        if (!catalogExportGate.tryBegin()) {
            mutable.update { it.copy(message = "Another catalog export is already running.") }
            return@launch
        }
        mutable.update { it.copy(message = "Exporting catalog…") }
        try {
            val exported = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val overrides = mutable.value.dynamicOverrides
                val scopeKeys = if (request.scope == ExportScope.MODIFIED)
                    database.existingKeys(build, overrides.keys) else null
                context.contentResolver.openOutputStream(destination, "wt").use { stream ->
                    val output = checkNotNull(stream) { "The destination cannot be opened." }
                    BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8), 32 * 1024).use { writer ->
                        val query = CatalogQuery(editableOnly = request.scope != ExportScope.ALL_DISCOVERIES,
                            includeUnknown = true)
                        CatalogExportWriter.write(writer, request.format,
                            request.includeMethods, request.includeDefaults, request.includeOverrides,
                            overrides, pages = { offset, size ->
                                check(mutable.value.aa.buildFingerprintSha256 == build) {
                                    "Installed/catalog build changed during export."
                                }
                                database.exportPage(build, query, offset, size,
                                    request.includeMethods, scopeKeys)
                            })
                    }
                }
            }
            mutable.update { it.copy(message = "Catalog exported: $exported identifiers.") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ex: Exception) {
            runCatching { getApplication<Application>().contentResolver.delete(destination, null, null) }
            mutable.update { it.copy(message = "Export failed: ${ex.message}; any partial file should be discarded.") }
        } finally {
            catalogExportGate.finish()
        }
    }

    fun importArchive(uri: Uri) = viewModelScope.launch {
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Finish the current catalog analysis before importing an archive.")
            return@launch
        }
        try {
            keepAnalysisAlive(true)
            mutable.value = mutable.value.copy(loading = true, scanProgress = AnalysisProgress(AnalysisStage.PREPARING_ARCHIVE, detail = "Copying and opening archive"), message = "Importing and analyzing archive…")
            val result = withContext(Dispatchers.IO) { runCatching {
            val context = getApplication<Application>()
            val imports = File(context.filesDir, "imports").apply { mkdirs() }
            val directory = File(imports, "session-${System.currentTimeMillis()}").apply { mkdirs() }
            val archive = File(directory, "import-${System.currentTimeMillis()}.apkm")
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    ArchiveInputResolver.copyInputBounded(requireNotNull(input), archive)
                }
                val resolved = ArchiveInputResolver.resolve(archive, directory); val base = resolved.base
                val info = context.packageManager.getPackageArchiveInfo(base.absolutePath, ManifestCatalog.PACKAGE_FLAGS)
                val versionCode = info?.let { PackageInfoCompat.getLongVersionCode(it) }
                val identity = BuildFingerprint.compute(versionCode ?: 0L, base, resolved.splits)
                val previous = database.latestEditableMappings()
                val inventory = DexCatalogAnalyzer().analyze(
                    base,
                    resolved.splits,
                    previousMappings = previous?.second.orEmpty(),
                    previousBaseSha256 = previous?.first?.baseSha256,
                    buildIdentity = identity,
                    onProgress = ::reportScanProgress
                ).let { scanned ->
                    if (info == null) scanned else scanned.copy(identifiers = (scanned.identifiers + ManifestCatalog.components(info)).distinctBy { it.key }.sortedBy { it.key })
                }
                database.put(inventory, ::reportScanProgress, CatalogOrigin.IMPORTED)
                AaInstallation(installed=true, versionName=info?.versionName, versionCode=versionCode, baseSha256=inventory.baseSha256,
                    dexSha256=inventory.dexSha256, identifierCount=inventory.identifiers.size, editableCount=inventory.identifiers.count { it.editable },
                    discoveryErrorCount=inventory.errors.size, runtimeTarget=false, buildFingerprintSha256=inventory.buildFingerprintSha256,
                    packageArtifactSha256=inventory.packageArtifactSha256)
            } finally {
                directory.deleteRecursively()
                if (imports.listFiles().isNullOrEmpty()) imports.delete()
            }
        } }
            result.onSuccess { aa ->
            reportScanProgress(AnalysisProgress(AnalysisStage.LOADING_EXPERIMENTS, detail = "Loading editable experiments"))
            val page = withContext(Dispatchers.IO) { database.editablePage(checkNotNull(aa.buildFingerprintSha256), Int.MAX_VALUE, 0) }
            val previous = withContext(Dispatchers.IO) { database.previousSummary(checkNotNull(aa.buildFingerprintSha256)) }
            val deltas = withContext(Dispatchers.IO) {
                previous?.let { compareEditableCatalogs(database.editablePage(it.baseSha256, Int.MAX_VALUE, 0), page) }.orEmpty()
            }
            val resolverRun = withContext(Dispatchers.IO) { database.resolverRunSummary(checkNotNull(aa.buildFingerprintSha256)) }
            profileRefreshMutex.withLock {
                mutable.value = mutable.value.copy(loading = false, scanProgress = null, aa = aa, discovered = page, loadedEditableCount = page.size,
                    dynamicOverrides = emptyMap(), suspendedOverrides = emptyList(), hiddenSuspendedKeys = emptySet(),
                    profilePublished = false, changes = previous?.let { deltaSummary(deltas) }, catalogDeltas = deltas, resolverRun = resolverRun,
                    message = "Imported ${aa.identifierCount} identifiers for offline review; the installed-AA runtime profile was not replaced.")
            }
            }.onFailure { error -> mutable.value = mutable.value.copy(loading = false, scanProgress = null, message = "Import failed: ${error.message}") }
        } catch (cancelled: CancellationException) {
            mutable.update { it.copy(loading = false, scanProgress = null) }
            throw cancelled
        } catch (failure: Exception) {
            mutable.update { it.copy(loading = false, scanProgress = null,
                message = "Archive analysis failed: ${failure.javaClass.simpleName}: ${failure.message}") }
        } finally {
            try { keepAnalysisAlive(false) } finally { analysisOperationGate.finish() }
        }
    }

    private fun reportScanProgress(progress: AnalysisProgress) {
        // This callback runs on the DEX/IO worker thread; avoid overwriting
        // simultaneous framework state changes with a non-atomic read-copy-write.
        mutable.update { it.copy(scanProgress = progress) }
    }

    /**
     * One-key, explicit and read-only reverse call graph for Advanced getters.
     * Does not modify getter identity, editor permissions, baseline or profiles.
     */
    fun traceAdvancedConsumers(key: String) = viewModelScope.launch {
        if (mutable.value.deepResolveRunning || mutable.value.loading) {
            mutable.value = mutable.value.copy(message = "Wait for the current catalog analysis to finish.")
            return@launch
        }
        val aa = mutable.value.aa
        val hash = aa.buildFingerprintSha256
        val basePath = aa.basePath
        if (!aa.runtimeTarget || hash == null || basePath == null) {
            mutable.value = mutable.value.copy(message = "Consumer tracing requires the installed Android Auto build.")
            return@launch
        }
        val result = withContext(Dispatchers.IO) { runCatching {
            val id = checkNotNull(database.details(hash, key)) { "Identifier not in this build." }
            require(!id.editable && AdvancedOverridePolicy.issue(id) == null) {
                "Only structurally eligible, non-editable Advanced getters may be traced."
            }
        } }
        if (result.isFailure) {
            mutable.value = mutable.value.copy(message = "Trace unavailable: ${result.exceptionOrNull()?.message}")
            return@launch
        }
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Another catalog analysis is already running.")
            return@launch
        }
        deepResolveCancelRequested = false
        try {
            keepAnalysisAlive(true)
            mutable.value = mutable.value.copy(loading = true, deepResolveRunning = true,
                consumerTraceKey = key, selectedDetails = null,
                scanProgress = AnalysisProgress(AnalysisStage.DEEP_RESOLVING, 0, 1, "Tracing one Advanced getter"))
            val tracing = withContext(Dispatchers.IO) { runCatching {
                val selected = setOf(key)
                val baseline = database.compatibilityMappingsByKeys(hash, selected)
                var found: DiscoveredIdentifier? = null
                DexCatalogAnalyzer().analyze(
                    File(basePath), aa.splitPaths.map(::File),
                    previousMappings = baseline, previousBaseSha256 = hash,
                    buildIdentity = BuildFingerprint.compute(aa.versionCode ?: 0,
                        File(basePath), aa.splitPaths.map(::File)),
                    deepResolveKeys = selected, streamDeepResolveResults = true,
                    shouldContinueDeepResolve = { !deepResolveCancelRequested },
                    onDeepResolveResult = { enriched, _, _ ->
                        if (enriched.key == key) {
                            database.updateDeepResolveResults(hash, listOf(enriched))
                            found = enriched
                        }
                    },
                    onProgress = ::reportScanProgress
                )
                checkNotNull(found) { "The getter was not located in the inspected DEX." }
                val updated = checkNotNull(database.details(hash, key)) { "Saved evidence missing." }
                updated
            } }
            tracing.onSuccess { updated ->
                mutable.value = mutable.value.copy(
                    selectedDetails = updated, message = "Consumer trace completed for $key: " +
                        "${updated.consumers.size} static path(s); does not prove runtime invocation."
                )
            }.onFailure { error ->
                mutable.value = mutable.value.copy(
                    message = "Consumer trace failed: ${error.javaClass.simpleName}: ${error.message}"
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutable.update { it.copy(message =
                "Consumer tracing stopped: ${failure.javaClass.simpleName}: ${failure.message}") }
        } finally {
            mutable.update { it.copy(loading = false, deepResolveRunning = false,
                consumerTraceKey = null, scanProgress = null) }
            try { keepAnalysisAlive(false) } finally { analysisOperationGate.finish() }
        }
    }

    fun setVideoTimingProtocolTrace(enabled: Boolean) = viewModelScope.launch {
        val aa = mutable.value.aa
        if (!aa.runtimeTarget || !io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.isSupported(aa.baseSha256)) {
            mutable.value = mutable.value.copy(message = "Read-only VTC protocol tracing is pinned to the inspected Android Auto 18.0 base APK.")
            return@launch
        }
        val result = writePreferencesAndPublish {
            withContext(Dispatchers.IO) {
                check(ServiceBridge.dynamicPreferencesOrNull()?.edit()
                    ?.putString(io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.KEY,
                        enabled.toString())?.commit() == true) {
                    "LSPosed remote preferences unavailable."
                }
            }
        }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Cannot update VTC trace: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() != ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "VTC trace preference saved, but runtime profile was NOT published. Check LSPosed."
            enabled -> "VTC read-only trace enabled and published. Restart Android Auto, then inspect LSPosed module logs."
            else -> "VTC trace disabled and profile published. Restart AA to unload existing hooks."
        })
    }

    /** Media Startup diagnostic opt-in; profile scope and pinned SHA stay unchanged. */
    fun setMediaStartupTrace(enabled: Boolean) = viewModelScope.launch {
        val aa = mutable.value.aa
        if (!aa.runtimeTarget ||
            !io.github.highwindbr.aaxp.runtime.MediaStartupTrace.isSupported(aa.baseSha256)) {
            mutable.value = mutable.value.copy(message =
                "Media Startup observation requires the exact inspected Android Auto 18.1 base APK.")
            return@launch
        }
        val result = writePreferencesAndPublish {
            withContext(Dispatchers.IO) {
                check(ServiceBridge.dynamicPreferencesOrNull()?.edit()
                    ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupTrace.KEY,
                        enabled.toString())
                    ?.apply {
                        if (!enabled) putString(
                            io.github.highwindbr.aaxp.runtime.MediaStartupAutoGate.KEY, "false")
                    }?.commit() == true) {
                    "LSPosed remote preferences unavailable."
                }
            }
        }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Cannot update Media Startup trace: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() != ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Media Startup trace preference saved, but runtime profile was NOT published. Check LSPosed."
            enabled -> "Media Startup trace enabled and published. Restart Android Auto and inspect LSPosed logs."
            else -> "Media Startup trace disabled and profile published. Restart Android Auto to unload its hooks."
        })
    }

    /**
     * PF5 discovery does NOT publish runtime hooks for an unknown build.
     * Explicitly invoked, bounded scan on the installed AA base APK.
     */
    fun inspectMediaStartupSemantics() = viewModelScope.launch {
        val aa = mutable.value.aa
        val base = aa.basePath
        if (!aa.runtimeTarget || base == null) {
            mutable.value = mutable.value.copy(message =
                "Compatibility checks require the installed Android Auto APK, not an imported archive.")
            return@launch
        }
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Another APK analysis is already running.")
            return@launch
        }
        mutable.value = mutable.value.copy(mediaSemanticAnalysisRunning = true,
            mediaSemanticReport = null)
        try {
            val outcome = withContext(Dispatchers.IO) {
                io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver.inspect(File(base))
            }
            if (mutable.value.aa.basePath == base &&
                mutable.value.aa.buildFingerprintSha256 == aa.buildFingerprintSha256) {
                mutable.value = mutable.value.copy(
                    mediaSemanticReport = outcome.summary(),
                    message = "Media Startup compatibility: ${outcome.status}. Opening settings were not changed.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutable.value = mutable.value.copy(message =
                "Compatibility scan could not finish: ${failure.javaClass.simpleName}")
        } finally {
            mutable.value = mutable.value.copy(mediaSemanticAnalysisRunning = false)
            analysisOperationGate.finish()
        }
    }

    /**
     * PF6.2: arm build-bound manual A/B routes and optionally the
     * ActivityTracker observer needed for a separate automatic opt-in.
     * Every new proof starts with PF6 auto OFF, independently of PF4.
     */
    fun armMediaStartupLab() = viewModelScope.launch {
        val aa = mutable.value.aa
        val base = aa.basePath
        val fingerprint = aa.buildFingerprintSha256
        val hash = aa.baseSha256
        if (!aa.runtimeTarget || base == null || fingerprint == null || hash == null ||
            !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "Install Android Auto and connect LSPosed first.")
            return@launch
        }
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Another APK analysis is already running.")
            return@launch
        }
        mutable.value = mutable.value.copy(mediaSemanticAnalysisRunning = true)
        try {
            val evidence = withContext(Dispatchers.IO) {
                io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver.inspect(File(base))
            }
            val required = setOf("projection.ready", "projection.end",
                "projection.teardown", "launch.controllerFactory", "media.selectedApp")
            val hasA = "launch.auxiliary" in evidence.roles
            val hasB = "launch.startCarActivity" in evidence.roles
            if (evidence.status !in setOf(
                    io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver.Status.KNOWN_BUILD_VALIDATED,
                    io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver.Status.REVIEW_ONLY) ||
                evidence.baseSha256 != hash || !evidence.roles.keys.containsAll(required) ||
                (!hasA && !hasB)) {
                mutable.value = mutable.value.copy(mediaSemanticReport = evidence.summary(),
                    message = "Dynamic routes not armed: incomplete, ambiguous or incompatible DEX proof.")
                return@launch
            }
            if (mutable.value.aa.buildFingerprintSha256 != fingerprint ||
                mutable.value.aa.baseSha256 != hash) {
                mutable.value = mutable.value.copy(message = "Android Auto changed during analysis; restart the scan.")
                return@launch
            }
            val lab = JSONObject().apply {
                put("mode", io.github.highwindbr.aaxp.runtime.MediaStartupLabProfile.MODE)
                put("baseSha256", hash)
                put("buildFingerprintSha256", fingerprint)
                put("roles", JSONObject(evidence.roles))
            }
            // A single explicit click requests setup + auto-opening, but never
            // arms the automatic gate before the new profile was VERIFIED.
            // A future build has a different fingerprint and requires a new click.
            val requestAutomatic = MediaStartupConsentPolicy.allowAutoAfterExplicitResolution(
                evidence.status, evidence.roles)
            var consentRollbackFailed = false
            val (stored, published, automaticEnabled) = profileRefreshMutex.withLock {
                if (mutable.value.aa.buildFingerprintSha256 != fingerprint ||
                    mutable.value.aa.baseSha256 != hash || !mutable.value.aa.runtimeTarget) {
                    Triple(false, false, false)
                } else {
                    val disabledAuto = withContext(Dispatchers.IO) {
                        ServiceBridge.dynamicPreferencesOrNull()?.edit()
                            ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupAutoGate.KEY, "false")
                            ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY, "false")
                            ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.LEGACY_PF6_KEY, "false")
                            ?.commit() == true
                    }
                    // Persist a disabled baseline first: failed publication cannot
                    // leave remote automatic opening active, even on existing hooks.
                    val selectedRoute = io.github.highwindbr.aaxp.runtime.MediaStartupRoutePolicy.select(
                        mediaLabPreferences.getString("route:$fingerprint", "A"), hasA, hasB)
                    val saved = disabledAuto && selectedRoute != null &&
                        mediaLabPreferences.edit()
                            .putString("auto-selected:$fingerprint", "false")
                            .putString("route:$fingerprint", selectedRoute)
                            .putString(fingerprint, lab.toString()).commit()
                    if (!saved) Triple(false, false, false)
                    else {
                        mutable.value = mutable.value.copy(mediaSemanticReport = evidence.summary())
                        val publishedNow = refreshOverridesAndProfileLocked()
                        var enabledNow = false
                        if (publishedNow && requestAutomatic &&
                            mutable.value.aa.buildFingerprintSha256 == fingerprint) {
                            // Consent is build-scoped and is only enabled AFTER
                            // successful profile readback + slot activation.
                            val localSaved = mediaLabPreferences.edit()
                                .putString("auto-selected:$fingerprint", "true").commit()
                            if (localSaved) {
                                enabledNow = withContext(Dispatchers.IO) {
                                    ServiceBridge.dynamicPreferencesOrNull()?.edit()
                                        ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY, "true")
                                        ?.commit() == true
                                }
                                if (!enabledNow) {
                                    val localRollback = mediaLabPreferences.edit()
                                        .putString("auto-selected:$fingerprint", "false").commit()
                                    val remoteRollback = withContext(Dispatchers.IO) {
                                        ServiceBridge.dynamicPreferencesOrNull()?.edit()
                                            ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY, "false")
                                            ?.commit() == true
                                    }
                                    consentRollbackFailed = !localRollback || !remoteRollback
                                }
                            }
                        }
                        mutable.value = mutable.value.copy(mediaLabAutoEnabled = enabledNow)
                        Triple(true, publishedNow, enabledNow)
                    }
                }
            }
            mutable.value = mutable.value.copy(message = when {
                !stored -> "Could not save Media Startup setup. Automatic opening was not enabled."
                !published -> "Compatibility proof saved, but its profile was NOT published. Automatic opening remains off. Check LSPosed."
                automaticEnabled -> "Compatibility verified. Automatic media opening is ON for this build. Restart Android Auto, then reconnect while parked."
                consentRollbackFailed -> "Media-opening consent could not be enabled or safely rolled back. Check LSPosed preferences before reconnecting."
                requestAutomatic -> "Routes are ready, but automatic opening could not be enabled. Check LSPosed or use advanced options."
                mutable.value.mediaLabAutoCapable -> "Opening routes and automatic trigger verified. Confirm automatic opening to enable it for this build."
                else -> "Opening routes verified, but automatic opening requirements could not be verified. Manual testing is available."
            })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) {
            mutable.value = mutable.value.copy(message =
                "Media Startup scan failed safely: ${e.javaClass.simpleName}")
        } finally {
            mutable.value = mutable.value.copy(mediaSemanticAnalysisRunning = false)
            analysisOperationGate.finish()
        }
    }

    fun disarmMediaStartupLab() = viewModelScope.launch {
        val fingerprint = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val (stored, published) = profileRefreshMutex.withLock {
            if (mutable.value.aa.buildFingerprintSha256 != fingerprint) {
                false to false
            } else {
                val remoteDisabled = withContext(Dispatchers.IO) {
                    ServiceBridge.dynamicPreferencesOrNull()?.edit()
                        ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY, "false")
                        ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.LEGACY_PF6_KEY, "false")
                        ?.commit() == true
                }
                val saved = remoteDisabled && mediaLabPreferences.edit().remove(fingerprint)
                    .putString("auto-selected:$fingerprint", "false").commit()
                if (saved) true to refreshOverridesAndProfileLocked() else false to false
            }
        }
        mutable.value = mutable.value.copy(message = when {
            !stored -> "Dynamic route disable could not be saved. Do not assume the current hook has stopped."
            published -> "Dynamic routes disarmed and profile published; automatic attempts disabled. Restart Android Auto to unload the hooks."
            else -> "Dynamic routes disarmed in preferences, but runtime profile was NOT published. Check LSPosed; restart AA after resolving the failure."
        })
    }

    fun chooseMediaLabRoute(route: String) = viewModelScope.launch {
        val fingerprint = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        if ((route != "A" && route != "B") ||
            (route == "A" && !mutable.value.mediaLabRouteAAvailable) ||
            (route == "B" && !mutable.value.mediaLabRouteBAvailable)) {
            mutable.value = mutable.value.copy(message =
                "This route was not verified for the installed Android Auto build.")
            return@launch
        }
        val persisted = profileRefreshMutex.withLock {
            if (mutable.value.aa.buildFingerprintSha256 != fingerprint) false
            else {
                val saved = withContext(Dispatchers.IO) {
                    ServiceBridge.dynamicPreferencesOrNull()?.edit()
                        ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupLabGate.ROUTE_KEY,
                            route)?.commit() == true
                }
                saved && mediaLabPreferences.edit().putString(
                    "route:$fingerprint", route).commit()
            }
        }
        mutable.value = mutable.value.copy(
            mediaLabRoute = if (persisted) route else mutable.value.mediaLabRoute,
            message = if (persisted) "Preferred Media Startup route: ${route}. Applies to the next automatic attempt."
                else "Could not persist Media Startup route.")
    }

    /** PF6.2: experimental automatic open is a separate per-build user opt-in. */
    fun setMediaLabAuto(enabled: Boolean) = viewModelScope.launch {
        val state = mutable.value
        val fingerprint = state.aa.buildFingerprintSha256 ?: return@launch
        if (!state.framework.connected ||
            (enabled && (!state.mediaLabArmed || !state.mediaLabAutoCapable ||
                !state.profilePublished || state.mediaStartupAutoEnabled))) {
            mutable.value = state.copy(message =
                "Automatic opening requires verified routes, a unique activity trigger and legacy Spotify auto-opening disabled.")
            return@launch
        }
        val key = io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY
        val status = profileRefreshMutex.withLock {
            val current = mutable.value
            if (current.aa.buildFingerprintSha256 != fingerprint ||
                !current.framework.connected ||
                (enabled && (!current.mediaLabArmed || !current.mediaLabAutoCapable ||
                    !current.profilePublished || current.mediaStartupAutoEnabled))) {
                "STALE"
            } else {
                val wrote = withContext(Dispatchers.IO) {
                    ServiceBridge.dynamicPreferencesOrNull()?.edit()
                        ?.putString(key, enabled.toString())
                        ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.LEGACY_PF6_KEY,
                            "false")?.commit() == true
                }
                if (!wrote) "REMOTE_FAILED"
                else if (mediaLabPreferences.edit()
                        .putString("auto-selected:$fingerprint", enabled.toString()).commit()) "SAVED"
                else {
                    // Fail closed if the local per-build consent cannot be stored.
                    val rolledBack = withContext(Dispatchers.IO) {
                        ServiceBridge.dynamicPreferencesOrNull()?.edit()
                            ?.putString(key, "false")?.commit() == true
                    }
                    if (rolledBack) "LOCAL_FAILED" else "ROLLBACK_FAILED"
                }
            }
        }
        mutable.value = mutable.value.copy(
            mediaLabAutoEnabled = when (status) {
                "SAVED" -> enabled
                "LOCAL_FAILED" -> false // Verified remote rollback: reflect fail-closed state.
                else -> mutable.value.mediaLabAutoEnabled
            },
            message = when (status) {
                "SAVED" -> if (enabled)
                    "Automatic media opening enabled for this Android Auto build's NEXT connection. Select your media player in Android Auto first."
                    else "Automatic media opening disabled. Any queued attempt will be skipped."
                "STALE" -> "Media Startup state changed; reopen the current build and try again."
                "REMOTE_FAILED" -> "Could not update automatic Media Startup preference in LSPosed."
                "LOCAL_FAILED" -> "Per-build media consent could not be saved; remote automatic opening was disabled for safety."
                else -> "Per-build consent write and remote rollback failed. Disable automatic opening in LSPosed before using AA."
            })
    }

    fun requestMediaLabManualTest() = viewModelScope.launch {
        val ui = mutable.value
        if (!ui.framework.connected || !ui.profilePublished || !ui.mediaLabArmed) {
            mutable.value = ui.copy(message =
                "Media Startup is not armed. Resolve, publish profile and restart Android Auto.")
            return@launch
        }
        val request = "v1|" + android.os.SystemClock.elapsedRealtime() + "|" +
            java.util.UUID.randomUUID().toString() + "|" + ui.mediaLabRoute
        val saved = withContext(Dispatchers.IO) {
            ServiceBridge.dynamicPreferencesOrNull()?.edit()
                ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupLabGate.REQUEST_KEY,
                    request)?.commit() == true
        }
        mutable.value = mutable.value.copy(message = if (saved)
            "Media Startup route ${ui.mediaLabRoute} test requested. Check LSPosed events and the head unit; only one attempt per connection."
        else "Media Startup LSPosed request failed.")
    }

    /** PF4 opt-in: default off, and never allowed unless PF2/PF3 trace is armed. */
    fun setMediaStartupAuto(enabled: Boolean) = viewModelScope.launch {
        val current = mutable.value
        // Turning OFF must remain possible even after AA updates to an
        // uninspected build; only enabling requires a verified runtime.
        if (!current.framework.connected ||
            (enabled && (current.mediaLabArmed || !current.aa.runtimeTarget ||
                !io.github.highwindbr.aaxp.runtime.MediaStartupTrace.isSupported(current.aa.baseSha256) ||
                !current.mediaStartupTraceEnabled || !current.profilePublished))) {
            mutable.value = mutable.value.copy(message =
                "Legacy Spotify auto-opening requires the previously validated AA 18.1 build and an active media trace.")
            return@launch
        }
        val ok = profileRefreshMutex.withLock {
            val live = mutable.value
            val eligible = !enabled || (!live.mediaLabArmed && live.aa.runtimeTarget &&
                io.github.highwindbr.aaxp.runtime.MediaStartupTrace.isSupported(live.aa.baseSha256) &&
                live.mediaStartupTraceEnabled && live.profilePublished)
            if (!live.framework.connected || !eligible ||
                live.aa.buildFingerprintSha256 != current.aa.buildFingerprintSha256) false
            else withContext(Dispatchers.IO) {
                ServiceBridge.dynamicPreferencesOrNull()?.edit()
                    ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupAutoGate.KEY,
                        enabled.toString())?.commit() == true
            }
        }
        if (!ok) {
            mutable.value = mutable.value.copy(message =
                "Cannot update legacy Spotify auto-opening: LSPosed preferences unavailable.")
            return@launch
        }
        // This is deliberately a remote preference, not a change to the
        // normal flag catalog or profile schema. Armed at NEXT projection ready.
        mutable.value = mutable.value.copy(mediaStartupAutoEnabled = enabled,
            message = if (enabled)
                "Legacy Spotify auto-opening enabled for the next connection. Spotify must already be the selected media app."
            else "Legacy Spotify auto-opening disabled. Pending attempts will be skipped.")
    }

    /** Experimental PF3: one manually confirmed, short-lived Spotify request. */
    fun requestSpotifyManualLaunch() = viewModelScope.launch {
        val value = mutable.value
        if (!value.framework.connected || !value.profilePublished ||
                !value.mediaStartupTraceEnabled || !value.aa.runtimeTarget ||
                !io.github.highwindbr.aaxp.runtime.MediaStartupTrace.isSupported(
                    value.aa.baseSha256)) {
            mutable.value = mutable.value.copy(message =
                "Legacy manual media test unavailable. Enable Media Startup compatibility, publish the profile and restart Android Auto.")
            return@launch
        }
        val request = io.github.highwindbr.aaxp.runtime.MediaStartupLaunchGate.makeRequest(
            android.os.SystemClock.elapsedRealtime(), java.util.UUID.randomUUID().toString())
        val committed = withContext(Dispatchers.IO) {
            ServiceBridge.dynamicPreferencesOrNull()?.edit()
                ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupLaunchGate.REQUEST_KEY,
                    request)?.commit() == true
        }
        mutable.value = mutable.value.copy(message = if (committed)
            "Legacy manual request saved. Check LSPosed events to verify the result; one attempt per active connection."
        else "Legacy manual request failed: LSPosed remote preferences unavailable.")
    }

    fun deepResolveInconclusive() = viewModelScope.launch {
        if (mutable.value.deepResolveRunning) {
            deepResolveCancelRequested = true
            mutable.value = mutable.value.copy(message = "Deep resolve will stop after the current mapping.")
            return@launch
        }
        val aa = mutable.value.aa
        val hash = aa.buildFingerprintSha256
        val basePath = aa.basePath
        if (!aa.runtimeTarget || hash == null || basePath == null) {
            mutable.value = mutable.value.copy(message = "Directed deep resolve is available for the installed Android Auto catalog.")
            return@launch
        }
        val keys = withContext(Dispatchers.IO) {
            (database.inconclusiveKeys(hash, Int.MAX_VALUE) + database.targetedRetryKeys(hash)).distinct()
        }
        if (keys.isEmpty()) {
            mutable.value = mutable.value.copy(message = "No pending inconclusive mapping remains for directed analysis.")
            return@launch
        }
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Another catalog analysis is already running.")
            return@launch
        }
        deepResolveCancelRequested = false
        try {
            keepAnalysisAlive(true)
            mutable.value = mutable.value.copy(
            loading = true,
            deepResolveRunning = true,
            scanProgress = AnalysisProgress(AnalysisStage.DEEP_RESOLVING, 0, keys.size, "Preparing one shared DEX graph"),
            message = "Deep resolving up to ${keys.size} inconclusive mappings with one DEX graph…"
        )
            val result = withContext(Dispatchers.IO) { runCatching {
            val baseline = database.compatibilityMappingsByKeys(hash, keys.toSet())
            var saved = 0
            var resolved = 0
            var inconclusive = 0
            DexCatalogAnalyzer().analyze(
                File(basePath),
                aa.splitPaths.map(::File),
                previousMappings = baseline,
                previousBaseSha256 = hash,
                buildIdentity = BuildFingerprint.compute(aa.versionCode ?: 0, File(basePath), aa.splitPaths.map(::File)),
                deepResolveKeys = keys.toSet(),
                streamDeepResolveResults = true,
                shouldContinueDeepResolve = { !deepResolveCancelRequested },
                onDeepResolveResult = { identifier, current, total ->
                    database.updateDeepResolveResults(hash, listOf(identifier))
                    saved++
                    if (identifier.metadata["deepResolveStatus"] == "RESOLVED") resolved++ else inconclusive++
                    reportScanProgress(AnalysisProgress(AnalysisStage.DEEP_RESOLVING, current, total, identifier.key))
                },
                onProgress = ::reportScanProgress
            )
            DeepResolveOutcome(saved, resolved, inconclusive, deepResolveCancelRequested)
        } }
            val resolverRun = withContext(Dispatchers.IO) { database.resolverRunSummary(hash) }
            val pending = withContext(Dispatchers.IO) { database.actionableDeepResolveCount(hash) }
            result.onSuccess { outcome ->
            val stoppedForMemory = !outcome.cancelled && outcome.saved < keys.size
            mutable.value = mutable.value.copy(
                loading = false,
                deepResolveRunning = false,
                scanProgress = null,
                resolverRun = resolverRun,
                deepResolvePending = pending,
                message = when {
                    outcome.cancelled -> "Deep resolve cancelled: ${outcome.resolved} resolved, ${outcome.inconclusive} still inconclusive, $pending pending."
                    stoppedForMemory -> "Paused safely for memory: ${outcome.resolved} resolved, ${outcome.inconclusive} still inconclusive, $pending pending."
                    else -> "Deep resolve complete: ${outcome.resolved} resolved, ${outcome.inconclusive} still inconclusive, $pending pending."
                }
            )
            }.onFailure { error ->
            mutable.value = mutable.value.copy(loading = false, deepResolveRunning = false, scanProgress = null, resolverRun = resolverRun,
                deepResolvePending = pending, message = "Deep resolve stopped: ${error.javaClass.simpleName}: ${error.message}. Completed mappings were preserved.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutable.update { it.copy(message =
                "Deep analysis failed: ${failure.javaClass.simpleName}: ${failure.message}") }
        } finally {
            deepResolveCancelRequested = false
            mutable.update { it.copy(loading = false, deepResolveRunning = false, scanProgress = null) }
            try { keepAnalysisAlive(false) } finally { analysisOperationGate.finish() }
        }
    }

    fun setDynamicOverride(identifier: DiscoveredIdentifier, rawInput: String?) = viewModelScope.launch {
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, saveTargetKey = identifier.key, message = null)
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            require(mutable.value.aa.runtimeTarget) { "Imported archives are read-only; refresh the installed Android Auto before editing." }
            val build = checkNotNull(mutable.value.aa.buildFingerprintSha256) { "No installed Android Auto build" }
            val current = checkNotNull(database.details(build, identifier.key)) { "Flag absent from current build" }
            require(current.inferredType == identifier.inferredType) {
                "Flag type changed; reopen the current installed-build catalog."
            }
            val value = rawInput?.let { DynamicOverrideCodec.validate(current.inferredType, it) }
            require(current.editable && (value == null ||
                suspendedOverrideReason(current, value) == null)) {
                "Getter is no longer safely editable or value exceeds its return type."
            }
            require(probeKeyForBuild(build) != current.key) {
                "Disarm the read-only probe for this getter before editing."
            }
            require(ServiceBridge.dynamicPreferencesOrNull()?.getString(
                AdvancedOverridePolicy.preferenceKey(identifier.key), null) == null) {
                "Restore the advanced baseline before editing normally."
            }
            require(!(identifier.key == ExperimentalBooleanPolicy.KEY &&
                mutable.value.experimentalSnapshot?.buildFingerprint == mutable.value.aa.buildFingerprintSha256)) {
                "Restore the experimental baseline before editing this flag normally."
            }
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            val editor = prefs.edit(); val key = DynamicOverrideCodec.preferenceKey(identifier.key)
            if (rawInput == null) {
                editor.remove(key)
                editor.remove(DynamicOverrideCodec.namePreferenceKey(identifier.key))
            } else {
                editor.putString(key, DynamicOverrideCodec.serialize(checkNotNull(value)))
                editor.putString(DynamicOverrideCodec.namePreferenceKey(identifier.key), identifier.key)
            }
            require(editor.commit()) { "Framework rejected the preference update" }
        } }
        when {
            result.isFailure -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Override rejected: ${result.exceptionOrNull()?.message}")
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED -> {
                val restarted = autoRestartAfterChange() && sendControlRequests(restart = true, headUnit = headUnitAfterRestart())
                mutable.value = mutable.value.copy(saveState = SaveState.SAVED, message = if (restarted)
                    "Override saved and runtime profile published. Android Auto was asked to restart."
                else "Override saved and runtime profile published. Restart Android Auto to apply.")
            }
            else -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Override saved, but runtime profile was NOT published. Check LSPosed and retry publication.")
        }
    }

    // ---- Quick switches: groups of flag overrides behind one switch ----

    /** Whether every flag of [switch] currently has the switch's value. */
    fun flagSwitchOn(switch: io.github.highwindbr.aaxp.core.FlagSwitch): Boolean =
        io.github.highwindbr.aaxp.core.FlagSwitches.isOn(switch, currentOverridesSerialized())

    /** Flags of [switch] that the installed Android Auto's catalogue does not contain; null when that is not known yet. */
    suspend fun flagSwitchMissing(switch: io.github.highwindbr.aaxp.core.FlagSwitch): Set<String>? = withContext(Dispatchers.IO) {
        runCatching {
            val build = mutable.value.aa.buildFingerprintSha256 ?: return@runCatching null
            switch.flags.keys - database.identifiersByKeys(build, switch.flags.keys).keys
        }.getOrNull()
    }

    /**
     * Turns a quick switch on or off by changing its flags one by one through [setDynamicOverride],
     * the same path as editing a flag by hand. Off restores what each flag was before it was turned on.
     */
    fun setFlagSwitch(switch: io.github.highwindbr.aaxp.core.FlagSwitch, on: Boolean) = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256
        val found = if (build == null) emptyMap() else withContext(Dispatchers.IO) { database.identifiersByKeys(build, switch.flags.keys) }
        val missing = switch.flags.keys - found.keys
        if (missing.isNotEmpty()) {
            mutable.value = mutable.value.copy(message = "${switch.title} not changed: this Android Auto version does not have " +
                missing.joinToString(", ") + ". Open Flags and refresh the installed Android Auto if that looks wrong.")
            return@launch
        }
        val stored = io.github.highwindbr.aaxp.core.FlagSwitches.decodeBefore(controlPreferences.getString("flag_switch_before_${switch.id}", null))
        if (on) {
            val before = io.github.highwindbr.aaxp.core.FlagSwitches.beforeValues(switch, currentOverridesSerialized(), stored)
            controlPreferences.edit().putString("flag_switch_before_${switch.id}",
                io.github.highwindbr.aaxp.core.FlagSwitches.encodeBefore(before)).apply()
        }
        switch.flags.forEach { (key, target) ->
            val raw = if (on) target else io.github.highwindbr.aaxp.core.FlagSwitches.valueToRestore(stored, key)
            setDynamicOverride(found.getValue(key), raw).join()
        }
        val ok = flagSwitchOn(switch) == on
        if (ok && !on) controlPreferences.edit().remove("flag_switch_before_${switch.id}").apply()
        if (!ok) mutable.value = mutable.value.copy(message = "${switch.title} could not be fully " + (if (on) "turned on" else "turned off") +
            ". Check the Flags page for the flags involved.")
    }

    // ---- Presets: named sets of normal overrides ----

    private val presetPreferences = application.getSharedPreferences("override_presets", Context.MODE_PRIVATE)

    fun presets(): List<io.github.highwindbr.aaxp.core.Preset> = runCatching {
        presetPreferences.getString("presets", null)?.let { io.github.highwindbr.aaxp.core.Presets.decode(it) }
    }.getOrNull().orEmpty().sortedBy { it.name.lowercase() }

    private fun storePresets(list: List<io.github.highwindbr.aaxp.core.Preset>): Boolean =
        presetPreferences.edit().putString("presets", io.github.highwindbr.aaxp.core.Presets.encode(list)).commit()

    private fun currentOverridesSerialized(): Map<String, String> =
        mutable.value.dynamicOverrides.mapValues { DynamicOverrideCodec.serialize(it.value) }

    fun presetDiff(preset: io.github.highwindbr.aaxp.core.Preset) =
        io.github.highwindbr.aaxp.core.Presets.diff(currentOverridesSerialized(), preset.overrides)

    /** Saves the current normal overrides under [rawName], replacing a preset of the same name. */
    fun savePreset(rawName: String): Boolean {
        val name = io.github.highwindbr.aaxp.core.Presets.cleanName(rawName) ?: return false
        val others = presets().filterNot { it.name.equals(name, true) }
        if (others.size >= io.github.highwindbr.aaxp.core.Presets.MAX_PRESETS) {
            mutable.value = mutable.value.copy(message = "Preset not saved: the limit of ${io.github.highwindbr.aaxp.core.Presets.MAX_PRESETS} presets is reached.")
            return false
        }
        val saved = storePresets(others + io.github.highwindbr.aaxp.core.Preset(name, currentOverridesSerialized(),
            mutable.value.aa.versionName.orEmpty()))
        mutable.value = mutable.value.copy(message = if (saved) "Preset \"$name\" saved." else "Preset not saved.")
        return saved
    }

    fun deletePreset(name: String) {
        val removed = storePresets(presets().filterNot { it.name == name })
        mutable.value = mutable.value.copy(message = if (removed) "Preset \"$name\" deleted." else "Preset not deleted.")
    }

    fun exportPresetsJson(): String = io.github.highwindbr.aaxp.core.Presets.encode(presets())

    /** Adds presets from a file; an imported preset replaces an existing one with the same name. */
    fun importPresetsJson(text: String) {
        val result = runCatching {
            val imported = io.github.highwindbr.aaxp.core.Presets.decode(text)
            val names = imported.map { it.name.lowercase() }.toSet()
            val merged = (presets().filterNot { it.name.lowercase() in names } + imported)
                .take(io.github.highwindbr.aaxp.core.Presets.MAX_PRESETS)
            check(storePresets(merged)) { "could not be stored" }
            imported.size
        }
        mutable.value = mutable.value.copy(message = result.fold(
            { "Imported $it preset(s). Nothing is applied until you tap Apply." }, { "Presets not imported: ${it.message}" }))
    }

    /**
     * Makes the normal overrides equal to [preset]: its values are validated against the installed
     * build exactly like a manual edit, the rest go back to default, and the profile is republished.
     */
    fun applyPreset(preset: io.github.highwindbr.aaxp.core.Preset) = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget) { mutable.value = mutable.value.copy(message = "Imported archives are read-only."); return@launch }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING)
        val result = writePreferencesAndPublishWithResult { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            require(prefs.getString(ExperimentalBooleanPolicy.RECORD_KEY, null) == null &&
                prefs.all.keys.none { it.startsWith(AdvancedOverridePolicy.PREFIX) }) {
                "Restore experimental and advanced baselines before applying a preset."
            }
            val byKey = database.identifiersByKeys(checkNotNull(mutable.value.aa.buildFingerprintSha256), preset.overrides.keys)
            val accepted = mutableMapOf<String, TypedOverride>(); val rejected = mutableSetOf<String>()
            preset.overrides.forEach { (key, raw) ->
                val value = DynamicOverrideCodec.deserialize(raw)
                if (suspendedOverrideReason(byKey[key], value) == null) accepted[key] = checkNotNull(value) else rejected += key
            }
            val editor = prefs.edit()
            mutable.value.dynamicOverrides.keys.filterNot { it in accepted }.forEach { key ->
                editor.remove(DynamicOverrideCodec.preferenceKey(key))
                editor.remove(DynamicOverrideCodec.namePreferenceKey(key))
            }
            accepted.forEach { (key, value) ->
                editor.putString(DynamicOverrideCodec.preferenceKey(key), DynamicOverrideCodec.serialize(value))
                editor.putString(DynamicOverrideCodec.namePreferenceKey(key), key)
            }
            require(editor.commit()) { "Framework rejected the preference update" }
            accepted.size to rejected.size
        } }
        result.onSuccess { (counts, publication) ->
            val (accepted, rejected) = counts
            val published = publication == ProfileMutationCoordinator.Outcome.PUBLISHED
            val restarted = published && autoRestartAfterChange() &&
                withContext(Dispatchers.IO) { runCatching { sendControlRequests(restart = true, headUnit = headUnitAfterRestart()) }.getOrDefault(false) }
            val skipped = if (rejected == 0) "" else " $rejected not available on this Android Auto build were skipped."
            mutable.value = mutable.value.copy(
                saveState = if (published) SaveState.SAVED else SaveState.FAILED,
                message = when {
                    !published -> "Preset \"${preset.name}\" saved, but the runtime profile was NOT published. Check LSPosed.$skipped"
                    restarted -> "Preset \"${preset.name}\" applied ($accepted override(s)). Android Auto was asked to restart.$skipped"
                    else -> "Preset \"${preset.name}\" applied ($accepted override(s)). Restart Android Auto to apply.$skipped"
                })
        }.onFailure { mutable.value = mutable.value.copy(saveState = SaveState.FAILED, message = "Preset not applied: ${it.message}") }
    }

    // ---- Palette colour overrides ----

    fun themeRoleClass(): String? = runCatching {
        ServiceBridge.dynamicPreferencesOrNull()?.getString(io.github.highwindbr.aaxp.runtime.ThemeColors.CLASS_KEY, null)
    }.getOrNull()

    fun themeColorsText(): String = runCatching {
        ServiceBridge.dynamicPreferencesOrNull()?.getString(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY, null)
    }.getOrNull().orEmpty().replace(';', '\n')

    /** Finds the palette-role enum in the installed Android Auto and stores its name for the module. */
    fun locateThemeRoles(onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        val found = withContext(Dispatchers.IO) { runCatching {
            val apk = java.io.File(getApplication<Application>().packageManager
                .getApplicationInfo(InstalledAaScanner.AA_PACKAGE, 0).sourceDir)
            val name = io.github.highwindbr.aaxp.discovery.ThemeRoleLocator.locate(apk)
            if (name != null) check(checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }.edit()
                .putString(io.github.highwindbr.aaxp.runtime.ThemeColors.CLASS_KEY, name).commit()) { "Framework rejected the preference update" }
            name
        } }
        mutable.value = mutable.value.copy(message = found.fold(
            { if (it != null) "Colour roles found. Restart Android Auto once so the module can use them." else "Colour roles not found in this Android Auto build." },
            { "Colour roles not found: ${it.message}" }))
        onDone(found.getOrNull())
    }

    /** Stores "Role=colour" overrides (one per line); blank text removes them all. */
    fun setThemeColors(text: String, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val parsed = io.github.highwindbr.aaxp.runtime.ThemeColors.parse(text)
        val saved = parsed != null && withContext(Dispatchers.IO) { runCatching {
            captureUndo("changing colours")
            val editor = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()).edit()
            val encoded = io.github.highwindbr.aaxp.runtime.ThemeColors.encode(parsed)
            if (encoded == null) editor.remove(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY)
            else editor.putString(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY, encoded)
            editor.commit()
        }.getOrDefault(false) }
        mutable.value = mutable.value.copy(message = when {
            parsed == null -> "Colours not saved: each line must be Role=RRGGBB or Role=AARRGGBB."
            !saved -> "Colours not saved: LSPosed remote preferences unavailable."
            parsed.isEmpty() -> "Colour overrides removed. Restart Android Auto."
            else -> "${parsed.size} colour override(s) saved. Restart Android Auto to see them."
        })
        onDone(saved)
    }

    // ---- Themes: palette colours plus glyph colours, applied together ----

    private val themePreferences = application.getSharedPreferences("car_themes", Context.MODE_PRIVATE)

    fun themeColorsMap(): Map<String, Int> = runCatching {
        io.github.highwindbr.aaxp.runtime.ThemeColors.parse(ServiceBridge.dynamicPreferencesOrNull()
            ?.getString(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY, null))
    }.getOrNull().orEmpty()

    fun carThemes(): List<io.github.highwindbr.aaxp.core.CarTheme> = runCatching {
        themePreferences.getString("themes", null)?.let { io.github.highwindbr.aaxp.core.Themes.decode(it) }
    }.getOrNull().orEmpty().sortedBy { it.name.lowercase() }

    private fun storeCarThemes(list: List<io.github.highwindbr.aaxp.core.CarTheme>): Boolean =
        themePreferences.edit().putString("themes", io.github.highwindbr.aaxp.core.Themes.encode(list)).commit()

    fun saveCarTheme(theme: io.github.highwindbr.aaxp.core.CarTheme): Boolean {
        val name = io.github.highwindbr.aaxp.core.Presets.cleanName(theme.name) ?: return false
        val saved = storeCarThemes(carThemes().filterNot { it.name.equals(name, true) } + theme.copy(name = name, builtIn = false))
        mutable.value = mutable.value.copy(message = if (saved) "Theme \"$name\" kept." else "Theme not kept.")
        return saved
    }

    fun deleteCarTheme(name: String) {
        storeCarThemes(carThemes().filterNot { it.name == name })
        mutable.value = mutable.value.copy(message = "Theme \"$name\" deleted.")
    }

    fun importCarThemes(imported: List<io.github.highwindbr.aaxp.core.CarTheme>) {
        val names = imported.map { it.name.lowercase() }.toSet()
        val stored = storeCarThemes((carThemes().filterNot { it.name.lowercase() in names } + imported)
            .take(io.github.highwindbr.aaxp.core.Presets.MAX_PRESETS))
        mutable.value = mutable.value.copy(message = if (stored) "Imported ${imported.size} theme(s)." else "Themes not imported.")
    }

    /** Writes the role colours and the glyph colours in one step; null [glyphColors] turns glyphs off. */
    fun applyCarTheme(roles: Map<String, Int>, glyphColors: String?) = viewModelScope.launch {
        val restarted = withContext(Dispatchers.IO) { runCatching {
            captureUndo("applying a theme")
            val editor = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }.edit()
            val encoded = io.github.highwindbr.aaxp.runtime.ThemeColors.encode(roles)
            if (encoded == null) editor.remove(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY)
            else editor.putString(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY, encoded)
            if (glyphColors == null) editor.remove(io.github.highwindbr.aaxp.runtime.GlyphIcons.KEY)
            else {
                require(io.github.highwindbr.aaxp.runtime.GlyphIcons.parseColors(glyphColors) != null) { "bad glyph colours" }
                editor.putString(io.github.highwindbr.aaxp.runtime.GlyphIcons.KEY, glyphColors)
            }
            check(editor.commit()) { "Framework rejected the preference update" }
            autoRestartAfterChange() && sendControlRequests(restart = true, headUnit = headUnitAfterRestart())
        } }
        mutable.value = mutable.value.copy(message = restarted.fold(
            { changesWaiting = !it
              if (it) "Theme saved (${roles.size} colour(s)). Android Auto was asked to restart."
              else "Theme saved (${roles.size} colour(s)). Restart Android Auto to see it." },
            { "Theme not saved: ${it.message}" }))
    }

    // ---- Updates from this fork's GitHub releases ----

    private val uiPreferences = application.getSharedPreferences("manager_ui", Context.MODE_PRIVATE)

    fun installedVersionName(): String = runCatching {
        getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0).versionName
    }.getOrNull().orEmpty()

    fun checkUpdatesOnOpen(): Boolean = uiPreferences.getBoolean("check_updates_on_open", false)
    fun setCheckUpdatesOnOpen(enabled: Boolean) { uiPreferences.edit().putBoolean("check_updates_on_open", enabled).apply() }

    /** Asks GitHub for the latest build. [onDone] gets it when it is newer than this one, else null. */
    fun checkForUpdate(quiet: Boolean = false, onDone: (io.github.highwindbr.aaxp.core.AvailableUpdate?, String?) -> Unit) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { Updater.latest() } }
        val update = result.getOrNull()?.takeIf { io.github.highwindbr.aaxp.core.UpdateCheck.isNewer(it, installedVersionName()) }
        if (!quiet || update != null) mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Update check failed: ${result.exceptionOrNull()?.message}"
            update != null -> "Build ${update.versionName} is available."
            result.getOrNull() == null -> "No published build found yet."
            else -> "You have the latest build."
        })
        onDone(update, result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName })
    }

    /** Downloads [update], checks it can install over this app, and opens the system installer. */
    fun installUpdate(update: io.github.highwindbr.aaxp.core.AvailableUpdate, progress: (Int) -> Unit, onDone: () -> Unit) = viewModelScope.launch {
        val context = getApplication<Application>()
        val result = withContext(Dispatchers.IO) { runCatching {
            val apk = Updater.download(context, update) { percent -> viewModelScope.launch { progress(percent) } }
            Updater.problemWith(context, apk)?.let { error(it) }
            Updater.install(context, apk)
        } }
        mutable.value = mutable.value.copy(message = result.fold(
            { "Downloaded ${update.versionName}. Confirm the install when Android asks." },
            { "Update not installed: ${it.message}" }))
        onDone()
    }

    // ---- Look files: a style pack to share, or a backup of the whole look ----

    /** The current look as one file. The wallpaper is included when one is set and small enough. */
    fun exportLook(): String {
        val context = getApplication<Application>()
        val prefs = ServiceBridge.dynamicPreferencesOrNull()
        val image = if (carWallpaperEnabled()) CarWallpaperStudio.sourceFile(context)
            .takeIf { it.isFile && it.length() in 1..io.github.highwindbr.aaxp.core.LookBundles.MAX_IMAGE_BYTES }?.readBytes() else null
        return io.github.highwindbr.aaxp.core.LookBundles.encode(io.github.highwindbr.aaxp.core.LookBundle(
            prefs?.getString(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY, null),
            prefs?.getString(io.github.highwindbr.aaxp.runtime.GlyphIcons.KEY, null),
            prefs?.getString(io.github.highwindbr.aaxp.runtime.StatusBarTweaks.KEY, null),
            carThemes().takeIf { it.isNotEmpty() }?.let { io.github.highwindbr.aaxp.core.Themes.encode(it) },
            presets().takeIf { it.isNotEmpty() }?.let { io.github.highwindbr.aaxp.core.Presets.encode(it) },
            if (image != null) CarWallpaperStudio.settingsJson(CarWallpaperStudio.load(context)) else null, image,
            prefs?.getString(io.github.highwindbr.aaxp.runtime.ResourceColors.KEY, null)))
    }

    /**
     * Applies a look file. Colours, glyph icons and status bar changes are replaced by the
     * file's (a part the file lacks is switched off); themes and flag presets are added to the
     * saved ones, and presets are not applied; the wallpaper is replaced only when the file has one.
     */
    fun importLook(text: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching {
            val bundle = io.github.highwindbr.aaxp.core.LookBundles.decode(text)
            val context = getApplication<Application>()
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            val editor = prefs.edit()
            fun put(key: String, value: String?) { if (value == null) editor.remove(key) else editor.putString(key, value) }
            put(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY, bundle.themeColors)
            put(io.github.highwindbr.aaxp.runtime.GlyphIcons.KEY, bundle.glyphColors)
            put(io.github.highwindbr.aaxp.runtime.StatusBarTweaks.KEY, bundle.statusBar)
            put(io.github.highwindbr.aaxp.runtime.ResourceColors.KEY, bundle.resourceColors)
            val parts = mutableListOf("colours, glyph icons and status bar")
            bundle.wallpaperImage?.let { image ->
                CarWallpaperStudio.sourceFile(context).writeBytes(image)
                CarWallpaperStudio.save(context, CarWallpaperStudio.settingsFrom(bundle.wallpaperSettings))
                val (rendered, rail) = checkNotNull(CarWallpaperStudio.renderSaved(context)) { "The wallpaper in this file is not an image" }
                writeCarWallpaper(editor, rendered, rail)
                parts += "wallpaper"
            }
            check(editor.commit()) { "Framework rejected the preference update" }
            bundle.themesJson?.let { json ->
                val imported = io.github.highwindbr.aaxp.core.Themes.decode(json)
                val names = imported.map { it.name.lowercase() }.toSet()
                storeCarThemes((carThemes().filterNot { it.name.lowercase() in names } + imported)
                    .take(io.github.highwindbr.aaxp.core.Presets.MAX_PRESETS))
                parts += "${imported.size} theme(s)"
            }
            bundle.presetsJson?.let { json ->
                val imported = io.github.highwindbr.aaxp.core.Presets.decode(json)
                val names = imported.map { it.name.lowercase() }.toSet()
                storePresets((presets().filterNot { it.name.lowercase() in names } + imported)
                    .take(io.github.highwindbr.aaxp.core.Presets.MAX_PRESETS))
                parts += "${imported.size} flag preset(s), not applied"
            }
            val restarted = autoRestartAfterChange() && sendControlRequests(restart = true, headUnit = headUnitAfterRestart())
            parts.joinToString(", ") to restarted
        } }
        mutable.value = mutable.value.copy(message = result.fold(
            { (parts, restarted) -> "Look loaded: $parts." + if (restarted) " Android Auto was asked to restart." else " Restart Android Auto to see it." },
            { "Look not loaded, nothing was changed: ${it.message}" }))
        onDone()
    }

    // ---- Colours outside the palette roles (Android Auto's fixed resource palette) ----

    fun resourceColors(): Map<String, Int> = runCatching {
        io.github.highwindbr.aaxp.runtime.ResourceColors.parse(ServiceBridge.dynamicPreferencesOrNull()
            ?.getString(io.github.highwindbr.aaxp.runtime.ResourceColors.KEY, null))
    }.getOrNull().orEmpty()

    fun setResourceColors(colors: Map<String, Int>) = viewModelScope.launch {
        val restarted = withContext(Dispatchers.IO) { runCatching {
            captureUndo("changing the extra colours")
            val editor = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }.edit()
            val encoded = io.github.highwindbr.aaxp.runtime.ResourceColors.encode(colors)
            if (encoded == null) editor.remove(io.github.highwindbr.aaxp.runtime.ResourceColors.KEY)
            else editor.putString(io.github.highwindbr.aaxp.runtime.ResourceColors.KEY, encoded)
            check(editor.commit()) { "Framework rejected the preference update" }
            autoRestartAfterChange() && sendControlRequests(restart = true, headUnit = headUnitAfterRestart())
        } }
        mutable.value = mutable.value.copy(message = restarted.fold(
            { (if (colors.isEmpty()) "Extra colours removed." else "Extra colours saved.") +
                if (it) " Android Auto was asked to restart." else " Restart Android Auto to see it." },
            { "Extra colours not saved: ${it.message}" }))
    }

    // ---- Launcher (the grid of app icons) ----

    fun launcherConfig(): String? = runCatching {
        ServiceBridge.dynamicPreferencesOrNull()?.getString(io.github.highwindbr.aaxp.runtime.LauncherTweaks.KEY, null)
    }.getOrNull()

    fun setLauncherConfig(config: String?) = viewModelScope.launch {
        val restarted = withContext(Dispatchers.IO) { runCatching {
            val editor = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }.edit()
            if (config == null) editor.remove(io.github.highwindbr.aaxp.runtime.LauncherTweaks.KEY)
            else {
                require(io.github.highwindbr.aaxp.runtime.LauncherTweaks.parse(config) != null) { "nothing to change" }
                editor.putString(io.github.highwindbr.aaxp.runtime.LauncherTweaks.KEY, config)
            }
            check(editor.commit()) { "Framework rejected the preference update" }
            autoRestartAfterChange() && sendControlRequests(restart = true, headUnit = headUnitAfterRestart())
        } }
        mutable.value = mutable.value.copy(message = restarted.fold(
            { changesWaiting = !it
              (if (config == null) "Launcher shows app names again." else "Launcher set to icons only.") +
                if (it) " Android Auto was asked to restart." else " Restart Android Auto to see it." },
            { "Launcher setting not saved: ${it.message}" }))
    }

    // ---- Status bar, crash safe mode and screen layout captures ----

    fun statusBarConfig(): String? = runCatching {
        ServiceBridge.dynamicPreferencesOrNull()?.getString(io.github.highwindbr.aaxp.runtime.StatusBarTweaks.KEY, null)
    }.getOrNull()

    fun setStatusBarConfig(config: String?) = viewModelScope.launch {
        val restarted = withContext(Dispatchers.IO) { runCatching {
            captureUndo("changing the status bar")
            val editor = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }.edit()
            if (config == null) editor.remove(io.github.highwindbr.aaxp.runtime.StatusBarTweaks.KEY)
            else {
                require(io.github.highwindbr.aaxp.runtime.StatusBarTweaks.parse(config) != null) { "nothing to change" }
                editor.putString(io.github.highwindbr.aaxp.runtime.StatusBarTweaks.KEY, config)
            }
            check(editor.commit()) { "Framework rejected the preference update" }
            autoRestartAfterChange() && sendControlRequests(restart = true, headUnit = headUnitAfterRestart())
        } }
        mutable.value = mutable.value.copy(message = restarted.fold(
            { changesWaiting = !it
              (if (config == null) "Status bar changes off." else "Status bar changes saved.") +
                if (it) " Android Auto was asked to restart." else " Restart Android Auto to see it." },
            { "Status bar not changed: ${it.message}" }))
    }

    private val reportPreferences = ModuleReportProvider.preferences(application)

    /** When and where crash safe mode last switched the look off; null when it has not. */
    fun safeModeReport(): String? {
        val time = reportPreferences.getLong(ModuleReportProvider.KEY_SAFE_TIME, 0L)
        if (time == 0L) return null
        val whenText = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
            .format(java.util.Date(time))
        return "reported $whenText"
    }

    fun resetSafeMode(onDone: () -> Unit = {}) = viewModelScope.launch {
        val reset = withContext(Dispatchers.IO) { runCatching {
            val editor = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()).edit()
            editor.putString(io.github.highwindbr.aaxp.runtime.SafeMode.EPOCH_KEY, System.currentTimeMillis().toString())
            editor.commit()
        }.getOrDefault(false) }
        if (reset) reportPreferences.edit().remove(ModuleReportProvider.KEY_SAFE_TIME)
            .remove(ModuleReportProvider.KEY_SAFE_PROCESS).apply()
        mutable.value = mutable.value.copy(message = if (reset)
            "Look changes are back on from the next Android Auto restart. If it crashes again, change the last thing you set."
        else "Not changed: LSPosed remote preferences unavailable.")
        onDone()
    }

    private fun layoutFiles(): List<java.io.File> =
        ModuleReportProvider.dumpDirectory(getApplication()).listFiles().orEmpty()
            .filter { it.isFile && it.length() in 1..ModuleReportProvider.MAX_DUMP_BYTES }.sortedBy { it.name }

    /** Captured screen layouts as (process, size in bytes). */
    fun screenLayoutCaptures(): List<Pair<String, Long>> = layoutFiles().map {
        it.name.removePrefix("layout_").removeSuffix(".txt").substringAfterLast("gearhead").trim('_').ifEmpty { "main" } to it.length()
    }

    fun screenLayoutText(): String = layoutFiles().joinToString("\n\n") { it.readText() }

    fun clearScreenLayouts() { layoutFiles().forEach { it.delete() } }

    fun requestScreenLayout() = viewModelScope.launch {
        val sent = withContext(Dispatchers.IO) { runCatching {
            val editor = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()).edit()
            editor.putString(io.github.highwindbr.aaxp.runtime.ControlRequests.DUMP_KEY,
                io.github.highwindbr.aaxp.runtime.ControlRequests.make(System.currentTimeMillis(), java.util.UUID.randomUUID().toString()))
            editor.commit()
        }.getOrDefault(false) }
        mutable.value = mutable.value.copy(message = if (sent)
            "Asked Android Auto for its screen layout. It only answers while it is running and showing something."
        else "Request failed: LSPosed remote preferences unavailable.")
    }

    // ---- Glyph-style app icons ----

    /** The stored "glyph,background" colours, or null when the mode is off. */
    fun glyphIconColors(): String? = runCatching {
        ServiceBridge.dynamicPreferencesOrNull()?.getString(io.github.highwindbr.aaxp.runtime.GlyphIcons.KEY, null)
    }.getOrNull()

    fun setGlyphIcons(colors: String?, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val valid = colors == null || io.github.highwindbr.aaxp.runtime.GlyphIcons.parseColors(colors) != null
        val saved = valid && withContext(Dispatchers.IO) { runCatching {
            captureUndo("changing the icons")
            val editor = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()).edit()
            if (colors == null) editor.remove(io.github.highwindbr.aaxp.runtime.GlyphIcons.KEY)
            else editor.putString(io.github.highwindbr.aaxp.runtime.GlyphIcons.KEY, colors)
            editor.commit()
        }.getOrDefault(false) }
        if (saved) changesWaiting = true
        mutable.value = mutable.value.copy(message = when {
            !valid -> "Glyph icons not changed: colours must be RRGGBB or AARRGGBB."
            !saved -> "Glyph icons not changed: LSPosed remote preferences unavailable."
            colors == null -> "Glyph icons off. Restart Android Auto to see normal icons again."
            else -> "Glyph icons on. Restart Android Auto; icons Android Auto has already cached may take a while to refresh."
        })
        onDone(saved)
    }

    // ---- Undo for the look ----

    private val undoKeeper = io.github.highwindbr.aaxp.core.UndoKeeper(File(application.filesDir, "look_undo.json"))
    private val undoImage get() = File(getApplication<Application>().filesDir, "look_undo_wallpaper")

    /** True while there is a saved look that "Undo last change" can put back. */
    var canUndo by androidx.compose.runtime.mutableStateOf(undoKeeper.exists())
        private set
    var undoLabel by androidx.compose.runtime.mutableStateOf(undoKeeper.load()?.label.orEmpty())
        private set

    private fun wallpaperSourceStamp(): String? =
        CarWallpaperStudio.sourceFile(getApplication()).takeIf { it.isFile }
            ?.let { io.github.highwindbr.aaxp.core.LookUndo.stamp(it.length(), it.lastModified()) }

    /** The wallpaper image the car is using, as far as it is known; the current file when nothing was recorded. */
    private fun appliedWallpaperStamp(): String? {
        val recorded = controlPreferences.getString("wallpaper_applied_stamp", null)
        if (recorded != null || !carWallpaperEnabled()) return recorded
        // Not recorded yet (an older install): take the current file to be the one in use, and remember that.
        return wallpaperSourceStamp()?.also { controlPreferences.edit().putString("wallpaper_applied_stamp", it).apply() }
    }

    private fun snapshotNow(label: String, wallpaperStamp: String?): io.github.highwindbr.aaxp.core.LookSnapshot {
        val prefs = ServiceBridge.dynamicPreferencesOrNull()
        return io.github.highwindbr.aaxp.core.LookSnapshot(label,
            prefs?.getString(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY, null),
            prefs?.getString(io.github.highwindbr.aaxp.runtime.GlyphIcons.KEY, null),
            prefs?.getString(io.github.highwindbr.aaxp.runtime.StatusBarTweaks.KEY, null),
            prefs?.getString(io.github.highwindbr.aaxp.runtime.ResourceColors.KEY, null),
            if (wallpaperStamp != null) CarWallpaperStudio.settingsJson(CarWallpaperStudio.load(getApplication())) else null,
            wallpaperStamp, carWallpaperEnabled())
    }

    /** Copies the wallpaper image in use aside for undo if that has not been done. Blocking: call off Main. */
    fun keepWallpaperImageForUndo() {
        runCatching {
            val plan = io.github.highwindbr.aaxp.core.LookUndo.wallpaperPlan(carWallpaperEnabled(), wallpaperSourceStamp(),
                appliedWallpaperStamp(), controlPreferences.getString("undo_image_stamp", null))
            if (plan.copyImage) {
                CarWallpaperStudio.sourceFile(getApplication()).copyTo(undoImage, overwrite = true)
                controlPreferences.edit().putString("undo_image_stamp", plan.snapshotStamp).apply()
            }
        }
    }

    /**
     * Keeps a copy of the current look so it can be undone. Blocking file and preference I/O: call
     * off Main. Never throws. If the copy cannot be made the previous one is dropped as well.
     */
    fun captureUndo(label: String) {
        val stored = undoKeeper.capture {
            checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            val plan = io.github.highwindbr.aaxp.core.LookUndo.wallpaperPlan(carWallpaperEnabled(), wallpaperSourceStamp(),
                appliedWallpaperStamp(), controlPreferences.getString("undo_image_stamp", null))
            if (plan.copyImage) {
                CarWallpaperStudio.sourceFile(getApplication()).copyTo(undoImage, overwrite = true)
                controlPreferences.edit().putString("undo_image_stamp", plan.snapshotStamp).apply()
            }
            snapshotNow(label, plan.snapshotStamp)
        }
        canUndo = stored
        undoLabel = if (stored) label else ""
    }

    /** Keeps an undo copy, then runs [block] (on Main). For changes made outside this class. */
    fun undoThen(label: String, block: () -> Unit) {
        viewModelScope.launch { withContext(Dispatchers.IO) { captureUndo(label) }; block() }
    }

    /** Remembers that the current wallpaper source file is the image the car uses. */
    fun markWallpaperApplied() {
        if (carWallpaperEnabled()) controlPreferences.edit().putString("wallpaper_applied_stamp", wallpaperSourceStamp()).apply()
    }

    /** The wallpaper source file's stamp now; compare before and after a change made elsewhere. */
    fun wallpaperStampNow(): String? = wallpaperSourceStamp()

    /** Records the source file as applied only if it changed since [before], so a failed change is not mistaken for an applied one. */
    fun markWallpaperAppliedIfChanged(before: String?) {
        if (wallpaperSourceStamp() != before) markWallpaperApplied()
    }

    /** Puts back the look kept before the last change. */
    fun undoLastChange() = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching {
            val kept = checkNotNull(undoKeeper.load()) { "The saved copy cannot be read" }
            val context = getApplication<Application>()
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            val now = snapshotNow(kept.label, if (carWallpaperEnabled()) appliedWallpaperStamp() else null)
            val editor = prefs.edit()
            fun put(key: String, value: String?) { if (value == null) editor.remove(key) else editor.putString(key, value) }
            put(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY, kept.themeColors)
            put(io.github.highwindbr.aaxp.runtime.GlyphIcons.KEY, kept.glyphColors)
            put(io.github.highwindbr.aaxp.runtime.StatusBarTweaks.KEY, kept.statusBar)
            put(io.github.highwindbr.aaxp.runtime.ResourceColors.KEY, kept.resourceColors)
            val imageKept = kept.wallpaperStamp != null && kept.wallpaperSettings != null && undoImage.isFile &&
                controlPreferences.getString("undo_image_stamp", null) == kept.wallpaperStamp
            val action = io.github.highwindbr.aaxp.core.LookUndo.wallpaperAction(kept, imageKept)
            val restoredSettings = kept.wallpaperSettings?.let { CarWallpaperStudio.settingsFrom(it) }
            when (action) {
                io.github.highwindbr.aaxp.core.LookUndo.WallpaperAction.RESTORE -> {
                    // Render from the kept copy first; the current image and settings are only replaced once the commit succeeded.
                    val (rendered, rail) = checkNotNull(CarWallpaperStudio.renderFrom(undoImage, checkNotNull(restoredSettings))) {
                        "The kept wallpaper is not an image"
                    }
                    writeCarWallpaper(editor, rendered, rail)
                }
                io.github.highwindbr.aaxp.core.LookUndo.WallpaperAction.REMOVE -> {
                    editor.remove(io.github.highwindbr.aaxp.runtime.CarWallpaper.VERSION_KEY)
                    editor.remove(io.github.highwindbr.aaxp.runtime.CarWallpaper.RAIL_KEY)
                }
                io.github.highwindbr.aaxp.core.LookUndo.WallpaperAction.LEAVE -> Unit
            }
            check(editor.commit()) { "Framework rejected the preference update" }
            if (action == io.github.highwindbr.aaxp.core.LookUndo.WallpaperAction.RESTORE) {
                undoImage.copyTo(CarWallpaperStudio.sourceFile(context), overwrite = true)
                CarWallpaperStudio.save(context, checkNotNull(restoredSettings))
                markWallpaperApplied()
            }
            undoKeeper.clear()
            val restarted = autoRestartAfterChange() && sendControlRequests(restart = true, headUnit = headUnitAfterRestart())
            val effective = when (action) {
                io.github.highwindbr.aaxp.core.LookUndo.WallpaperAction.RESTORE -> kept
                io.github.highwindbr.aaxp.core.LookUndo.WallpaperAction.REMOVE ->
                    io.github.highwindbr.aaxp.core.LookSnapshot(kept.label, kept.themeColors, kept.glyphColors, kept.statusBar,
                        kept.resourceColors, null, null, false)
                io.github.highwindbr.aaxp.core.LookUndo.WallpaperAction.LEAVE ->
                    io.github.highwindbr.aaxp.core.LookSnapshot(kept.label, kept.themeColors, kept.glyphColors, kept.statusBar,
                        kept.resourceColors, now.wallpaperSettings, now.wallpaperStamp, now.wallpaperOn)
            }
            io.github.highwindbr.aaxp.core.LookUndo.restoredMessage(
                io.github.highwindbr.aaxp.core.LookUndo.differences(now, effective), kept.label) to restarted
        } }
        canUndo = undoKeeper.exists()
        result.onSuccess { changesWaiting = !it.second }
        mutable.value = mutable.value.copy(message = result.fold(
            { (text, restarted) -> text + if (restarted) " Android Auto was asked to restart." else " Restart Android Auto to see it." },
            { "Nothing was undone: ${it.message}" }))
    }

    // ---- One-tap styles ----

    /**
     * Applies a whole look in one go: palette colours, extra colours, single-colour icons, status
     * bar changes and, when the look has them, the wallpaper's darken and blur. A part the look
     * does not have is switched off. An undo copy is kept first.
     */
    fun applyLook(look: io.github.highwindbr.aaxp.core.CarTheme) = viewModelScope.launch {
        val plan = io.github.highwindbr.aaxp.core.LookPlan.of(look)
        val restarted = withContext(Dispatchers.IO) { runCatching {
            captureUndo("applying ${look.name}")
            val context = getApplication<Application>()
            val editor = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }.edit()
            fun put(key: String, value: String?) { if (value == null) editor.remove(key) else editor.putString(key, value) }
            put(io.github.highwindbr.aaxp.runtime.ThemeColors.COLORS_KEY, plan.themeColors)
            put(io.github.highwindbr.aaxp.runtime.GlyphIcons.KEY, plan.glyphColors)
            put(io.github.highwindbr.aaxp.runtime.StatusBarTweaks.KEY, plan.statusBar)
            put(io.github.highwindbr.aaxp.runtime.ResourceColors.KEY, plan.resourceColors)
            if (plan.wallpaperDim != null || plan.wallpaperBlur != null) {
                val current = CarWallpaperStudio.load(context)
                CarWallpaperStudio.save(context, current.copy(dim = plan.wallpaperDim ?: current.dim,
                    blurLevel = plan.wallpaperBlur ?: current.blurLevel))
                if (carWallpaperEnabled()) CarWallpaperStudio.renderSaved(context)?.let { (rendered, rail) -> writeCarWallpaper(editor, rendered, rail) }
            }
            check(editor.commit()) { "Framework rejected the preference update" }
            autoRestartAfterChange() && sendControlRequests(restart = true, headUnit = headUnitAfterRestart())
        } }
        restarted.onSuccess { changesWaiting = !it }
        mutable.value = mutable.value.copy(message = restarted.fold(
            { "${look.name} applied. " + if (it) "Android Auto was asked to restart." else "Restart Android Auto to see it." },
            { "${look.name} not applied: ${it.message}" }))
    }

    // ---- Car wallpaper ----

    fun carWallpaperEnabled(): Boolean = runCatching {
        io.github.highwindbr.aaxp.runtime.CarWallpaper.isEnabled(ServiceBridge.dynamicPreferencesOrNull()
            ?.getString(io.github.highwindbr.aaxp.runtime.CarWallpaper.VERSION_KEY, null))
    }.getOrDefault(false)

    /** Stores [bitmap] as the car wallpaper for the module and enables it. */
    private fun jpeg(bitmap: android.graphics.Bitmap): ByteArray = java.io.ByteArrayOutputStream().use { out ->
        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)) { "Image could not be encoded" }
        out.toByteArray()
    }

    /** Writes the wallpaper files and returns an editor action that publishes their version. */
    private fun writeCarWallpaper(editor: android.content.SharedPreferences.Editor, bitmap: android.graphics.Bitmap, rail: android.graphics.Bitmap?) {
        ServiceBridge.writeRemoteFile(io.github.highwindbr.aaxp.runtime.CarWallpaper.FILE_NAME, jpeg(bitmap))
        if (rail != null) ServiceBridge.writeRemoteFile(io.github.highwindbr.aaxp.runtime.CarWallpaper.RAIL_FILE_NAME, jpeg(rail))
        val version = System.currentTimeMillis().toString()
        editor.putString(io.github.highwindbr.aaxp.runtime.CarWallpaper.VERSION_KEY, version)
        if (rail != null) editor.putString(io.github.highwindbr.aaxp.runtime.CarWallpaper.RAIL_KEY, version)
        else editor.remove(io.github.highwindbr.aaxp.runtime.CarWallpaper.RAIL_KEY)
    }

    internal fun storeCarWallpaper(bitmap: android.graphics.Bitmap, rail: android.graphics.Bitmap? = null,
                          settings: CarWallpaperStudio.Saved? = null, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching {
            // The undo copy is taken here, before anything is written, so cancelling the editor never replaces it.
            captureUndo("changing the wallpaper")
            settings?.let { CarWallpaperStudio.save(getApplication(), it) }
            val editor = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }.edit()
            // The version changes last, so the module never sees a new version with an old file.
            writeCarWallpaper(editor, bitmap, rail)
            check(editor.commit()) { "Framework rejected the preference update" }
            markWallpaperApplied()
            "${bitmap.width}x${bitmap.height}"
        } }
        mutable.value = mutable.value.copy(message = result.fold(
            { changesWaiting = true
              "Car wallpaper saved ($it). In Android Auto's settings turn on \"Use phone's wallpaper\", then restart Android Auto." },
            { "Car wallpaper not saved: ${it.message}" }))
        onDone(result.isSuccess)
    }

    fun reportMessage(text: String) { mutable.value = mutable.value.copy(message = text) }

    fun clearCarWallpaper(onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val cleared = withContext(Dispatchers.IO) { runCatching {
            captureUndo("removing the wallpaper")
            ServiceBridge.dynamicPreferencesOrNull()?.edit()
                ?.remove(io.github.highwindbr.aaxp.runtime.CarWallpaper.VERSION_KEY)
                ?.remove(io.github.highwindbr.aaxp.runtime.CarWallpaper.RAIL_KEY)?.commit() == true
        }.getOrDefault(false) }
        if (cleared) changesWaiting = true
        mutable.value = mutable.value.copy(message = if (cleared)
            "Car wallpaper removed. Android Auto shows the phone's wallpaper again after a restart."
        else "Car wallpaper not removed: LSPosed remote preferences unavailable.")
        onDone(cleared)
    }

    // ---- Android Auto controls (restart / developer head unit server) ----

    private val controlPreferences = application.getSharedPreferences("manager_ui", Context.MODE_PRIVATE)

    fun autoRestartAfterChange(): Boolean = controlPreferences.getBoolean(AUTO_RESTART_PREF, false)

    fun setAutoRestartAfterChange(enabled: Boolean) {
        controlPreferences.edit().putBoolean(AUTO_RESTART_PREF, enabled).apply()
    }

    /** DHU mode: every restart requested from the Manager also brings the head unit server back up. */
    fun headUnitAfterRestart(): Boolean = controlPreferences.getBoolean(HEAD_UNIT_AFTER_RESTART_PREF, false)

    fun setHeadUnitAfterRestart(enabled: Boolean) {
        controlPreferences.edit().putBoolean(HEAD_UNIT_AFTER_RESTART_PREF, enabled).apply()
    }

    /** Writes one-shot requests the module acts on. Blocking remote-preference I/O: call off Main. */
    private fun sendControlRequests(restart: Boolean, headUnit: Boolean): Boolean {
        val editor = ServiceBridge.dynamicPreferencesOrNull()?.edit() ?: return false
        val request = io.github.highwindbr.aaxp.runtime.ControlRequests.make(
            System.currentTimeMillis(), java.util.UUID.randomUUID().toString())
        // The head unit request is written with the restart so the restarted process still sees it.
        if (headUnit) editor.putString(io.github.highwindbr.aaxp.runtime.ControlRequests.HEAD_UNIT_KEY, request)
        if (restart) editor.putString(io.github.highwindbr.aaxp.runtime.ControlRequests.RESTART_KEY, request)
        if (restart) lastRestartRequest = request
        return editor.commit()
    }

    @Volatile private var lastRestartRequest: String? = null

    /** Ends Android Auto's processes with root, for when the module inside it does not answer. */
    private fun stopAndroidAutoWithRoot(): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "pkill -f com.google.android.projection.gearhead")
            .redirectErrorStream(true).start()
        // Drain the output so a chatty su cannot block, then wait a bounded time.
        Thread { runCatching { process.inputStream.readBytes() } }.start()
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { process.destroy(); return false }
        process.exitValue() == 0
    }.getOrDefault(false)

    fun requestAndroidAutoControl(restart: Boolean, headUnit: Boolean) = viewModelScope.launch {
        val sent = withContext(Dispatchers.IO) { runCatching { sendControlRequests(restart, headUnit) }.getOrDefault(false) }
        if (sent && restart) {
            changesWaiting = false
            mutable.value = mutable.value.copy(message = "Asked Android Auto to restart…")
            kotlinx.coroutines.delay(2_500)
            val heard = reportPreferences.getString(ModuleReportProvider.KEY_RESTART_ACK, null) == lastRestartRequest
            if (!heard) {
                val stopped = withContext(Dispatchers.IO) { stopAndroidAutoWithRoot() }
                mutable.value = mutable.value.copy(message = if (stopped)
                    "Android Auto did not answer, so the module is not loaded in it yet. It was stopped with root instead; the module loads when it starts again."
                else "Android Auto did not answer and root was not available. Either it is not running, or the module is not loaded in it: " +
                    "check that AA Experiments is enabled for Android Auto in LSPosed, then force stop Android Auto once in the phone's settings.")
                return@launch
            }
        }
        mutable.value = mutable.value.copy(message = when {
            !sent -> "Request failed: LSPosed remote preferences unavailable."
            restart && headUnit -> "Asked Android Auto to restart; the head unit server starts a few seconds after it is back up."
            restart -> "Android Auto is restarting."
            else -> "Asked Android Auto to start the head unit server. It only reacts while it is running; check LSPosed events (CONTROL_HEAD_UNIT_*)."
        })
    }

    private fun deltaSummary(items: List<EditableCatalogDelta>) = CatalogChangeSummary(
        items.count { it.kind == EditableDeltaKind.ADDED },
        items.count { it.kind == EditableDeltaKind.REMOVED },
        items.count { it.kind == EditableDeltaKind.CHANGED }
    )

    private fun hiddenKeysForBuild(fingerprint: String?): Set<String> {
        if (fingerprint == null) return emptySet()
        if (hiddenPreferences.getString("fingerprint", null) != fingerprint) {
            hiddenPreferences.edit().putString("fingerprint", fingerprint).remove("keys").apply()
            return emptySet()
        }
        return hiddenPreferences.getStringSet("keys", emptySet())?.toSet().orEmpty()
    }

    /** Affects only the All tab for this precise Android Auto build fingerprint. */
    fun hideSuspendedFromAll(preferenceKey: String) {
        val current = mutable.value
        val fingerprint = current.aa.buildFingerprintSha256 ?: return
        if (current.suspendedOverrides.none { it.preferenceKey == preferenceKey }) return
        val previous = hiddenKeysForBuild(fingerprint)
        val next = previous + preferenceKey
        hiddenPreferences.edit().putString("fingerprint", fingerprint)
            .putStringSet("keys", next).apply()
        mutable.value = current.copy(hiddenSuspendedKeys = next)
    }

    private fun probeKeyForBuild(buildFingerprint: String): String? =
        probePreferences.getString("build", null)?.takeIf { it == buildFingerprint }
            ?.let { probePreferences.getString("key", null) }

    /** Only arms a read-only method-call trace for the exact installed build. */
    /** Preflight12-A: choose one reviewed DOUBLE getter for a no-write runtime test. */
    fun setDoubleGetterProbe(key: String, enabled: Boolean) = viewModelScope.launch {
        val aa = mutable.value.aa
        val build = aa.buildFingerprintSha256
        if (!aa.runtimeTarget || build == null ||
            !GetterProbePolicy.isInspectedDoubleBuild(aa.baseSha256) ||
            key !in GetterProbePolicy.doubleCandidates) {
            mutable.value = mutable.value.copy(
                message = "DOUBLE probe unavailable: unsupported Android Auto build or getter.")
            return@launch
        }
        val id = withContext(Dispatchers.IO) { database.details(build, key) }
        if (id == null) {
            mutable.value = mutable.value.copy(message = "DOUBLE getter absent from the installed catalog.")
            return@launch
        }
        if (enabled && probeKeyForBuild(build) != null) {
            mutable.value = mutable.value.copy(
                message = "Disarm the existing getter probe before selecting another.")
            return@launch
        }
        setReadOnlyProbe(id, enabled)
    }

    fun setReadOnlyProbe(identifier: DiscoveredIdentifier, enabled: Boolean) = viewModelScope.launch {
        val aa = mutable.value.aa
        val fingerprint = aa.buildFingerprintSha256 ?: return@launch
        if (!aa.runtimeTarget || !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "Read-only probes need the installed Android Auto and LSPosed service.")
            return@launch
        }
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            if (enabled) {
                val current = checkNotNull(database.details(fingerprint, identifier.key)) { "Flag is not in the installed catalog" }
                require(GetterProbePolicy.issue(current) == null) { GetterProbePolicy.issue(current) ?: "Not eligible" }
                require(current.key == identifier.key)
                if (current.key in GetterProbePolicy.doubleCandidates) {
                    require(GetterProbePolicy.isInspectedDoubleBuild(aa.baseSha256)) {
                        "DOUBLE probe requires the exact inspected base APK."
                    }
                    val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
                    require(prefs.getString(DynamicOverrideCodec.preferenceKey(current.key), null) == null) {
                        "A normal DOUBLE override already owns this getter. Restore it first."
                    }
                }
                require(ServiceBridge.dynamicPreferencesOrNull()?.getString(
                    AdvancedOverridePolicy.preferenceKey(current.key), null) == null) {
                    "Restore the advanced baseline before probing the getter."
                }
                require(current.key != ExperimentalBooleanPolicy.KEY ||
                    ExperimentalBooleanSnapshot.decode(ServiceBridge.dynamicPreferencesOrNull()?.getString(
                        ExperimentalBooleanPolicy.RECORD_KEY, null)) == null) {
                    "Restore the experimental baseline before arming this getter for read-only probing."
                }
                require(probePreferences.edit().putString("build", fingerprint)
                    .putString("key", current.key).commit()) { "Failed to save probe choice" }
            } else {
                require(probeKeyForBuild(fingerprint) == identifier.key) { "Probe already changed" }
                require(probePreferences.edit().clear().commit()) { "Failed to disable probe" }
            }
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Getter probe rejected: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() != ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Getter probe preference saved, but runtime profile was NOT published. Check LSPosed."
            enabled -> "Read-only probe armed and profile published. Restart Android Auto; observe AAX_EVENT in logcat."
            else -> "Probe disabled and profile published. Restart Android Auto to remove its installed hook."
        })
    }

    /** Only one BOOLEAN experiment is permitted in this beta4 preflight. */
    fun setExperimentalBoolean(identifier: DiscoveredIdentifier, value: Boolean) = viewModelScope.launch {
        val aa = mutable.value.aa
        val build = aa.buildFingerprintSha256
        if (!aa.runtimeTarget || build == null || !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "Experimental overrides require the installed Android Auto and LSPosed service.")
            return@launch
        }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, saveTargetKey = identifier.key, message = null)
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val current = checkNotNull(database.details(build, identifier.key)) { "Flag not found for installed Android Auto" }
            require(ExperimentalBooleanPolicy.issue(current) == null) {
                ExperimentalBooleanPolicy.issue(current) ?: "Getter not eligible"
            }
            require(probeKeyForBuild(build) != current.key) {
                "Disarm the read-only getter probe and restart Android Auto before editing."
            }
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service disconnected" }
            val all = prefs.all
            val recordRaw = all[ExperimentalBooleanPolicy.RECORD_KEY] as? String
            val previous = ExperimentalBooleanSnapshot.decode(recordRaw)
            require(recordRaw == null || previous != null) { "Existing experimental snapshot is unreadable; refusing to overwrite its baseline." }
            require(previous == null || ExperimentalBooleanPolicy.matches(current, previous, build)) {
                "Previous experimental snapshot belongs to another build or getter; cannot reapply automatically."
            }
            val regular = DynamicOverrideCodec.preferenceKey(current.key)
            val name = DynamicOverrideCodec.namePreferenceKey(current.key)
            val previousNormalRaw = all[regular] as? String
            require(previous != null || previousNormalRaw == null ||
                DynamicOverrideCodec.deserialize(previousNormalRaw)?.type == DiscoveredType.BOOLEAN) {
                "Existing normal override is malformed; refusing to replace its saved value."
            }
            val updated = if (previous != null) previous.copy(value = value, active = true) else
                ExperimentalBooleanPolicy.snapshot(
                    current, build, value, previousNormalRaw, all[name] as? String,
                    System.currentTimeMillis()
                )
            require(prefs.edit().putString(ExperimentalBooleanPolicy.RECORD_KEY, updated.encode())
                .remove(regular).remove(name).commit()) { "Failed to atomically save baseline and experiment." }
        } }
        when {
            result.isFailure -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Experimental override rejected: ${result.exceptionOrNull()?.message}")
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED &&
                mutable.value.experimentalProfileReady ->
                mutable.value = mutable.value.copy(saveState = SaveState.SAVED,
                    message = "Experimental value saved and profile published. Restart Android Auto to install the new hook; check OVERRIDE_RETURNED.")
            else -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Experimental value saved, but its hook profile is NOT ready or published. Check LSPosed before restarting AA.")
        }
    }

    fun pauseExperimentalBoolean() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val record = checkNotNull(ExperimentalBooleanSnapshot.decode(
                prefs.getString(ExperimentalBooleanPolicy.RECORD_KEY, null))) { "No experimental snapshot" }
            require(record.buildFingerprint == build) { "The experiment belongs to another build." }
            require(prefs.edit().putString(ExperimentalBooleanPolicy.RECORD_KEY,
                record.copy(active = false).encode()).commit()) { "Unable to pause experiment" }
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Pause failed: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Experimental override paused and profile published. Restart Android Auto to unload the previous hook."
            else -> "Experimental pause saved, but runtime profile was NOT published. Check LSPosed and restart AA only after recovery."
        })
    }

    fun restoreExperimentalBaseline() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val record = checkNotNull(ExperimentalBooleanSnapshot.decode(
                prefs.getString(ExperimentalBooleanPolicy.RECORD_KEY, null))) { "No valid baseline to restore" }
            require(record.buildFingerprint == build) { "Cannot restore a baseline onto a different Android Auto build." }
            val regular = DynamicOverrideCodec.preferenceKey(ExperimentalBooleanPolicy.KEY)
            val name = DynamicOverrideCodec.namePreferenceKey(ExperimentalBooleanPolicy.KEY)
            val editor = prefs.edit().remove(ExperimentalBooleanPolicy.RECORD_KEY)
            if (record.previousOverride == null) editor.remove(regular)
            else editor.putString(regular, record.previousOverride)
            if (record.previousName == null) editor.remove(name)
            else editor.putString(name, record.previousName)
            require(editor.commit()) { "Atomic baseline restoration failed" }
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Baseline restoration rejected: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Original override state restored and profile published (including previous absence). Restart Android Auto."
            else -> "Original override baseline restored, but runtime profile was NOT published. Check LSPosed."
        })
    }

    /**
     * Beta4-only shadow handoff for the already proven Assistant LONG getter.
     * Preserve the normal preference LONG:3 throughout the experiment: ONLY
     * the build-scoped profile decides which single hook owns the getter.
     */
    fun startLongHandoff() = viewModelScope.launch {
        val aa = mutable.value.aa
        val build = aa.buildFingerprintSha256
        if (!aa.runtimeTarget || build == null || !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "LONG handoff requires installed AA and LSPosed.")
            return@launch
        }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING,
            saveTargetKey = LongHandoffPolicy.KEY, message = null)
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val id = checkNotNull(database.details(build, LongHandoffPolicy.KEY))
            require(LongHandoffPolicy.issue(id, aa.baseSha256) == null) {
                LongHandoffPolicy.issue(id, aa.baseSha256) ?: "Not eligible"
            }
            require(probeKeyForBuild(build) != LongHandoffPolicy.KEY) {
                "Disarm the competing getter probe first."
            }
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val recordKey = AdvancedOverridePolicy.preferenceKey(LongHandoffPolicy.KEY)
            require(prefs.getString(recordKey, null) == null) {
                "Restore the existing Advanced record before starting a new handoff."
            }
            val normalKey = DynamicOverrideCodec.preferenceKey(LongHandoffPolicy.KEY)
            val nameKey = DynamicOverrideCodec.namePreferenceKey(LongHandoffPolicy.KEY)
            val record = LongHandoffPolicy.record(id, build, aa.baseSha256,
                prefs.getString(normalKey, null), prefs.getString(nameKey, null),
                System.currentTimeMillis())
            require(prefs.edit().putString(recordKey, record.encode()).commit()) {
                "Could not atomically save the handoff record."
            }
        } }
        when {
            result.isFailure -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "LONG handoff rejected: ${result.exceptionOrNull()?.message}")
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                mutable.value = mutable.value.copy(saveState = SaveState.SAVED,
                    message = "LONG 3→3 handoff published; original LONG:3 is intact. Restart Android Auto and check ADVANCED_OVERRIDE_RETURNED.")
            else -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "LONG handoff saved, but runtime profile was NOT published. Keep original LONG:3 and check LSPosed.")
        }
    }

    fun setAdvancedOverride(identifier: DiscoveredIdentifier, raw: String) = viewModelScope.launch {
        val aa = mutable.value.aa
        val build = aa.buildFingerprintSha256
        if (!aa.runtimeTarget || build == null || !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "Advanced overrides require the installed AA and LSPosed service.")
            return@launch
        }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, saveTargetKey = identifier.key)
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val id = checkNotNull(database.details(build, identifier.key))
            require(AdvancedOverridePolicy.issue(id) == null) { AdvancedOverridePolicy.issue(id) ?: "Not eligible" }
            require(probeKeyForBuild(build) != id.key) { "Disarm the read-only probe first." }
            require(id.key != ExperimentalBooleanPolicy.KEY) { "Restore the legacy experimental baseline first." }
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val all = prefs.all
            val recordKey = AdvancedOverridePolicy.preferenceKey(id.key)
            val serialized = all[recordKey] as? String
            val earlier = AdvancedOverrideRecord.decode(serialized)
            require(serialized == null || earlier != null) { "Unreadable advanced snapshot; refusing to overwrite." }
            require(earlier == null || AdvancedOverridePolicy.matches(id, earlier, build)) {
                "Saved experiment belongs to another build or getter; keep it suspended."
            }
            val normalKey = DynamicOverrideCodec.preferenceKey(id.key)
            val nameKey = DynamicOverrideCodec.namePreferenceKey(id.key)
            val record = if (earlier == null) AdvancedOverridePolicy.newRecord(id, build, raw,
                all[normalKey] as? String, all[nameKey] as? String, System.currentTimeMillis())
            else earlier.copy(value = DynamicOverrideCodec.validate(earlier.type, raw).encodedValue, active = true)
            require(prefs.edit().putString(recordKey, record.encode()).remove(normalKey).remove(nameKey).commit()) {
                "Unable to atomically preserve the baseline."
            }
        } }
        when {
            result.isFailure -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Advanced override rejected: ${result.exceptionOrNull()?.message}")
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                mutable.value = mutable.value.copy(saveState = SaveState.SAVED,
                    message = "Advanced value saved and profile published. Restart Android Auto and inspect LSPosed events.")
            else -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Advanced value saved, but runtime profile was NOT published. Check LSPosed and retry.")
        }
    }

    fun pauseAdvancedOverride(key: String) = setAdvancedActivity(key, false)
    fun resumeAdvancedOverride(key: String) = setAdvancedActivity(key, true)

    private fun setAdvancedActivity(key: String, enabled: Boolean) = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val storageKey = AdvancedOverridePolicy.preferenceKey(key)
            val record = checkNotNull(AdvancedOverrideRecord.decode(prefs.getString(storageKey, null)))
            require(record.key == key && record.buildFingerprint == build) { "Record from another AA build." }
            if (enabled) {
                val id = checkNotNull(database.details(build, key))
                require(AdvancedOverridePolicy.matches(id, record, build, mutable.value.aa.baseSha256)) {
                    "Method/type changed; cannot resume."
                }
                if (record.handoffMode != null) require(LongHandoffPolicy.baselineIntact(
                    record, prefs.getString(DynamicOverrideCodec.preferenceKey(key), null),
                    prefs.getString(DynamicOverrideCodec.namePreferenceKey(key), null))) {
                    "Original LONG:3 changed; cannot resume."
                }
            }
            require(prefs.edit().putString(storageKey, record.copy(active = enabled).encode()).commit())
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Advanced change failed: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Advanced override ${if (enabled) "resumed" else "paused"} and profile published; restart AA."
            else -> "Advanced ${if (enabled) "resume" else "pause"} saved, but runtime profile was NOT published. Check LSPosed."
        })
    }

    fun restoreAdvancedBaseline(key: String) = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val storageKey = AdvancedOverridePolicy.preferenceKey(key)
            val record = checkNotNull(AdvancedOverrideRecord.decode(prefs.getString(storageKey, null))) {
                "Missing or unreadable baseline"
            }
            require(record.key == key && record.buildFingerprint == build) {
                "Cannot restore previous build's baseline on the current build."
            }
            val normalKey = DynamicOverrideCodec.preferenceKey(key)
            val nameKey = DynamicOverrideCodec.namePreferenceKey(key)
            val edit = prefs.edit().remove(storageKey)
            if (record.handoffMode != null) {
                require(record.handoffMode == LongHandoffPolicy.MODE &&
                    record.buildFingerprint == build &&
                    LongHandoffPolicy.baselineIntact(record,
                        prefs.getString(normalKey, null), prefs.getString(nameKey, null))) {
                    "Normal LONG baseline changed; refuse to overwrite it."
                }
                // Shadow handoff NEVER removed the normal preference.
            } else {
                if (record.previousOverride == null) edit.remove(normalKey)
                else edit.putString(normalKey, record.previousOverride)
                if (record.previousName == null) edit.remove(nameKey)
                else edit.putString(nameKey, record.previousName)
            }
            require(edit.commit())
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Restore failed: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Advanced baseline restored and profile published, including previous absence. Restart AA."
            else -> "Advanced baseline restored, but runtime profile was NOT published. Check LSPosed."
        })
    }

    fun pauseAllAdvanced() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val editor = prefs.edit()
            prefs.all.forEach { (name, value) ->
                if (name.startsWith(AdvancedOverridePolicy.PREFIX)) {
                    val record = checkNotNull(AdvancedOverrideRecord.decode(value as? String))
                    require(AdvancedOverridePolicy.preferenceKey(record.key) == name) { "Invalid advanced storage key." }
                    if (record.buildFingerprint == build) editor.putString(name, record.copy(active = false).encode())
                }
            }
            require(editor.commit())
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Pause all rejected: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "All current-build Advanced overrides paused and profile published; restart AA."
            else -> "Advanced overrides paused in storage, but runtime profile was NOT published. Check LSPosed."
        })
    }

    fun restoreAllAdvanced() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublishWithResult { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val records = prefs.all.filterKeys { it.startsWith(AdvancedOverridePolicy.PREFIX) }
                .map { (key, value) ->
                    val record = checkNotNull(AdvancedOverrideRecord.decode(value as? String)) {
                        "Unreadable advanced record; abort restore."
                    }
                    require(AdvancedOverridePolicy.preferenceKey(record.key) == key) { "Invalid record key." }
                    key to record
                }.filter { it.second.buildFingerprint == build }
            val editor = prefs.edit()
            records.forEach { (storageKey, record) ->
                editor.remove(storageKey)
                val normalKey = DynamicOverrideCodec.preferenceKey(record.key)
                val nameKey = DynamicOverrideCodec.namePreferenceKey(record.key)
                if (record.handoffMode != null) {
                    require(record.handoffMode == LongHandoffPolicy.MODE &&
                        LongHandoffPolicy.baselineIntact(record,
                            prefs.getString(normalKey, null), prefs.getString(nameKey, null))) {
                        "Normal LONG baseline changed; abort entire restore-all."
                    }
                } else {
                    if (record.previousOverride == null) editor.remove(normalKey)
                    else editor.putString(normalKey, record.previousOverride)
                    if (record.previousName == null) editor.remove(nameKey)
                    else editor.putString(nameKey, record.previousName)
                }
            }
            require(editor.commit())
            records.size
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Bulk restore rejected: ${result.exceptionOrNull()?.message}"
            result.getOrThrow().second == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "${result.getOrThrow().first} current-build Advanced baselines restored and profile published; restart Android Auto."
            else -> "${result.getOrThrow().first} Advanced baselines restored in storage, but runtime profile was NOT published. Check LSPosed."
        })
    }

    fun discardSuspendedOverride(preferenceKey: String) = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget || !preferenceKey.startsWith(DynamicOverrideCodec.PREFIX)) return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            val editor = prefs.edit().remove(preferenceKey)
            DynamicOverrideCodec.nameKeyForPreference(preferenceKey)?.let(editor::remove)
            require(editor.commit()) { "Framework rejected override removal" }
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Discard failed: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Suspended override removed and runtime profile published."
            else -> "Suspended override removed, but runtime profile was NOT published. Check LSPosed."
        })
    }

    fun restoreAllDynamic() = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget) return@launch
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, saveTargetKey = null)
        val result = writePreferencesAndPublish {
            withContext(Dispatchers.IO) {
                val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "LSPosed is disconnected" }
                val editor = prefs.edit().remove(ExperimentalBooleanPolicy.RECORD_KEY)
                prefs.all.keys.filter { it.startsWith(DynamicOverrideCodec.PREFIX) ||
                    it.startsWith(DynamicOverrideCodec.NAME_PREFIX) ||
                    it.startsWith(AdvancedOverridePolicy.PREFIX) }.forEach(editor::remove)
                require(editor.commit()) { "Framework rejected reset" }
            }
        }
        mutable.value = mutable.value.copy(
            saveState = if (result.getOrNull() == ProfileMutationCoordinator.Outcome.PUBLISHED)
                SaveState.SAVED else SaveState.FAILED,
            message = when {
                result.isFailure -> "Reset rejected: ${result.exceptionOrNull()?.message}"
                result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                    "All override records removed and runtime profile published. Restart Android Auto."
                else -> "Override records removed, but runtime profile was NOT published. Check LSPosed."
            })
    }

    fun exportDynamicJson(): String = JSONObject().apply {
        put("schemaVersion", 3); put("package", InstalledAaScanner.AA_PACKAGE)
        put("versionCode", mutable.value.aa.versionCode ?: JSONObject.NULL); put("buildFingerprintSha256", mutable.value.aa.buildFingerprintSha256 ?: JSONObject.NULL)
        put("overrides", JSONObject().apply { mutable.value.dynamicOverrides.forEach { (key, value) -> put(key, DynamicOverrideCodec.serialize(value)) } })
        put("suspended", JSONObject().apply {
            mutable.value.suspendedOverrides.forEach { entry ->
                entry.serializedValue?.let { serialized ->
                    put(entry.preferenceKey, JSONObject().apply {
                        put("raw", serialized)
                        put("identifier", entry.identifierKey ?: JSONObject.NULL)
                    })
                }
            }
        })
    }.toString(2)

    /** Only the exact current AA build may export/restore all special baselines. */
    fun exportCompleteRecoveryJson(): String {
        val aa = mutable.value.aa
        require(aa.runtimeTarget && aa.buildFingerprintSha256 != null && aa.versionCode != null) {
            "Installed Android Auto is not available."
        }
        val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) {
            "LSPosed remote preferences are disconnected."
        }
        return FullRecoveryBundle.create(prefs.all, aa.buildFingerprintSha256, aa.versionCode)
    }

    fun reportRecoveryMessage(text: String) {
        mutable.update { it.copy(message = text.take(250)) }
    }

    /** Exact replacement of managed keys only; never touches debug/traces or slot pointers. */
    fun restoreCompleteRecoveryJson(text: String) = viewModelScope.launch {
        mutable.update { it.copy(saveState = SaveState.SAVING, message = "Checking complete recovery bundle…") }
        val result = withContext(Dispatchers.IO) { runCatching {
            profileRefreshMutex.withLock {
                val aa = mutable.value.aa
                val build = checkNotNull(aa.buildFingerprintSha256) { "No installed build fingerprint" }
                val version = checkNotNull(aa.versionCode) { "No installed AA version code" }
                require(aa.runtimeTarget) { "Imported archives are read-only." }
                val bundle = FullRecoveryBundle.decode(text, build, version)
                val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) {
                    "LSPosed is not connected"
                }
                val current = prefs.all
                // Paused/historical snapshots must be recoverable even if their
                // former getter is no longer eligible. Only hooks to be resumed
                // on this build require current runtime-eligibility checks.
                val activeAdv = bundle.activeAdvancedRecords()
                val exp = bundle.activeExperimentalRecord()
                val keys = activeAdv.map { it.key }.toSet() +
                    listOfNotNull(if (exp != null) ExperimentalBooleanPolicy.KEY else null)
                val identifiers = database.identifiersByKeys(build, keys)
                activeAdv.forEach { record ->
                    val id = checkNotNull(identifiers[record.key]) {
                        "Advanced getter is absent from the installed build: ${record.key}"
                    }
                    require(AdvancedOverridePolicy.matches(id, record, build, aa.baseSha256)) {
                        "Advanced getter or safety policy changed: ${record.key}"
                    }
                    if (record.handoffMode != null) require(LongHandoffPolicy.baselineIntact(
                        record,
                        bundle.entries[DynamicOverrideCodec.preferenceKey(record.key)],
                        bundle.entries[DynamicOverrideCodec.namePreferenceKey(record.key)])) {
                        "LONG handoff baseline missing or modified."
                    }
                }
                if (exp != null) {
                    val id = checkNotNull(identifiers[ExperimentalBooleanPolicy.KEY]) {
                        "Experimental BOOLEAN getter is missing."
                    }
                    require(ExperimentalBooleanPolicy.matches(id, exp, build)) {
                        "Experimental BOOLEAN getter or policy no longer matches."
                    }
                }
                require(mutable.value.aa.buildFingerprintSha256 == build) {
                    "Android Auto build changed before restoring."
                }
                val edit = prefs.edit()
                current.keys.filter(FullRecoveryBundle::managedKey).forEach(edit::remove)
                bundle.entries.forEach { (key, value) -> edit.putString(key, value) }
                require(edit.commit()) { "Framework rejected the complete recovery transaction." }
                // Publish under the same gate used by normal/Advanced edits.
                // The committed recovery remains available if publication fails.
                bundle to refreshOverridesAndProfileLocked()
            }
        } }
        result.onSuccess { (bundle, publicationSucceeded) ->
            val published = publicationSucceeded &&
                mutable.value.aa.buildFingerprintSha256 == bundle.fingerprint
            mutable.update { it.copy(saveState = if (published) SaveState.SAVED else SaveState.FAILED,
                message = if (published)
                    "Restored ${bundle.normal} normal, ${bundle.advanced} Advanced and ${bundle.experimental} Experimental records. Restart Android Auto after checking settings."
                else "Recovery records were restored, but the profile was NOT published. Check LSPosed connection and Diagnostics before restarting Android Auto.") }
        }.onFailure { ex ->
            mutable.update { it.copy(saveState = SaveState.FAILED,
                message = "Complete recovery rejected without changes: ${ex.message}") }
        }
    }

    fun importDynamicJson(text: String) = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget) { mutable.value = mutable.value.copy(message = "Imported archives are read-only."); return@launch }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING)
        val result = writePreferencesAndPublishWithResult { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            require(prefs.getString(ExperimentalBooleanPolicy.RECORD_KEY, null) == null &&
                prefs.all.keys.none { it.startsWith(AdvancedOverridePolicy.PREFIX) }) {
                "Restore experimental and advanced baselines before importing standard overrides."
            }
            val root = JSONObject(text); val schema = root.getInt("schemaVersion"); require(schema in 2..3) { "Unsupported schema" }
            val sameBuild = if (schema >= 3) root.optString("buildFingerprintSha256").equals(mutable.value.aa.buildFingerprintSha256, true)
                else root.optString("baseSha256").equals(mutable.value.aa.baseSha256, true)
            require(sameBuild) { "Backup belongs to another Android Auto build; nothing was applied." }
            val values = root.getJSONObject("overrides"); val keys = buildSet { values.keys().forEach(::add) }
            val byKey = database.identifiersByKeys(checkNotNull(mutable.value.aa.buildFingerprintSha256), keys)
            val accepted = mutableMapOf<String, TypedOverride>(); val rejected = mutableSetOf<String>()
            keys.forEach { key ->
                val identifier = byKey[key]
                val value = DynamicOverrideCodec.deserialize(values.getString(key))
                if (suspendedOverrideReason(identifier, value) == null) accepted[key] = checkNotNull(value)
                else rejected += key
            }
            val editor=prefs.edit()
            accepted.forEach { (key,value) ->
                editor.putString(DynamicOverrideCodec.preferenceKey(key),DynamicOverrideCodec.serialize(value))
                editor.putString(DynamicOverrideCodec.namePreferenceKey(key),key)
            }
            val suspendedImport = root.optJSONObject("suspended")
            suspendedImport?.keys()?.forEach { preferenceKey ->
                if (!preferenceKey.startsWith(DynamicOverrideCodec.PREFIX)) return@forEach
                val record = suspendedImport.optJSONObject(preferenceKey) ?: return@forEach
                val raw = record.optString("raw")
                if (DynamicOverrideCodec.deserialize(raw) == null) return@forEach
                // Never take over a currently valid mapping restored above.
                if (accepted.keys.any { DynamicOverrideCodec.preferenceKey(it) == preferenceKey }) return@forEach
                editor.putString(preferenceKey, raw)
                val identifierName = record.optString("identifier").takeIf { it.isNotBlank() && it != "null" }
                if (identifierName != null && DynamicOverrideCodec.preferenceKey(identifierName) == preferenceKey)
                    editor.putString(DynamicOverrideCodec.namePreferenceKey(identifierName), identifierName)
            }
            require(editor.commit())
            accepted.size to rejected.size
        } }
        result.onSuccess { (counts, publication) ->
            val (accepted, rejected) = counts
            mutable.value = mutable.value.copy(
                saveState = if (publication == ProfileMutationCoordinator.Outcome.PUBLISHED) SaveState.SAVED else SaveState.FAILED,
                message = if (publication == ProfileMutationCoordinator.Outcome.PUBLISHED)
                    "Imported $accepted override(s); rejected $rejected; runtime profile published."
                else "Imported $accepted override(s); rejected $rejected; runtime profile was NOT published. Check LSPosed.")
        }.onFailure { mutable.value = mutable.value.copy(saveState = SaveState.FAILED, message = "Import failed: ${it.message}") }
    }

    /** Prevent queued edits from writing into a catalog that changed before the lock. */
    private fun requireCurrentInstalledBuild(expected: String?) {
        require(expected != null && mutable.value.aa.runtimeTarget &&
            mutable.value.aa.buildFingerprintSha256 == expected) {
            "Installed Android Auto build changed before saving; reopen the current catalog."
        }
    }

    private suspend fun <T> writePreferencesAndPublishWithResult(write: suspend () -> T):
        Result<Pair<T, ProfileMutationCoordinator.Outcome>> {
        val expectedBuild = mutable.value.aa.buildFingerprintSha256
        return try {
            Result.success(profileMutationCoordinator.executeWithResult({
                requireCurrentInstalledBuild(expectedBuild)
                write()
            }) { refreshOverridesAndProfileLocked() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    /**
     * Keep the preference commit and profile publication in one serialized scope.
     * A saved value remains recoverable when publication fails, but is not reported
     * as applied until the verified slot pointer is committed.
     */
    private suspend fun writePreferencesAndPublish(write: suspend () -> Unit):
        Result<ProfileMutationCoordinator.Outcome> {
        val expectedBuild = mutable.value.aa.buildFingerprintSha256
        return try {
            Result.success(profileMutationCoordinator.execute({
                requireCurrentInstalledBuild(expectedBuild)
                write()
            }) { refreshOverridesAndProfileLocked() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private suspend fun refreshOverridesAndProfile(): Boolean =
        profileRefreshMutex.withLock { refreshOverridesAndProfileLocked() }

    private suspend fun refreshOverridesAndProfileLocked(): Boolean {
        val aa = mutable.value.aa
        if (!aa.runtimeTarget || aa.baseSha256 == null || aa.buildFingerprintSha256 == null) return false
        val result = withContext(Dispatchers.IO) { runCatching {
            // A transient LSPosed disconnection must not publish an empty profile.
            // The last selected verified slot remains active until reconnection.
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) {
                "Remote preferences unavailable; preserving last published profile"
            }
            val allPrefs = prefs.all.toMutableMap()
            // Retire the two historical diagnostics no longer reachable from
            // the UI. Disable persisted VTC trace before publishing a new
            // profile; disarm old DOUBLE read-only probes without affecting
            // unrelated probes or experiment baselines.
            val obsoleteTraceKey =
                io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.KEY
            if (io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.enabled(
                    allPrefs[obsoleteTraceKey] as? String)) {
                check(prefs.edit().putString(obsoleteTraceKey, "false").commit()) {
                    "Could not disable retired Video Timing trace"
                }
                allPrefs[obsoleteTraceKey] = "false"
            }
            if (probePreferences.getString("key", null) in GetterProbePolicy.doubleCandidates.keys) {
                check(probePreferences.edit().clear().commit()) {
                    "Could not disarm retired DOUBLE probe"
                }
            }
            val protocolTraceOn = false
            val mediaStartupTraceOn =
                io.github.highwindbr.aaxp.runtime.MediaStartupTrace.isSupported(aa.baseSha256) &&
                io.github.highwindbr.aaxp.runtime.MediaStartupTrace.enabled(
                    allPrefs[io.github.highwindbr.aaxp.runtime.MediaStartupTrace.KEY] as? String)
            // Report the saved opt-in even when a new AA build is unsupported,
            // so Diagnostics can offer a visible OFF switch.
            val mediaStartupAutoOn =
                io.github.highwindbr.aaxp.runtime.MediaStartupAutoGate.enabled(
                    allPrefs[io.github.highwindbr.aaxp.runtime.MediaStartupAutoGate.KEY] as? String)
            val savedLab = mediaLabPreferences.getString(aa.buildFingerprintSha256, null)
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?.takeIf { it.optString("buildFingerprintSha256") == aa.buildFingerprintSha256 &&
                    it.optString("baseSha256") == aa.baseSha256 &&
                    it.optString("mode") in setOf(
                        io.github.highwindbr.aaxp.runtime.MediaStartupLabProfile.LEGACY_MODE,
                        io.github.highwindbr.aaxp.runtime.MediaStartupLabProfile.MODE) }
            val labRoles = savedLab?.optJSONObject("roles")
            val routeAAvailable = labRoles?.has("launch.auxiliary") == true
            val routeBAvailable = labRoles?.has("launch.startCarActivity") == true
            val navigationObserverAvailable = labRoles?.has("launch.navigationObserver") == true ||
                routeBAvailable
            val labAutoCapable = (routeAAvailable || routeBAvailable) &&
                navigationObserverAvailable && savedLab?.optString("mode") ==
                io.github.highwindbr.aaxp.runtime.MediaStartupLabProfile.MODE &&
                labRoles?.has("activity.firstProjected") == true
            val labAutoEnabled = labAutoCapable &&
                (mediaLabPreferences.getString("auto-selected:${aa.buildFingerprintSha256}", "false") == "true")
            // Restore remote preference only for this installed build.
            if ((allPrefs[
                io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY] as? String)
                != labAutoEnabled.toString()) {
                check(prefs.edit().putString(
                    io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY,
                    labAutoEnabled.toString()).commit()) { "Unable to synchronize automatic media opening" }
            }
            val requestedRoute = mediaLabPreferences.getString(
                "route:${aa.buildFingerprintSha256}", "A")
            val selectedRoute = io.github.highwindbr.aaxp.runtime.MediaStartupRoutePolicy.select(
                requestedRoute, routeAAvailable, routeBAvailable) ?: "A"
            if (savedLab != null && selectedRoute != requestedRoute) {
                check(mediaLabPreferences.edit().putString(
                    "route:${aa.buildFingerprintSha256}", selectedRoute).commit()) {
                    "Could not persist the proven Media Startup fallback route"
                }
            }
            // Remote preference applies in :projection; restore each build's
            // preferred route before publishing its verified profile.
            if ((allPrefs[
                io.github.highwindbr.aaxp.runtime.MediaStartupLabGate.ROUTE_KEY] as? String)
                != selectedRoute && savedLab != null) {
                check(prefs.edit().putString(
                    io.github.highwindbr.aaxp.runtime.MediaStartupLabGate.ROUTE_KEY,
                    selectedRoute).commit()) { "Unable to synchronize preferred media opening route" }
            }
            val snapshot = allPrefs.mapNotNull { (key, value) ->
                if (key.startsWith(DynamicOverrideCodec.PREFIX) && value is String) key to value else null
            }.toMap()
            val names = database.historicalOverrideNames(snapshot.keys)
            val resolved = database.identifiersByPreferenceKeys(aa.buildFingerprintSha256, snapshot.keys).toMap()
            val nameEditor = prefs.edit()
            var recoveredNames = 0
            val advancedRecords = allPrefs.mapNotNull { (key, raw) ->
                if (!key.startsWith(AdvancedOverridePolicy.PREFIX)) null
                else AdvancedOverrideRecord.decode(raw as? String)?.takeIf {
                    AdvancedOverridePolicy.preferenceKey(it.key) == key
                }
            }
            val experimental = ExperimentalBooleanSnapshot.decode(
                allPrefs[ExperimentalBooleanPolicy.RECORD_KEY] as? String)
            val activeExperiment = experimental?.takeIf { it.active && it.buildFingerprint == aa.buildFingerprintSha256 }
            val experimentId = activeExperiment?.let { database.details(aa.buildFingerprintSha256, ExperimentalBooleanPolicy.KEY) }
            val approvedExperiment = experimentId?.takeIf { id ->
                ExperimentalBooleanPolicy.matches(id, checkNotNull(activeExperiment), aa.buildFingerprintSha256)
            }
            val active = mutableMapOf<String, TypedOverride>(); val suspended = mutableListOf<SuspendedOverride>(); val mappings = mutableListOf<DynamicProfile.Mapping>()
            val claimedNormalGetters = mutableMapOf<Triple<String, String, String>, String>()
            // Stable arbitration avoids registering two competing hooks on the same getter.
            snapshot.toSortedMap().forEach { (preferenceKey, raw) ->
                val identifier = resolved[preferenceKey]
                val value = DynamicOverrideCodec.deserialize(raw)
                val reason = suspendedOverrideReason(identifier, value)
                val nameKey = DynamicOverrideCodec.nameKeyForPreference(preferenceKey)
                val storedName = nameKey?.let { allPrefs[it] as? String }
                    ?.takeIf { DynamicOverrideCodec.preferenceKey(it) == preferenceKey }
                val history = names[preferenceKey]
                val identifierName = identifier?.key ?: storedName ?: history?.first
                if (storedName == null && identifierName != null && nameKey != null) {
                    nameEditor.putString(nameKey, identifierName)
                    recoveredNames++
                }
                if (reason != null) suspended += SuspendedOverride(
                    preferenceKey, identifierName, value?.type?.name, identifier?.inferredType?.name,
                    reason, value?.encodedValue, raw, history?.second
                )
                else {
                    val safeIdentifier = checkNotNull(identifier); val safeValue = checkNotNull(value); val method = safeIdentifier.getterCandidates.single()
                    val cls = method.className.removePrefix("L").removeSuffix(";").replace('/', '.')
                    if (!claimNormalGetter(claimedNormalGetters, safeIdentifier.key,
                            cls, method.methodName, method.descriptor)) {
                        suspended += SuspendedOverride(preferenceKey, identifierName,
                            safeValue.type.name, safeIdentifier.inferredType.name,
                            SuspendedOverrideReason.SUSPENDED_TARGET_COLLISION,
                            safeValue.encodedValue, raw, history?.second)
                    } else {
                        active[safeIdentifier.key] = safeValue
                        mappings += DynamicProfile.Mapping(safeIdentifier.key, preferenceKey,
                            cls, method.methodName, method.descriptor, safeIdentifier.inferredType.name)
                    }
                }
            }
            if (recoveredNames > 0) require(nameEditor.commit()) { "Failed to persist recovered mapping names" }
            val probeKey = probeKeyForBuild(aa.buildFingerprintSha256)
            val probeIdentifier = probeKey?.let { database.details(aa.buildFingerprintSha256, it) }
            val selected = probeIdentifier?.takeIf {
                GetterProbePolicy.issue(it) == null &&
                (it.key !in GetterProbePolicy.doubleCandidates ||
                    GetterProbePolicy.isInspectedDoubleBuild(aa.baseSha256))
            }
            val probes = selected?.let { id ->
                val method = id.getterCandidates.single()
                val className = method.className.removePrefix("L").removeSuffix(";").replace('/', '.')
                val conflict = mappings.any { it.className == className && it.methodName == method.methodName && it.descriptor == method.descriptor }
                if (conflict) emptyList() else listOf(DynamicProfile.Probe(id.key, className, method.methodName, method.descriptor))
            }.orEmpty()
            val experimentalMappings = approvedExperiment?.let { id ->
                val getter = id.getterCandidates.single()
                val className = getter.className.removePrefix("L").removeSuffix(";").replace('/', '.')
                val conflict = mappings.any { it.className == className && it.methodName == getter.methodName && it.descriptor == getter.descriptor } ||
                    probes.any { it.className == className && it.methodName == getter.methodName && it.descriptor == getter.descriptor }
                if (conflict) emptyList() else listOf(DynamicProfile.ExperimentalMapping(
                    id.key, className, getter.methodName, getter.descriptor
                ))
            }.orEmpty()
            // Handoff controls hook ownership, not the stored normal LONG:3.
            // Keep the preference intact so an already-running normal hook can
            // continue returning 3 until AA restarts into its new profile.
            val handoff = advancedRecords.singleOrNull {
                it.key == LongHandoffPolicy.KEY && it.active &&
                    it.buildFingerprint == aa.buildFingerprintSha256 &&
                    it.handoffMode == LongHandoffPolicy.MODE
            }
            val handoffId = handoff?.let {
                database.details(aa.buildFingerprintSha256, LongHandoffPolicy.KEY)
            }
            val handoffValid = handoff != null && handoffId != null &&
                LongHandoffPolicy.matches(handoffId, handoff,
                    aa.buildFingerprintSha256, aa.baseSha256) &&
                LongHandoffPolicy.baselineIntact(handoff,
                    allPrefs[DynamicOverrideCodec.preferenceKey(LongHandoffPolicy.KEY)] as? String,
                    allPrefs[DynamicOverrideCodec.namePreferenceKey(LongHandoffPolicy.KEY)] as? String)
            if (handoffValid) {
                mappings.removeAll { it.key == LongHandoffPolicy.KEY }
            }
            val reserved = (mappings.map { Triple(it.className, it.methodName, it.descriptor) } +
                probes.map { Triple(it.className, it.methodName, it.descriptor) } +
                experimentalMappings.map { Triple(it.className, it.methodName, it.descriptor) }).toMutableSet()
            val advancedMappings = mutableListOf<DynamicProfile.AdvancedMapping>()
            advancedRecords.filter { it.active && it.buildFingerprint == aa.buildFingerprintSha256 }.forEach { record ->
                val id = database.details(aa.buildFingerprintSha256, record.key)
                if (id != null && AdvancedOverridePolicy.matches(id, record, aa.buildFingerprintSha256, aa.baseSha256) &&
                    (record.handoffMode == null || (handoffValid && record.key == LongHandoffPolicy.KEY))) {
                    val getter = id.getterCandidates.single()
                    val cls = getter.className.removePrefix("L").removeSuffix(";").replace('/', '.')
                    val token = Triple(cls, getter.methodName, getter.descriptor)
                    if (reserved.add(token)) advancedMappings += DynamicProfile.AdvancedMapping(
                        id.key, cls, getter.methodName, getter.descriptor,
                        id.inferredType.name, AdvancedOverridePolicy.preferenceKey(id.key)
                    )
                }
            }
            val published = ServiceBridge.publishProfile(aa.buildFingerprintSha256, aa.baseSha256,
                aa.versionCode ?: 0, aa.packageArtifactSha256, aa.dexSha256, mappings, probes,
                experimentalMappings, advancedMappings, protocolTraceOn, mediaStartupTraceOn, savedLab)
            OverrideRefreshOutcome(active, suspended, published.isSuccess, published.exceptionOrNull(),
                experimental, published.isSuccess && experimentalMappings.isNotEmpty(),
                advancedRecords, if (published.isSuccess) advancedMappings.size else 0,
                protocolTraceOn, mediaStartupTraceOn, mediaStartupAutoOn,
                savedLab != null, selectedRoute, labAutoCapable, labAutoEnabled,
                routeAAvailable, routeBAvailable)
        } }
        // Build state is handed off under the same mutex: stale queued refreshes
        // cannot overwrite an imported archive or a newly installed AA build.
        if (mutable.value.aa.buildFingerprintSha256 != aa.buildFingerprintSha256 ||
            !mutable.value.aa.runtimeTarget) return false
        result.onSuccess { outcome -> mutable.value=mutable.value.copy(dynamicOverrides=outcome.active,
            suspendedOverrides=outcome.suspended, probeTargetKey=probeKeyForBuild(aa.buildFingerprintSha256),
            experimentalSnapshot=outcome.experimental, experimentalProfileReady=outcome.experimentalReady,
            advancedRecords=outcome.advanced, advancedPublishedCount=outcome.advancedPublishedCount,
            videoTimingTraceEnabled=outcome.videoTimingTraceEnabled,
            mediaStartupTraceEnabled=outcome.mediaStartupTraceEnabled,
            mediaStartupAutoEnabled=outcome.mediaStartupAutoEnabled,
            mediaLabArmed=outcome.mediaLabArmed,
            mediaLabRoute=outcome.mediaLabRoute,
            mediaLabRouteAAvailable=outcome.mediaLabRouteAAvailable,
            mediaLabRouteBAvailable=outcome.mediaLabRouteBAvailable,
            mediaLabAutoCapable=outcome.mediaLabAutoCapable,
            mediaLabAutoEnabled=outcome.mediaLabAutoEnabled,
            profilePublished=outcome.published,
            message=outcome.error?.let { "Dynamic profile was not published: ${it.message}" }) }
            .onFailure { mutable.value=mutable.value.copy(profilePublished=false,message="Dynamic profile was not published: ${it.message}") }
        return result.getOrNull()?.published == true
    }

    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""

    private fun keepAnalysisAlive(active: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, AnalysisKeepAliveService::class.java)
        if (active) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
    }
}
