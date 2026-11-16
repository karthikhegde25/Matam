package com.kARThik.vote.data

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.kARThik.vote.utils.ImageStore
import com.kARThik.vote.utils.fmtDate
import com.kARThik.vote.utils.fmtDateTime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** JSON backup/restore, CSV and PDF export. */
class ExportManager(private val context: Context, private val repo: MatamRepository) {

    // ---------- JSON ----------
    private fun ElectionEntity.toJson() = JSONObject().put("id", id).put("name", name).put("description", description)
        .put("category", category).put("dateMillis", dateMillis).put("startTime", startTime).put("endTime", endTime)
        .put("location", location).put("votingMode", votingMode).put("instructions", instructions)
        .put("status", status).put("createdAt", createdAt).put("finalizedAt", finalizedAt ?: JSONObject.NULL)
        .put("passwordHash", passwordHash).put("passwordSalt", passwordSalt).put("ballotStyle", ballotStyle)

    private fun JSONObject.toElection() = ElectionEntity(
        getString("id"), getString("name"), optString("description"), optString("category"),
        getLong("dateMillis"), optString("startTime"), optString("endTime"), optString("location"),
        optString("votingMode", "SINGLE_PHONE"), optString("instructions"), optString("status", "DRAFT"),
        getLong("createdAt"), if (isNull("finalizedAt")) null else getLong("finalizedAt"),
        optString("passwordHash"), optString("passwordSalt"), optString("ballotStyle", "MODERN")
    )

    private fun ElectionCandidateEntity.toJson() = JSONObject().put("electionId", electionId).put("candidateId", candidateId)
        .put("name", name).put("photoFile", photoFile ?: JSONObject.NULL).put("serial", serial)
        .put("partyId", partyId ?: JSONObject.NULL).put("partyName", partyName).put("partyAbbr", partyAbbr)
        .put("partyLogoFile", partyLogoFile ?: JSONObject.NULL).put("partyColor", partyColor)
        .put("designation", designation).put("description", description).put("manifesto", manifesto)
        .put("extraInfo", extraInfo).put("sortOrder", sortOrder)
        .put("emoji", emoji ?: JSONObject.NULL).put("partyEmoji", partyEmoji ?: JSONObject.NULL)

    private fun JSONObject.str(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)

    private fun JSONObject.toEc() = ElectionCandidateEntity(
        getString("electionId"), getString("candidateId"), getString("name"), str("photoFile"), optString("serial"),
        str("partyId"), optString("partyName"), optString("partyAbbr"), str("partyLogoFile"),
        optLong("partyColor", 0xFF757575), optString("designation"), optString("description"),
        optString("manifesto"), optString("extraInfo"), optInt("sortOrder"), str("emoji"), str("partyEmoji")
    )

    private fun PartyEntity.toJson() = JSONObject().put("id", id).put("name", name).put("abbreviation", abbreviation)
        .put("logoFile", logoFile ?: JSONObject.NULL).put("bannerFile", bannerFile ?: JSONObject.NULL)
        .put("description", description).put("colorArgb", colorArgb).put("leader", leader)
        .put("emoji", emoji ?: JSONObject.NULL)

    private fun JSONObject.toParty() = PartyEntity(
        getString("id"), getString("name"), optString("abbreviation"), str("logoFile"), str("bannerFile"),
        optString("description"), optLong("colorArgb", 0xFF1E88E5), optString("leader"), str("emoji")
    )

    private fun CandidateEntity.toJson() = JSONObject().put("id", id).put("name", name)
        .put("photoFile", photoFile ?: JSONObject.NULL).put("serial", serial).put("partyId", partyId ?: JSONObject.NULL)
        .put("description", description).put("designation", designation).put("manifesto", manifesto).put("extraInfo", extraInfo)
        .put("emoji", emoji ?: JSONObject.NULL)

    private fun JSONObject.toCandidate() = CandidateEntity(
        getString("id"), getString("name"), str("photoFile"), optString("serial"), str("partyId"),
        optString("description"), optString("designation"), optString("manifesto"), optString("extraInfo"), str("emoji")
    )

    private fun VoteRecordEntity.toJson() = JSONObject().put("transactionId", transactionId).put("electionId", electionId)
        .put("candidateId", candidateId).put("sessionId", sessionId).put("timestamp", timestamp)
        .put("status", status).put("deviceId", deviceId)

    private fun JSONObject.toVote() = VoteRecordEntity(
        getString("transactionId"), getString("electionId"), getString("candidateId"), getString("sessionId"),
        getLong("timestamp"), getString("status"), optString("deviceId")
    )

    private fun VotingSessionEntity.toJson() = JSONObject().put("id", id).put("electionId", electionId)
        .put("startedAt", startedAt).put("endedAt", endedAt ?: JSONObject.NULL).put("status", status).put("deviceId", deviceId)

    private fun JSONObject.toSession() = VotingSessionEntity(
        getString("id"), getString("electionId"), getLong("startedAt"),
        if (isNull("endedAt")) null else getLong("endedAt"), optString("status", "COMPLETED"), optString("deviceId")
    )

    private fun ElectionAuditEntity.toJson() = JSONObject().put("electionId", electionId).put("timestamp", timestamp)
        .put("action", action).put("details", details)

    private fun JSONObject.toAudit() = ElectionAuditEntity(0, getString("electionId"), getLong("timestamp"), getString("action"), optString("details"))

    private fun ElectionResultEntity.toJson() = JSONObject().put("electionId", electionId).put("finalizedAt", finalizedAt)
        .put("totalVotes", totalVotes).put("cancelledCount", cancelledCount).put("winnerNames", winnerNames).put("snapshotJson", snapshotJson)

    private fun JSONObject.toResult() = ElectionResultEntity(
        getString("electionId"), getLong("finalizedAt"), getInt("totalVotes"), getInt("cancelledCount"),
        optString("winnerNames"), optString("snapshotJson", "[]")
    )

    private fun imagesFor(names: Set<String?>, compact: Boolean = false): JSONObject {
        val o = JSONObject()
        names.filterNotNull().forEach { n ->
            (if (compact) ImageStore.toCompactBase64(context, n) else ImageStore.toBase64(context, n))?.let { o.put(n, it) }
        }
        return o
    }

    /** Full election export. [forSync] omits votes/audit (used when pushing configuration to the terminal phone). */
    suspend fun electionJson(id: String, forSync: Boolean = false, withImages: Boolean = true): JSONObject? {
        val e = repo.getElection(id) ?: return null
        val cands = repo.getElectionCandidates(id)
        val o = JSONObject().put("format", "matam-election").put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("election", e.toJson())
            .put("candidates", JSONArray(cands.map { it.toJson() }))
        if (!forSync) {
            o.put("votes", JSONArray(repo.votesFor(id).map { it.toJson() }))
            o.put("sessions", JSONArray(repo.sessionsFor(id).map { it.toJson() }))
            o.put("audit", JSONArray(repo.auditFor(id).map { it.toJson() }))
            repo.resultSnapshot(id)?.let { o.put("result", it.toJson()) }
        }
        if (withImages) o.put("images", imagesFor(cands.flatMap { listOf(it.photoFile, it.partyLogoFile) }.toSet(), compact = forSync))
        return o
    }

    /** Import election configuration without touching local votes (used by the Bluetooth terminal). */
    suspend fun importConfig(json: JSONObject) {
        saveImages(json)
        val e = json.getJSONObject("election").toElection()
        val cands = (0 until json.getJSONArray("candidates").length()).map { json.getJSONArray("candidates").getJSONObject(it).toEc() }
        val existing = repo.getElection(e.id)
        if (existing == null) {
            repo.createElectionRaw(e, cands)
        } else {
            repo.replaceConfig(e.copy(createdAt = existing.createdAt), cands)
        }
    }

    private fun saveImages(json: JSONObject) {
        val imgs = json.optJSONObject("images") ?: return
        imgs.keys().forEach { k -> ImageStore.saveBase64(context, k, imgs.getString(k)) }
    }

    /** Restore a full single-election backup (replaces an election with the same id). */
    suspend fun restoreElectionJson(json: JSONObject) {
        require(json.optString("format") == "matam-election") { "Not a Matam election backup" }
        saveImages(json)
        val e = json.getJSONObject("election").toElection()
        fun <T> arr(k: String, f: (JSONObject) -> T): List<T> {
            val a = json.optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).map { f(a.getJSONObject(it)) }
        }
        repo.restoreElection(
            e, arr("candidates") { it.toEc() }, arr("votes") { it.toVote() }, arr("sessions") { it.toSession() },
            json.optJSONObject("result")?.toResult(), arr("audit") { it.toAudit() }
        )
    }

    /** Whole-app backup: parties, candidates and every election with votes and images. */
    suspend fun fullBackupJson(): JSONObject {
        val o = JSONObject().put("format", "matam-backup").put("version", 1).put("exportedAt", System.currentTimeMillis())
        val parties = repo.allParties()
        val cands = repo.allCandidates()
        o.put("parties", JSONArray(parties.map { it.toJson() }))
        o.put("candidates", JSONArray(cands.map { it.toJson() }))
        val imageNames = mutableSetOf<String?>()
        parties.forEach { imageNames += it.logoFile; imageNames += it.bannerFile }
        cands.forEach { imageNames += it.photoFile }
        val els = JSONArray()
        repo.allElections().forEach { e ->
            electionJson(e.id, forSync = false, withImages = false)?.let { els.put(it) }
            repo.getElectionCandidates(e.id).forEach { imageNames += it.photoFile; imageNames += it.partyLogoFile }
        }
        o.put("elections", els)
        o.put("images", imagesFor(imageNames))
        return o
    }

    suspend fun restoreFullBackup(json: JSONObject): Int {
        when (json.optString("format")) {
            "matam-election" -> { restoreElectionJson(json); return 1 }
            "matam-backup" -> {}
            else -> throw IllegalArgumentException("Not a Matam backup file")
        }
        saveImages(json)
        json.optJSONArray("parties")?.let { a -> for (i in 0 until a.length()) repo.upsertPartyRaw(a.getJSONObject(i).toParty()) }
        json.optJSONArray("candidates")?.let { a -> for (i in 0 until a.length()) repo.upsertCandidateRaw(a.getJSONObject(i).toCandidate()) }
        val els = json.optJSONArray("elections") ?: return 0
        for (i in 0 until els.length()) restoreElectionJson(els.getJSONObject(i))
        return els.length()
    }

    // ---------- CSV ----------
    private fun csv(v: String) = "\"" + v.replace("\"", "\"\"") + "\""

    suspend fun csvFor(id: String): String? {
        val r = repo.getResults(id) ?: return null
        val sb = StringBuilder()
        sb.append("Election,").append(csv(r.election.name)).append('\n')
        sb.append("Date,").append(csv(fmtDate(r.election.dateMillis))).append('\n')
        sb.append("Category,").append(csv(r.election.category)).append('\n')
        sb.append("Location,").append(csv(r.election.location)).append('\n')
        sb.append("Status,").append(if (r.isFinal) "FINAL" else "PROVISIONAL").append('\n')
        sb.append("Total valid votes,").append(r.totalVotes).append('\n')
        sb.append("Cancelled/invalid,").append(r.cancelledCount).append('\n')
        sb.append("Unconfirmed,").append(r.pendingCount).append("\n\n")
        sb.append("Rank,Serial,Candidate,Party,Abbreviation,Votes,Percentage\n")
        r.candidates.forEach {
            sb.append(it.rank).append(',').append(csv(it.candidate.serial)).append(',').append(csv(it.candidate.name)).append(',')
                .append(csv(it.candidate.partyName)).append(',').append(csv(it.candidate.partyAbbr)).append(',')
                .append(it.votes).append(',').append(String.format("%.2f", it.percent)).append('\n')
        }
        sb.append("\nParty,Candidates,Votes,Percentage\n")
        r.parties.forEach {
            sb.append(csv(it.name)).append(',').append(it.candidateCount).append(',').append(it.votes).append(',')
                .append(String.format("%.2f", it.percent)).append('\n')
        }
        sb.append("\nTransactionId,Timestamp,Candidate,Status,Device\n")
        val names = r.candidates.associate { it.candidate.candidateId to it.candidate.name }
        repo.votesFor(id).forEach {
            sb.append(csv(it.transactionId)).append(',').append(csv(fmtDateTime(it.timestamp))).append(',')
                .append(csv(names[it.candidateId] ?: it.candidateId)).append(',').append(it.status).append(',').append(csv(it.deviceId)).append('\n')
        }
        return sb.toString()
    }

    // ---------- PDF ----------
    private fun bitmapFor(name: String?, size: Int): Bitmap? {
        val f = ImageStore.file(context, name) ?: return null
        val b = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        val s = Bitmap.createScaledBitmap(b, size, size, true)
        return s
    }

    suspend fun pdfFor(id: String): ByteArray? {
        val r = repo.getResults(id) ?: return null
        val doc = PdfDocument()
        val w = 595; val h = 842; val margin = 40f
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var c = page.canvas
        var y = margin
        val title = Paint().apply { textSize = 22f; isFakeBoldText = true; color = Color.rgb(25, 50, 120); isAntiAlias = true }
        val head = Paint().apply { textSize = 14f; isFakeBoldText = true; isAntiAlias = true }
        val text = Paint().apply { textSize = 11f; isAntiAlias = true; color = Color.DKGRAY }
        val bold = Paint(text).apply { isFakeBoldText = true; color = Color.BLACK }
        val emojiPaint = Paint().apply { textSize = 28f; isAntiAlias = true }

        fun newPage() {
            doc.finishPage(page); pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            c = page.canvas; y = margin
        }
        fun ensure(space: Float) { if (y + space > h - margin) newPage() }

        c.drawText(r.election.name, margin, y + 22f, title); y += 34f
        c.drawText(if (r.isFinal) "FINAL RESULT" else "PROVISIONAL RESULT (election not finalised)", margin, y + 12f,
            Paint(head).apply { color = if (r.isFinal) Color.rgb(30, 130, 60) else Color.rgb(200, 90, 0) }); y += 26f
        listOf(
            "Date: ${fmtDate(r.election.dateMillis)}   Category: ${r.election.category}",
            "Location: ${r.election.location.ifBlank { "-" }}   Voting mode: ${VotingMode.valueOf(r.election.votingMode).label}",
            "Total valid votes: ${r.totalVotes}   Cancelled/invalid: ${r.cancelledCount}   Unconfirmed: ${r.pendingCount}",
            "Winner: ${r.winners.joinToString { it.candidate.name }.ifBlank { "None (no votes)" }}" + if (r.winners.size > 1) "  (tie)" else ""
        ).forEach { c.drawText(it, margin, y + 11f, text); y += 17f }
        y += 8f
        c.drawText("Candidate results", margin, y + 14f, head); y += 26f
        val rowH = 52f
        val bar = Paint().apply { isAntiAlias = true }
        r.candidates.forEach { cr ->
            ensure(rowH + 4)
            val photo = bitmapFor(cr.candidate.photoFile, 44)
            if (photo != null) c.drawBitmap(photo, margin, y, null)
            else { bar.color = Color.LTGRAY; c.drawRect(margin, y, margin + 44, y + 44, bar); cr.candidate.emoji?.let { c.drawText(it, margin + 6, y + 32f, emojiPaint) } }
            val logo = bitmapFor(cr.candidate.partyLogoFile, 28)
            if (logo != null) c.drawBitmap(logo, margin + 52, y + 14, null)
            else cr.candidate.partyEmoji?.let { c.drawText(it, margin + 52, y + 34f, Paint(emojiPaint).apply { textSize = 22f }) }
            val tx = margin + if (logo != null || cr.candidate.partyEmoji != null) 90 else 56
            c.drawText("#${cr.rank}  ${cr.candidate.name}", tx, y + 14f, bold)
            c.drawText("${cr.candidate.partyName} (${cr.candidate.partyAbbr})  •  Serial ${cr.candidate.serial}", tx, y + 28f, text)
            c.drawText("${cr.votes} votes  (${String.format("%.1f", cr.percent)}%)", tx, y + 42f, text)
            val barX = w - margin - 150f
            bar.color = Color.rgb(230, 230, 235); c.drawRect(barX, y + 18, barX + 150f, y + 30, bar)
            bar.color = cr.candidate.partyColor.toInt(); c.drawRect(barX, y + 18, barX + 150f * (cr.percent / 100f).toFloat(), y + 30, bar)
            y += rowH
        }
        y += 8f; ensure(60f)
        c.drawText("Party summary", margin, y + 14f, head); y += 26f
        r.parties.forEach { p ->
            ensure(18f)
            c.drawText("${p.name} (${p.abbr}) — ${p.votes} votes (${String.format("%.1f", p.percent)}%), ${p.candidateCount} candidate(s)", margin, y + 11f, text)
            y += 17f
        }
        ensure(40f); y += 14f
        c.drawText("Generated ${fmtDateTime(System.currentTimeMillis())} by ${com.kARThik.vote.utils.AppInfo.NAME} ${com.kARThik.vote.utils.AppInfo.VERSION}", margin, y + 11f,
            Paint(text).apply { textSize = 9f })
        doc.finishPage(page)
        val out = java.io.ByteArrayOutputStream()
        doc.writeTo(out); doc.close()
        return out.toByteArray()
    }

    fun cacheFile(name: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name).also { it.writeBytes(bytes) }
    }
}
