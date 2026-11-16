package com.kARThik.vote.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kARThik.vote.utils.PasswordUtil

data class CandidateResult(val candidate: ElectionCandidateEntity, val votes: Int, val percent: Double, val rank: Int)

data class PartyResult(
    val key: String, val name: String, val abbr: String, val logoFile: String?,
    val color: Long, val votes: Int, val percent: Double, val candidateCount: Int, val emoji: String? = null
)

data class ElectionResults(
    val election: ElectionEntity,
    val candidates: List<CandidateResult>,
    val parties: List<PartyResult>,
    val totalVotes: Int,
    val cancelledCount: Int,
    val pendingCount: Int,
    val winners: List<CandidateResult>,
    val isFinal: Boolean
)

enum class RecordOutcome { RECORDED, DUPLICATE, REJECTED }

class MatamRepository(private val context: Context, val db: AppDatabase) {
    private val elections = db.electionDao()
    private val parties = db.partyDao()
    private val candidates = db.candidateDao()
    private val ecDao = db.electionCandidateDao()
    private val votes = db.voteDao()
    private val sessions = db.sessionDao()
    private val results = db.resultDao()
    private val audits = db.auditDao()
    val pairings = db.pairingDao()

    // ---------- Observables ----------
    fun observeElections() = elections.observeAllWithStats()
    fun observeElection(id: String) = elections.observe(id)
    fun observeParties() = parties.observeAll()
    fun observeCandidates() = candidates.observeAll()
    fun observeElectionCandidates(id: String) = ecDao.observe(id)
    fun observeVotes(id: String) = votes.observeForElection(id)
    fun observeAudit(id: String) = audits.observeForElection(id)
    fun observePending(id: String) = votes.observePendingCount(id)
    fun observeElectionCount() = elections.observeCount()
    fun observeCompletedCount() = elections.observeCompletedCount()
    fun observeCandidateCount() = candidates.observeCount()
    fun observeTotalVotes() = votes.observeTotalValid()
    fun observePairings() = pairings.observeAll()

    suspend fun getElection(id: String) = elections.get(id)
    suspend fun getElectionCandidates(id: String) = ecDao.get(id)

    // ---------- Election password ----------
    suspend fun hasPassword(id: String) = elections.get(id)?.passwordHash?.isNotEmpty() == true

    suspend fun verifyPassword(id: String, pw: String): Boolean {
        val e = elections.get(id) ?: return false
        if (e.passwordHash.isEmpty()) return false
        return withContext(Dispatchers.Default) { PasswordUtil.verify(pw, e.passwordSalt, e.passwordHash) }
    }

    private suspend fun storePassword(id: String, pw: String, action: String, requireEmpty: Boolean): Boolean {
        val salt = PasswordUtil.newSalt()
        val h = withContext(Dispatchers.Default) { PasswordUtil.hash(pw, salt) }
        return db.withTransaction {
            val e = elections.get(id) ?: return@withTransaction false
            if (requireEmpty && e.passwordHash.isNotEmpty()) return@withTransaction false
            elections.upsert(e.copy(passwordHash = h, passwordSalt = salt))
            audit(id, action, "Election password updated")
            true
        }
    }

    /** For elections created before passwords existed: sets the first password. */
    suspend fun setInitialPassword(id: String, pw: String) = storePassword(id, pw, "PASSWORD_SET", true)

    /** Changes the password; the current one must be correct (unless none has been set yet). */
    suspend fun changePassword(id: String, old: String, new: String): Boolean {
        val e = elections.get(id) ?: return false
        if (e.passwordHash.isNotEmpty() && !verifyPassword(id, old)) return false
        return storePassword(id, new, "PASSWORD_CHANGED", false)
    }

    // ---------- Audit ----------
    suspend fun audit(electionId: String, action: String, details: String = "") {
        audits.insert(ElectionAuditEntity(electionId = electionId, timestamp = System.currentTimeMillis(), action = action, details = details))
    }

    // ---------- Parties ----------
    suspend fun saveParty(p: PartyEntity, isNew: Boolean) {
        parties.upsert(p)
        audit("", if (isNew) "PARTY_ADDED" else "PARTY_EDITED", p.name)
    }

    data class PartyUsage(val candidateCount: Int, val electionNames: List<String>, val activeElectionUses: Int)

    suspend fun partyUsage(id: String) =
        PartyUsage(candidates.countForParty(id), ecDao.electionNamesUsingParty(id), ecDao.activeUsesOfParty(id))

    /** Deletes a party, moving its candidates to [reassignTo] (null = independent). Completed election snapshots keep their data. */
    suspend fun deleteParty(id: String, reassignTo: String?) = db.withTransaction {
        val name = parties.get(id)?.name ?: ""
        candidates.reassignParty(id, reassignTo)
        parties.delete(id)
        audit("", "PARTY_DELETED", "$name (candidates reassigned to ${reassignTo ?: "independent"})")
    }

    // ---------- Candidates ----------
    suspend fun saveCandidate(c: CandidateEntity, isNew: Boolean) {
        candidates.upsert(c)
        audit("", if (isNew) "CANDIDATE_ADDED" else "CANDIDATE_EDITED", c.name)
    }

    suspend fun deleteCandidate(id: String): Boolean {
        val used = ecDao.electionNamesUsingCandidate(id)
        // Election snapshots survive; the profile can be removed.
        val name = candidates.get(id)?.name ?: ""
        candidates.delete(id)
        audit("", "CANDIDATE_DELETED", "$name (kept in ${used.size} election record(s))")
        return true
    }

    suspend fun candidateElections(id: String) = ecDao.electionNamesUsingCandidate(id)

    private suspend fun snapshot(electionId: String, ids: List<String>): List<ElectionCandidateEntity> {
        val cmap = candidates.getMany(ids).associateBy { it.id }
        val pmap = parties.getAll().associateBy { it.id }
        return ids.mapIndexedNotNull { i, id ->
            val c = cmap[id] ?: return@mapIndexedNotNull null
            val p = c.partyId?.let { pmap[it] }
            ElectionCandidateEntity(
                electionId, c.id, c.name, c.photoFile, c.serial, p?.id,
                p?.name ?: "Independent", p?.let { it.abbreviation.ifBlank { com.kARThik.vote.utils.initialsOf(it.name) } } ?: "IND", p?.logoFile,
                p?.colorArgb ?: 0xFF757575, c.designation, c.description, c.manifesto, c.extraInfo, i,
                emoji = c.emoji, partyEmoji = p?.emoji
            )
        }
    }

    // ---------- Elections ----------
    suspend fun createElection(e: ElectionEntity, candidateIds: List<String>) = db.withTransaction {
        elections.upsert(e)
        ecDao.insertAll(snapshot(e.id, candidateIds))
        audit(e.id, "ELECTION_CREATED", "${e.name} with ${candidateIds.size} candidates")
    }

    suspend fun updateDraft(e: ElectionEntity, candidateIds: List<String>): Boolean = db.withTransaction {
        val cur = elections.get(e.id) ?: return@withTransaction false
        if (cur.status != ElectionStatus.DRAFT.name && cur.status != ElectionStatus.READY.name) return@withTransaction false
        elections.upsert(e.copy(status = cur.status, createdAt = cur.createdAt, passwordHash = cur.passwordHash, passwordSalt = cur.passwordSalt))
        ecDao.deleteForElection(e.id)
        ecDao.insertAll(snapshot(e.id, candidateIds))
        audit(e.id, "ELECTION_EDITED", "${e.name}; ${candidateIds.size} candidates")
        true
    }

    /** Nobody is voting right after the app starts, so any "ongoing" election is really paused. */
    suspend fun pauseOngoingOnStart() {
        val n = elections.pauseAllOngoing()
        if (n > 0) audit("", "AUTO_PAUSED", "$n ongoing election(s) paused when the app started")
    }

    /**
     * Edit after password confirmation. Name, schedule, instructions, ballot style and password can always change.
     * Candidates and voting mode only change before voting has started.
     */
    suspend fun updateElection(e: ElectionEntity, candidateIds: List<String>?, newPassword: String?): Boolean {
        val salt = newPassword?.let { PasswordUtil.newSalt() }
        val hash = if (newPassword != null && salt != null) withContext(Dispatchers.Default) { PasswordUtil.hash(newPassword, salt) } else null
        return db.withTransaction {
            val cur = elections.get(e.id) ?: return@withTransaction false
            val structural = cur.status == "DRAFT" || cur.status == "READY"
            var upd = cur.copy(
                name = e.name, description = e.description, category = e.category, dateMillis = e.dateMillis,
                startTime = e.startTime, endTime = e.endTime, location = e.location, instructions = e.instructions,
                ballotStyle = e.ballotStyle
            )
            if (structural) upd = upd.copy(votingMode = e.votingMode)
            if (hash != null && salt != null) upd = upd.copy(passwordHash = hash, passwordSalt = salt)
            elections.upsert(upd)
            if (structural && candidateIds != null) {
                ecDao.deleteForElection(e.id)
                ecDao.insertAll(snapshot(e.id, candidateIds))
            }
            audit(e.id, "ELECTION_EDITED", "Edited after password confirmation" + if (hash != null) "; password changed" else "")
            true
        }
    }

    suspend fun removeCandidateFromElection(electionId: String, candidateId: String): Boolean = db.withTransaction {
        val e = elections.get(electionId) ?: return@withTransaction false
        if (e.status != "DRAFT" && e.status != "READY") return@withTransaction false
        ecDao.remove(electionId, candidateId)
        audit(electionId, "CANDIDATE_REMOVED", candidateId)
        true
    }

    /** Re-reads profiles for candidates in a draft/ready election so edits are reflected before voting starts. */
    suspend fun refreshSnapshots(electionId: String) = db.withTransaction {
        val e = elections.get(electionId) ?: return@withTransaction
        if (e.status != "DRAFT" && e.status != "READY") return@withTransaction
        val ids = ecDao.get(electionId).map { it.candidateId }
        ecDao.deleteForElection(electionId)
        ecDao.insertAll(snapshot(electionId, ids))
    }

    suspend fun setStatus(id: String, status: ElectionStatus): String? = db.withTransaction {
        val e = elections.get(id) ?: return@withTransaction "Election not found"
        if (e.status == "COMPLETED") return@withTransaction "Election is already completed"
        val cands = ecDao.get(id)
        when (status) {
            ElectionStatus.READY -> {
                if (cands.size < 2) return@withTransaction "Add at least two candidates first"
                refreshSnapshots(id)
            }
            ElectionStatus.ONGOING -> {
                if (cands.size < 2) return@withTransaction "Add at least two candidates first"
                if (e.status == "DRAFT") refreshSnapshots(id)
            }
            ElectionStatus.DRAFT -> if (e.status != "READY") return@withTransaction "Only a ready election can return to draft"
            else -> {}
        }
        elections.upsert(e.copy(status = status.name))
        audit(id, "STATUS_CHANGED", "${e.status} -> ${status.name}")
        null
    }

    suspend fun deleteElection(id: String) = db.withTransaction {
        val name = elections.get(id)?.name ?: ""
        elections.delete(id)
        audits.deleteForElection(id)
        audit("", "ELECTION_DELETED", name)
    }

    suspend fun deleteHistory() = db.withTransaction {
        elections.deleteCompleted()
        audit("", "HISTORY_DELETED", "All completed elections removed by user")
    }

    suspend fun deleteResult(electionId: String) = db.withTransaction {
        // Un-finalise: result snapshot is removed and election returns to paused so it is clearly provisional.
        results.delete(electionId)
        elections.get(electionId)?.let { elections.upsert(it.copy(status = "PAUSED", finalizedAt = null)) }
        audit(electionId, "RESULT_DELETED", "Finalised result removed by user; election reopened as paused")
    }

    suspend fun clearAllData() {
        db.withTransaction {
            elections.deleteAll(); candidates.deleteAll(); parties.deleteAll()
            pairings.deleteAll(); audits.deleteAll()
        }
        com.kARThik.vote.utils.ImageStore.clearAll(context)
    }

    // ---------- Voting ----------
    suspend fun openSession(electionId: String, deviceId: String): String {
        val id = UUID.randomUUID().toString()
        sessions.upsert(VotingSessionEntity(id, electionId, System.currentTimeMillis(), null, "OPEN", deviceId))
        return id
    }

    suspend fun abandonOpenSessions(electionId: String) = sessions.abandonOpen(electionId, System.currentTimeMillis())

    /**
     * Atomically records a vote. Idempotent on [txId]: re-sending the same transaction returns DUPLICATE
     * and never creates a second row.
     */
    suspend fun recordVote(
        electionId: String, candidateId: String, sessionId: String?, txId: String,
        deviceId: String, timestamp: Long = System.currentTimeMillis(),
        status: VoteStatus = VoteStatus.VALID, requireOngoing: Boolean = true
    ): RecordOutcome = db.withTransaction {
        votes.get(txId)?.let { existing ->
            if (existing.status == VoteStatus.PENDING.name && status == VoteStatus.VALID) votes.setStatus(txId, status.name)
            return@withTransaction RecordOutcome.DUPLICATE
        }
        val e = elections.get(electionId) ?: return@withTransaction RecordOutcome.REJECTED
        if (e.status == "COMPLETED") return@withTransaction RecordOutcome.REJECTED
        if (requireOngoing && e.status != "ONGOING") return@withTransaction RecordOutcome.REJECTED
        if (ecDao.get(electionId).none { it.candidateId == candidateId }) return@withTransaction RecordOutcome.REJECTED
        val sid = sessionId ?: UUID.randomUUID().toString()
        if (sessions.get(sid) == null) sessions.upsert(VotingSessionEntity(sid, electionId, timestamp, null, "OPEN", deviceId))
        val inserted = votes.insertIgnore(VoteRecordEntity(txId, electionId, candidateId, sid, timestamp, status.name, deviceId))
        if (inserted == -1L) return@withTransaction RecordOutcome.DUPLICATE
        sessions.get(sid)?.let { sessions.upsert(it.copy(status = "COMPLETED", endedAt = System.currentTimeMillis())) }
        RecordOutcome.RECORDED
    }

    suspend fun recordVoteStatus(txId: String, status: VoteStatus) = votes.setStatus(txId, status.name)

    suspend fun markVoteConfirmed(txId: String) {
        val v = votes.get(txId) ?: return
        if (v.status == "PENDING") votes.setStatus(txId, "VALID")
    }

    suspend fun pendingVotes(electionId: String) = votes.getPending(electionId)

    suspend fun acceptPending(electionId: String): Int = db.withTransaction {
        val p = votes.getPending(electionId)
        p.forEach { votes.setStatus(it.transactionId, "VALID") }
        audit(electionId, "PENDING_ACCEPTED", "${p.size} pending vote(s) accepted locally by operator")
        p.size
    }

    /** Explicit, authorised correction: the record stays, its status changes, and the reason is audited. */
    suspend fun voidVote(txId: String, reason: String): Boolean = db.withTransaction {
        val v = votes.get(txId) ?: return@withTransaction false
        val e = elections.get(v.electionId) ?: return@withTransaction false
        if (e.status == "COMPLETED") return@withTransaction false
        votes.setStatus(txId, VoteStatus.CANCELLED.name)
        audit(v.electionId, "VOTE_CORRECTION", "tx ${txId.take(8)} ${v.status} -> CANCELLED. Reason: $reason")
        true
    }

    // ---------- Results ----------
    fun observeResults(electionId: String): Flow<ElectionResults?> = combine(
        elections.observe(electionId), ecDao.observe(electionId),
        votes.observeTally(electionId), votes.observeStatusCounts(electionId)
    ) { e, cands, tally, counts ->
        if (e == null) null else buildResults(e, cands, tally, counts)
    }

    suspend fun getResults(electionId: String): ElectionResults? {
        val e = elections.get(electionId) ?: return null
        return buildResults(e, ecDao.get(electionId), votes.getTally(electionId), votes.getStatusCounts(electionId))
    }

    private fun buildResults(
        e: ElectionEntity, cands: List<ElectionCandidateEntity>,
        tally: List<TallyRow>, counts: List<StatusCount>
    ): ElectionResults {
        val tmap = tally.associate { it.candidateId to it.votes }
        val total = tmap.values.sum()
        val sorted = cands.sortedWith(compareByDescending<ElectionCandidateEntity> { tmap[it.candidateId] ?: 0 }.thenBy { it.name })
        var rank = 0
        var prev = -1
        val list = sorted.mapIndexed { i, c ->
            val v = tmap[c.candidateId] ?: 0
            if (v != prev) { rank = i + 1; prev = v }
            CandidateResult(c, v, if (total == 0) 0.0 else v * 100.0 / total, rank)
        }
        val top = list.firstOrNull()?.votes ?: 0
        val winners = if (top > 0) list.filter { it.votes == top } else emptyList()
        val partyList = list.groupBy { it.candidate.partyId ?: "independent" }.map { (k, items) ->
            val first = items.first().candidate
            val v = items.sumOf { it.votes }
            PartyResult(
                k, first.partyName.ifBlank { "Independent" }, first.partyAbbr, first.partyLogoFile, first.partyColor,
                v, if (total == 0) 0.0 else v * 100.0 / total, items.size, first.partyEmoji
            )
        }.sortedByDescending { it.votes }
        fun c(s: String) = counts.firstOrNull { it.status == s }?.count ?: 0
        return ElectionResults(
            e, list, partyList, total, c("CANCELLED") + c("INVALID"), c("PENDING"),
            winners, e.status == "COMPLETED"
        )
    }

    /** Finalises an election: freezes a result snapshot. Refuses while unconfirmed votes exist. */
    suspend fun finalizeElection(id: String): String? = db.withTransaction {
        val e = elections.get(id) ?: return@withTransaction "Election not found"
        if (e.status == "COMPLETED") return@withTransaction "Already completed"
        if (e.status == "DRAFT" || e.status == "READY") return@withTransaction "Start the election before completing it"
        val r = getResults(id) ?: return@withTransaction "Election not found"
        if (r.pendingCount > 0) return@withTransaction "${r.pendingCount} vote(s) are awaiting confirmation. Reconnect to sync them, or accept them locally first."
        val snap = JSONArray()
        r.candidates.forEach {
            snap.put(JSONObject().put("candidateId", it.candidate.candidateId).put("name", it.candidate.name)
                .put("party", it.candidate.partyName).put("votes", it.votes).put("rank", it.rank))
        }
        val now = System.currentTimeMillis()
        sessions.abandonOpen(id, now)
        elections.upsert(e.copy(status = "COMPLETED", finalizedAt = now))
        results.upsert(ElectionResultEntity(id, now, r.totalVotes, r.cancelledCount, r.winners.joinToString(", ") { it.candidate.name }, snap.toString()))
        audit(id, "RESULT_FINALISED", "Total ${r.totalVotes} votes; winner: ${r.winners.joinToString { it.candidate.name }.ifBlank { "none" }}")
        null
    }

    fun observeResultSnapshot(id: String) = results.observe(id)
    suspend fun auditFor(id: String) = audits.getForElection(id)
    suspend fun votesFor(id: String) = votes.getForElection(id)
    suspend fun sessionsFor(id: String) = sessions.getForElection(id)
    suspend fun resultSnapshot(id: String) = results.get(id)
    suspend fun allParties() = parties.getAll()
    suspend fun allCandidates() = candidates.getAll()
    suspend fun allElections() = elections.getAll()
    suspend fun party(id: String) = parties.get(id)

    // Restore helpers used by BackupManager
    suspend fun restoreElection(
        e: ElectionEntity, cands: List<ElectionCandidateEntity>, voteList: List<VoteRecordEntity>,
        sessionList: List<VotingSessionEntity>, result: ElectionResultEntity?, auditList: List<ElectionAuditEntity>
    ) = db.withTransaction {
        elections.delete(e.id)
        audits.deleteForElection(e.id)
        elections.upsert(e)
        ecDao.insertAll(cands)
        sessionList.forEach { sessions.upsert(it) }
        voteList.forEach { votes.insertIgnore(it) }
        result?.let { results.upsert(it) }
        auditList.forEach { audits.insert(it.copy(id = 0)) }
        audit(e.id, "RESTORED", "Election restored from backup")
    }

    suspend fun createElectionRaw(e: ElectionEntity, cands: List<ElectionCandidateEntity>) = db.withTransaction {
        elections.upsert(e)
        ecDao.insertAll(cands)
        audit(e.id, "SYNCED_FROM_CONTROLLER", "Election configuration received over Bluetooth")
    }

    /** Replaces candidate snapshots and election fields without removing votes already held locally. */
    suspend fun replaceConfig(e: ElectionEntity, cands: List<ElectionCandidateEntity>) = db.withTransaction {
        elections.upsert(e)
        ecDao.deleteForElection(e.id)
        ecDao.insertAll(cands)
        audit(e.id, "CONFIG_SYNCED", "Configuration updated from controller")
    }

    /** Stable fingerprint of what voters see; both phones must compute the same value. */
    suspend fun configHash(electionId: String): String {
        val e = elections.get(electionId) ?: return ""
        val sb = StringBuilder(e.id).append('|').append(e.name)
        ecDao.get(electionId).sortedBy { it.candidateId }.forEach {
            sb.append('|').append(it.candidateId).append(':').append(it.name).append(':').append(it.serial).append(':').append(it.partyName)
        }
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    /** Terminal phone follows the controller's election status; never moves a completed election backwards. */
    suspend fun mirrorStatus(id: String, status: String) = db.withTransaction {
        val e = elections.get(id) ?: return@withTransaction
        if (e.status == "COMPLETED" || e.status == status) return@withTransaction
        elections.upsert(e.copy(status = status, finalizedAt = if (status == "COMPLETED") System.currentTimeMillis() else e.finalizedAt))
        audit(id, "STATUS_MIRRORED", "${e.status} -> $status (from controller)")
    }

    suspend fun upsertPartyRaw(p: PartyEntity) = parties.upsert(p)
    suspend fun insertCandidateIfAbsent(c: CandidateEntity) = candidates.insertIfAbsent(c)
    suspend fun upsertCandidateRaw(c: CandidateEntity) = candidates.upsert(c)
    suspend fun savePairing(p: DevicePairingEntity) = pairings.upsert(p)
}
