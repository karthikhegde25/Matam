package com.kARThik.vote.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ElectionStatus(val label: String) {
    DRAFT("Draft"), READY("Ready"), ONGOING("Ongoing"), PAUSED("Paused"), COMPLETED("Completed")
}

enum class VotingMode(val label: String) {
    SINGLE_PHONE("Single-Phone Voting"), BLUETOOTH("Two-Phone Bluetooth Voting")
}

enum class BallotStyle(val label: String, val description: String) {
    MODERN("Modern", "Large photo cards, as used throughout the app."),
    EVM("EVM style", "Electronic voting machine layout: name, symbol and a blue button beside each candidate.");
    companion object { fun from(s: String?) = values().firstOrNull { it.name == s } ?: MODERN }
}

enum class VoteStatus { VALID, PENDING, CANCELLED, INVALID }

val ELECTION_CATEGORIES = listOf(
    "School election", "College election", "Organisation election",
    "Club election", "Association election", "Custom election"
)

@Entity(tableName = "elections")
data class ElectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val category: String = ELECTION_CATEGORIES[0],
    val dateMillis: Long,
    val startTime: String = "",
    val endTime: String = "",
    val location: String = "",
    val votingMode: String = VotingMode.SINGLE_PHONE.name,
    val instructions: String = "",
    val status: String = ElectionStatus.DRAFT.name,
    val createdAt: Long,
    val finalizedAt: Long? = null,
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val ballotStyle: String = "MODERN"
)

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val abbreviation: String = "",
    val logoFile: String? = null,
    val bannerFile: String? = null,
    val description: String = "",
    val colorArgb: Long = 0xFF1E88E5,
    val leader: String = "",
    val emoji: String? = null
)

@Entity(tableName = "candidates")
data class CandidateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val photoFile: String? = null,
    val serial: String = "",
    val partyId: String? = null,
    val description: String = "",
    val designation: String = "",
    val manifesto: String = "",
    val extraInfo: String = "",
    val emoji: String? = null
)

/** Snapshot of a candidate (and their party) frozen inside an election. */
@Entity(
    tableName = "election_candidates",
    primaryKeys = ["electionId", "candidateId"],
    foreignKeys = [ForeignKey(
        entity = ElectionEntity::class, parentColumns = ["id"],
        childColumns = ["electionId"], onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("electionId")]
)
data class ElectionCandidateEntity(
    val electionId: String,
    val candidateId: String,
    val name: String,
    val photoFile: String? = null,
    val serial: String = "",
    val partyId: String? = null,
    val partyName: String = "",
    val partyAbbr: String = "",
    val partyLogoFile: String? = null,
    val partyColor: Long = 0xFF757575,
    val designation: String = "",
    val description: String = "",
    val manifesto: String = "",
    val extraInfo: String = "",
    val sortOrder: Int = 0,
    val emoji: String? = null,
    val partyEmoji: String? = null
)

@Entity(
    tableName = "vote_records",
    foreignKeys = [ForeignKey(
        entity = ElectionEntity::class, parentColumns = ["id"],
        childColumns = ["electionId"], onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("electionId"), Index("candidateId"), Index("sessionId")]
)
data class VoteRecordEntity(
    @PrimaryKey val transactionId: String,
    val electionId: String,
    val candidateId: String,
    val sessionId: String,
    val timestamp: Long,
    val status: String,
    val deviceId: String
)

@Entity(
    tableName = "voting_sessions",
    foreignKeys = [ForeignKey(
        entity = ElectionEntity::class, parentColumns = ["id"],
        childColumns = ["electionId"], onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("electionId")]
)
data class VotingSessionEntity(
    @PrimaryKey val id: String,
    val electionId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String = "OPEN", // OPEN, COMPLETED, ABANDONED
    val deviceId: String = ""
)

@Entity(
    tableName = "election_results",
    foreignKeys = [ForeignKey(
        entity = ElectionEntity::class, parentColumns = ["id"],
        childColumns = ["electionId"], onDelete = ForeignKey.CASCADE
    )]
)
data class ElectionResultEntity(
    @PrimaryKey val electionId: String,
    val finalizedAt: Long,
    val totalVotes: Int,
    val cancelledCount: Int,
    val winnerNames: String,
    val snapshotJson: String
)

@Entity(tableName = "device_pairings")
data class DevicePairingEntity(
    @PrimaryKey val address: String,
    val name: String,
    val role: String,
    val lastConnectedAt: Long,
    val lastElectionId: String = ""
)

/** electionId is "" for global (non-election-specific) events. No FK so history can be kept/cleared independently. */
@Entity(tableName = "election_audit", indices = [Index("electionId")])
data class ElectionAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val electionId: String,
    val timestamp: Long,
    val action: String,
    val details: String
)

data class ElectionWithStats(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val dateMillis: Long,
    val startTime: String,
    val endTime: String,
    val location: String,
    val votingMode: String,
    val instructions: String,
    val status: String,
    val createdAt: Long,
    val finalizedAt: Long?,
    val candidateCount: Int,
    val voteCount: Int
) {
    fun toEntity() = ElectionEntity(
        id, name, description, category, dateMillis, startTime, endTime, location,
        votingMode, instructions, status, createdAt, finalizedAt
    )
}

data class TallyRow(val candidateId: String, val votes: Int)
data class StatusCount(val status: String, val count: Int)
