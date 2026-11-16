package com.kARThik.vote.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kARThik.vote.AppContainer
import com.kARThik.vote.MatamApp
import com.kARThik.vote.data.*
import com.kARThik.vote.settings.AppSettings
import com.kARThik.vote.settings.ThemeMode
import com.kARThik.vote.voting.Buzzer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

@Composable
fun container(): AppContainer = (LocalContext.current.applicationContext as MatamApp).container

@Composable
inline fun <reified T : ViewModel> appVm(key: String? = null, crossinline create: (AppContainer) -> T): T {
    val c = container()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

fun <T> Flow<T>.stateInVm(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

// ---------------- Home ----------------
class HomeVm(private val c: AppContainer) : ViewModel() {
    val elections = c.repo.observeElections().stateInVm(this, emptyList())
    val totalElections = c.repo.observeElectionCount().stateInVm(this, 0)
    val completed = c.repo.observeCompletedCount().stateInVm(this, 0)
    val candidates = c.repo.observeCandidateCount().stateInVm(this, 0)
    val votes = c.repo.observeTotalVotes().stateInVm(this, 0)
}

// ---------------- Elections list / history ----------------
enum class DateFilter(val label: String) { LAST_7("Last 7 days"), LAST_30("Last 30 days"), THIS_YEAR("This year") }
enum class ElectionSort(val label: String) { NEWEST("Newest first"), OLDEST("Oldest first"), NAME("Name"), VOTES("Most votes") }

class ElectionsVm(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val status = MutableStateFlow<ElectionStatus?>(null)
    val category = MutableStateFlow<String?>(null)
    val date = MutableStateFlow<DateFilter?>(null)
    val sort = MutableStateFlow(ElectionSort.NEWEST)

    val all = c.repo.observeElections().stateInVm(this, emptyList())

    val filtered: StateFlow<List<ElectionWithStats>> = combine(all, query, status, category, date, sort) { a ->
        @Suppress("UNCHECKED_CAST")
        val list = a[0] as List<ElectionWithStats>
        val q = (a[1] as String).trim().lowercase()
        val st = a[2] as ElectionStatus?
        val cat = a[3] as String?
        val df = a[4] as DateFilter?
        val so = a[5] as ElectionSort
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_YEAR, 1); set(java.util.Calendar.HOUR_OF_DAY, 0) }
        list.filter { e ->
            (q.isEmpty() || e.name.lowercase().contains(q) || e.location.lowercase().contains(q) || e.category.lowercase().contains(q) || e.description.lowercase().contains(q)) &&
                (st == null || e.status == st.name) && (cat == null || e.category == cat) &&
                when (df) {
                    null -> true
                    DateFilter.LAST_7 -> e.dateMillis >= now - 7 * day
                    DateFilter.LAST_30 -> e.dateMillis >= now - 30 * day
                    DateFilter.THIS_YEAR -> e.dateMillis >= cal.timeInMillis
                }
        }.let {
            when (so) {
                ElectionSort.NEWEST -> it.sortedByDescending { e -> e.dateMillis }
                ElectionSort.OLDEST -> it.sortedBy { e -> e.dateMillis }
                ElectionSort.NAME -> it.sortedBy { e -> e.name.lowercase() }
                ElectionSort.VOTES -> it.sortedByDescending { e -> e.voteCount }
            }
        }
    }.stateInVm(this, emptyList())

    fun delete(id: String) = viewModelScope.launch { c.repo.deleteElection(id) }
}

// ---------------- Create / edit election ----------------
data class ElectionForm(
    val name: String = "", val description: String = "", val category: String = ELECTION_CATEGORIES[0],
    val date: Long = System.currentTimeMillis(), val start: String = "", val end: String = "", val location: String = "",
    val mode: VotingMode = VotingMode.SINGLE_PHONE, val instructions: String = "",
    val candidateIds: Set<String> = emptySet(),
    val password: String = "", val confirm: String = "",
    val ballotStyle: BallotStyle = BallotStyle.MODERN
)

class ElectionEditVm(private val c: AppContainer, val editId: String?) : ViewModel() {
    val form = MutableStateFlow(ElectionForm())
    val loaded = MutableStateFlow(editId == null)
    val structural = MutableStateFlow(true)   // candidates/voting mode editable only before voting starts
    val error = MutableStateFlow<String?>(null)
    val parties = c.repo.observeParties().stateInVm(this, emptyList())
    val candidates = c.repo.observeCandidates().stateInVm(this, emptyList())
    val candidateSearch = MutableStateFlow("")

    init {
        viewModelScope.launch {
            if (editId != null) {
                val e = c.repo.getElection(editId)
                if (e != null) {
                    structural.value = e.status == "DRAFT" || e.status == "READY"
                    val ids = c.repo.getElectionCandidates(editId).map { it.candidateId }.toSet()
                    form.value = ElectionForm(e.name, e.description, e.category, e.dateMillis, e.startTime, e.endTime, e.location,
                        VotingMode.valueOf(e.votingMode), e.instructions, ids, ballotStyle = BallotStyle.from(e.ballotStyle))
                }
            } else {
                val mode = c.settings.flow.first().defaultMode
                form.update { it.copy(mode = runCatching { VotingMode.valueOf(mode) }.getOrDefault(VotingMode.SINGLE_PHONE)) }
            }
            loaded.value = true
        }
    }

    fun update(f: (ElectionForm) -> ElectionForm) = form.update(f)

    fun toggleCandidate(id: String) = form.update { it.copy(candidateIds = if (id in it.candidateIds) it.candidateIds - id else it.candidateIds + id) }

    /** Selecting a party selects (or clears) all of its registered candidates. */
    fun toggleParty(partyId: String?) {
        val cands = candidates.value.filter { it.partyId == partyId }.map { it.id }.toSet()
        form.update { f -> if (f.candidateIds.containsAll(cands) && cands.isNotEmpty()) f.copy(candidateIds = f.candidateIds - cands) else f.copy(candidateIds = f.candidateIds + cands) }
    }

    fun validate(): String? {
        val f = form.value
        if (f.name.isBlank()) return "Election name is required"
        if (structural.value && f.candidateIds.size < 2) return "Select at least two candidates"
        if (editId == null || f.password.isNotEmpty() || f.confirm.isNotEmpty()) {
            if (f.password.length < 4) return "Election password must be at least 4 characters"
            if (f.password != f.confirm) return "Passwords do not match"
        }
        return null
    }

    fun save(onDone: (String) -> Unit) = viewModelScope.launch {
        validate()?.let { error.value = it; return@launch }
        val f = form.value
        val id = editId ?: UUID.randomUUID().toString()
        val salt = if (editId == null) com.kARThik.vote.utils.PasswordUtil.newSalt() else ""
        val hash = if (editId == null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.kARThik.vote.utils.PasswordUtil.hash(f.password, salt) } else ""
        val e = ElectionEntity(id, f.name.trim(), f.description.trim(), f.category, f.date, f.start, f.end, f.location.trim(),
            f.mode.name, f.instructions.trim(), ElectionStatus.DRAFT.name, System.currentTimeMillis(),
            passwordHash = hash, passwordSalt = salt, ballotStyle = f.ballotStyle.name)
        val ordered = candidates.value.map { it.id }.filter { it in f.candidateIds }
        if (editId == null) c.repo.createElection(e, ordered)
        else if (!c.repo.updateElection(e, if (structural.value) ordered else null, f.password.ifEmpty { null })) { error.value = "Could not save changes"; return@launch }
        onDone(id)
    }
}

// ---------------- Election detail ----------------
class ElectionDetailVm(private val c: AppContainer, val id: String) : ViewModel() {
    val election = c.repo.observeElection(id).stateInVm(this, null)
    val candidates = c.repo.observeElectionCandidates(id).stateInVm(this, emptyList())
    val results = c.repo.observeResults(id).stateInVm(this, null)
    val votes = c.repo.observeVotes(id).stateInVm(this, emptyList())
    val audit = c.repo.observeAudit(id).stateInVm(this, emptyList())
    val pending = c.repo.observePending(id).stateInVm(this, 0)
    val message = MutableStateFlow<String?>(null)

    fun setStatus(s: ElectionStatus) = viewModelScope.launch { c.repo.setStatus(id, s)?.let { message.value = it } }
    fun finalize() = viewModelScope.launch { c.repo.finalizeElection(id)?.let { message.value = it } ?: run { message.value = "Result finalised" } }
    fun acceptPending() = viewModelScope.launch { val n = c.repo.acceptPending(id); message.value = "$n pending vote(s) accepted" }
    fun voidVote(tx: String, reason: String) = viewModelScope.launch {
        message.value = if (c.repo.voidVote(tx, reason)) "Vote cancelled and logged" else "Votes in a completed election cannot be changed"
    }
    fun removeCandidate(cid: String) = viewModelScope.launch {
        if (!c.repo.removeCandidateFromElection(id, cid)) message.value = "Candidates can only be removed before voting starts"
    }
    fun deleteResult() = viewModelScope.launch { c.repo.deleteResult(id); message.value = "Finalised result removed; election reopened as paused" }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.repo.deleteElection(id); onDone() }
    fun clearMessage() { message.value = null }
}

// ---------------- Parties ----------------
class PartiesVm(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val all = c.repo.observeParties().stateInVm(this, emptyList())
    val filtered = combine(all, query) { l, q -> l.filter { q.isBlank() || it.name.contains(q, true) || it.abbreviation.contains(q, true) } }.stateInVm(this, emptyList())
    val usage = MutableStateFlow<Pair<PartyEntity, MatamRepository.PartyUsage>?>(null)

    fun requestDelete(p: PartyEntity, onFree: () -> Unit) = viewModelScope.launch {
        val u = c.repo.partyUsage(p.id)
        if (u.candidateCount == 0 && u.electionNames.isEmpty()) onFree() else usage.value = p to u
    }
    fun delete(id: String, reassignTo: String?) = viewModelScope.launch { c.repo.deleteParty(id, reassignTo); usage.value = null }
}

class PartyEditVm(private val c: AppContainer, val partyId: String?) : ViewModel() {
    val party = MutableStateFlow(PartyEntity(UUID.randomUUID().toString(), ""))
    val loaded = MutableStateFlow(partyId == null)
    init { if (partyId != null) viewModelScope.launch { c.repo.party(partyId)?.let { party.value = it }; loaded.value = true } }
    fun update(f: (PartyEntity) -> PartyEntity) = party.update(f)
    fun save(onDone: () -> Unit) = viewModelScope.launch {
        if (party.value.name.isBlank()) return@launch
        c.repo.saveParty(party.value.let { it.copy(name = it.name.trim(), abbreviation = it.abbreviation.trim().ifBlank { com.kARThik.vote.utils.initialsOf(it.name) }) }, partyId == null); onDone()
    }
}

// ---------------- Candidates ----------------
class CandidatesVm(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val partyFilter = MutableStateFlow<String?>("__all")
    val all = c.repo.observeCandidates().stateInVm(this, emptyList())
    val parties = c.repo.observeParties().stateInVm(this, emptyList())
    val filtered = combine(all, query, partyFilter) { l, q, pf ->
        l.filter { (q.isBlank() || it.name.contains(q, true) || it.serial.contains(q, true) || it.designation.contains(q, true)) &&
            (pf == "__all" || (pf == null && it.partyId == null) || it.partyId == pf) }
    }.stateInVm(this, emptyList())
    val usedIn = MutableStateFlow<Pair<CandidateEntity, List<String>>?>(null)
    fun requestDelete(cd: CandidateEntity) = viewModelScope.launch { usedIn.value = cd to c.repo.candidateElections(cd.id) }
    fun delete(id: String) = viewModelScope.launch { c.repo.deleteCandidate(id); usedIn.value = null }
}

class CandidateEditVm(private val c: AppContainer, val candidateId: String?) : ViewModel() {
    val candidate = MutableStateFlow(CandidateEntity(UUID.randomUUID().toString(), ""))
    val loaded = MutableStateFlow(candidateId == null)
    val parties = c.repo.observeParties().stateInVm(this, emptyList())
    init { if (candidateId != null) viewModelScope.launch { c.db.candidateDao().get(candidateId)?.let { candidate.value = it }; loaded.value = true } }
    fun update(f: (CandidateEntity) -> CandidateEntity) = candidate.update(f)
    fun save(onDone: () -> Unit) = viewModelScope.launch {
        if (candidate.value.name.isBlank()) return@launch
        c.repo.saveCandidate(candidate.value.copy(name = candidate.value.name.trim()), candidateId == null); onDone()
    }
}

// ---------------- Results ----------------
class ResultsVm(private val c: AppContainer, id: String) : ViewModel() {
    val results = c.repo.observeResults(id).stateInVm(this, null)
    val settings = c.settings.flow.stateInVm(this, AppSettings())
    val layout = MutableStateFlow(-1)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    init { viewModelScope.launch { layout.value = c.settings.flow.first().defaultResultLayout } }
    val exporter get() = c.exporter
    val electionId = id
}

// ---------------- Single-phone voting ----------------
enum class VotePhase { READY, BALLOT, CONFIRM, SUCCESS, PAUSED, UNAVAILABLE }

class VotingVm(private val c: AppContainer, private val id: String, private val appContext: Context) : ViewModel() {
    val election = c.repo.observeElection(id).stateInVm(this, null)
    val candidates = c.repo.observeElectionCandidates(id).stateInVm(this, emptyList())
    val phase = MutableStateFlow(VotePhase.READY)
    val selected = MutableStateFlow<ElectionCandidateEntity?>(null)
    val error = MutableStateFlow<String?>(null)
    private var sessionId: String? = null
    private var deviceId = ""

    init {
        viewModelScope.launch {
            deviceId = c.settings.ensureDeviceId()
            val e = c.repo.getElection(id)
            if (e == null || e.status == "COMPLETED") phase.value = VotePhase.UNAVAILABLE
            else if (e.status == "READY" || e.status == "PAUSED") {
                // operator explicitly starts/resumes via the session controls
                // Entering from the election page (after the password check) starts or continues voting.
                phase.value = VotePhase.READY
                c.repo.setStatus(id, ElectionStatus.ONGOING)?.let { error.value = it; phase.value = VotePhase.UNAVAILABLE }
            } else if (e.status == "DRAFT") {
                c.repo.setStatus(id, ElectionStatus.READY)?.let { error.value = it; phase.value = VotePhase.UNAVAILABLE; return@launch }
                c.repo.setStatus(id, ElectionStatus.ONGOING)
            }
        }
    }

    fun beginSession() = viewModelScope.launch {
        sessionId = c.repo.openSession(id, deviceId); selected.value = null; phase.value = VotePhase.BALLOT
    }
    fun select(cd: ElectionCandidateEntity) { selected.value = cd; phase.value = VotePhase.CONFIRM }
    fun back() { selected.value = null; phase.value = VotePhase.BALLOT }
    fun cancelSession() = viewModelScope.launch { c.repo.abandonOpenSessions(id); sessionId = null; selected.value = null; phase.value = VotePhase.READY }

    fun confirm() = viewModelScope.launch {
        val cd = selected.value ?: return@launch
        val out = c.repo.recordVote(id, cd.candidateId, sessionId, UUID.randomUUID().toString(), deviceId)
        if (out == RecordOutcome.RECORDED) {
            val s = c.settings.flow.first()
            if (s.buzzerEnabled) Buzzer.play(s.buzzerSound, s.buzzerVolume)
            if (s.vibration) Buzzer.vibrate(appContext)
            phase.value = VotePhase.SUCCESS
            kotlinx.coroutines.delay(2200)
            selected.value = null; sessionId = null
            phase.value = VotePhase.READY
        } else {
            error.value = "The vote could not be recorded. Please ask the operator."
            selected.value = null; phase.value = VotePhase.READY
        }
    }

    /** Leaving the voting screen pauses the election; continuing later needs the password again. */
    fun leave(onDone: () -> Unit) = viewModelScope.launch {
        if (c.repo.getElection(id)?.status == "ONGOING") c.repo.setStatus(id, ElectionStatus.PAUSED)
        onDone()
    }
    fun pause() = viewModelScope.launch { if (c.repo.setStatus(id, ElectionStatus.PAUSED) == null) phase.value = VotePhase.PAUSED }
    fun resume() = viewModelScope.launch { if (c.repo.setStatus(id, ElectionStatus.ONGOING) == null) phase.value = VotePhase.READY }
    fun clearError() { error.value = null }
}

// ---------------- Bluetooth screens ----------------
class BtVm(val c: AppContainer) : ViewModel() {
    val bt = c.bt
    val ui = bt.ui
    val log = bt.log
    val elections = c.repo.observeElections().stateInVm(this, emptyList())
    val pairings = c.repo.observePairings().stateInVm(this, emptyList())
    val settings = c.settings.flow.stateInVm(this, AppSettings())
    fun electionCandidates(id: String) = c.repo.observeElectionCandidates(id)
}

// ---------------- Settings ----------------
class SettingsVm(private val c: AppContainer) : ViewModel() {
    val elections = c.repo.observeElections().stateInVm(this, emptyList())
    val settings = c.settings.flow.stateInVm(this, AppSettings())
    val message = MutableStateFlow<String?>(null)
    val s get() = c.settings
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    fun preview() = viewModelScope.launch { val s = c.settings.flow.first(); Buzzer.play(s.buzzerSound, s.buzzerVolume) }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { c.settings.setTheme(t) }

    fun backupTo(write: (ByteArray) -> Unit) = viewModelScope.launch {
        runCatching { write(c.exporter.fullBackupJson().toString().toByteArray()) }
            .onSuccess { message.value = "Backup saved" }.onFailure { message.value = "Backup failed: ${it.message}" }
    }
    fun restore(text: String) = viewModelScope.launch {
        runCatching { c.exporter.restoreFullBackup(JSONObject(text)) }
            .onSuccess { message.value = "Restored $it election(s)" }.onFailure { message.value = "Restore failed: ${it.message}" }
    }
    fun deleteHistory() = viewModelScope.launch { c.repo.deleteHistory(); message.value = "Election history deleted" }
    fun clearAll() = viewModelScope.launch { c.repo.clearAllData(); message.value = "All application data cleared" }
}
