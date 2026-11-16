package com.kARThik.vote.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ElectionDao {
    @Query(
        """SELECT e.id, e.name, e.description, e.category, e.dateMillis, e.startTime, e.endTime, e.location,
        e.votingMode, e.instructions, e.status, e.createdAt, e.finalizedAt,
        (SELECT COUNT(*) FROM election_candidates c WHERE c.electionId = e.id) AS candidateCount,
        (SELECT COUNT(*) FROM vote_records v WHERE v.electionId = e.id AND v.status = 'VALID') AS voteCount
        FROM elections e ORDER BY e.dateMillis DESC, e.createdAt DESC"""
    )
    fun observeAllWithStats(): Flow<List<ElectionWithStats>>

    @Query("SELECT * FROM elections WHERE id = :id")
    fun observe(id: String): Flow<ElectionEntity?>

    @Query("SELECT * FROM elections WHERE id = :id")
    suspend fun get(id: String): ElectionEntity?

    @Query("SELECT * FROM elections")
    suspend fun getAll(): List<ElectionEntity>

    // Must be a true upsert (UPDATE in place). REPLACE would delete the row first and
    // cascade-delete every vote, session and candidate snapshot belonging to the election.
    @Upsert
    suspend fun upsert(e: ElectionEntity)

    @Query("DELETE FROM elections WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE elections SET status = 'PAUSED' WHERE status = 'ONGOING'")
    suspend fun pauseAllOngoing(): Int

    @Query("DELETE FROM elections WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()

    @Query("DELETE FROM elections")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM elections")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM elections WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>
}

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<PartyEntity>

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun get(id: String): PartyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PartyEntity)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM parties")
    suspend fun deleteAll()
}

@Dao
interface CandidateDao {
    @Query("SELECT * FROM candidates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CandidateEntity>>

    @Query("SELECT * FROM candidates ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<CandidateEntity>

    @Query("SELECT * FROM candidates WHERE id = :id")
    suspend fun get(id: String): CandidateEntity?

    @Query("SELECT * FROM candidates WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<CandidateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: CandidateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(c: CandidateEntity)

    @Query("DELETE FROM candidates WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE candidates SET partyId = :newPartyId WHERE partyId = :oldPartyId")
    suspend fun reassignParty(oldPartyId: String, newPartyId: String?)

    @Query("SELECT COUNT(*) FROM candidates WHERE partyId = :partyId")
    suspend fun countForParty(partyId: String): Int

    @Query("SELECT COUNT(*) FROM candidates")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM candidates")
    suspend fun deleteAll()
}

@Dao
interface ElectionCandidateDao {
    @Query("SELECT * FROM election_candidates WHERE electionId = :electionId ORDER BY sortOrder, serial")
    fun observe(electionId: String): Flow<List<ElectionCandidateEntity>>

    @Query("SELECT * FROM election_candidates WHERE electionId = :electionId ORDER BY sortOrder, serial")
    suspend fun get(electionId: String): List<ElectionCandidateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ElectionCandidateEntity>)

    @Query("DELETE FROM election_candidates WHERE electionId = :electionId")
    suspend fun deleteForElection(electionId: String)

    @Query("DELETE FROM election_candidates WHERE electionId = :electionId AND candidateId = :candidateId")
    suspend fun remove(electionId: String, candidateId: String)

    /** Elections (by id) that use a given party in their snapshot, with status. */
    @Query(
        """SELECT DISTINCT e.name FROM election_candidates ec JOIN elections e ON e.id = ec.electionId
           WHERE ec.partyId = :partyId"""
    )
    suspend fun electionNamesUsingParty(partyId: String): List<String>

    @Query(
        """SELECT DISTINCT e.name FROM election_candidates ec JOIN elections e ON e.id = ec.electionId
           WHERE ec.candidateId = :candidateId"""
    )
    suspend fun electionNamesUsingCandidate(candidateId: String): List<String>

    @Query(
        """SELECT COUNT(*) FROM election_candidates ec JOIN elections e ON e.id = ec.electionId
           WHERE ec.partyId = :partyId AND e.status != 'COMPLETED'"""
    )
    suspend fun activeUsesOfParty(partyId: String): Int
}

@Dao
interface VoteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(v: VoteRecordEntity): Long

    @Query("SELECT * FROM vote_records WHERE transactionId = :id")
    suspend fun get(id: String): VoteRecordEntity?

    @Query("UPDATE vote_records SET status = :status WHERE transactionId = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("SELECT * FROM vote_records WHERE electionId = :electionId ORDER BY timestamp DESC")
    fun observeForElection(electionId: String): Flow<List<VoteRecordEntity>>

    @Query("SELECT * FROM vote_records WHERE electionId = :electionId ORDER BY timestamp")
    suspend fun getForElection(electionId: String): List<VoteRecordEntity>

    @Query("SELECT * FROM vote_records WHERE electionId = :electionId AND status = 'PENDING' ORDER BY timestamp")
    suspend fun getPending(electionId: String): List<VoteRecordEntity>

    @Query("SELECT COUNT(*) FROM vote_records WHERE electionId = :electionId AND status = 'PENDING'")
    fun observePendingCount(electionId: String): Flow<Int>

    @Query("SELECT candidateId, COUNT(*) AS votes FROM vote_records WHERE electionId = :electionId AND status = 'VALID' GROUP BY candidateId")
    fun observeTally(electionId: String): Flow<List<TallyRow>>

    @Query("SELECT candidateId, COUNT(*) AS votes FROM vote_records WHERE electionId = :electionId AND status = 'VALID' GROUP BY candidateId")
    suspend fun getTally(electionId: String): List<TallyRow>

    @Query("SELECT status, COUNT(*) AS count FROM vote_records WHERE electionId = :electionId GROUP BY status")
    fun observeStatusCounts(electionId: String): Flow<List<StatusCount>>

    @Query("SELECT status, COUNT(*) AS count FROM vote_records WHERE electionId = :electionId GROUP BY status")
    suspend fun getStatusCounts(electionId: String): List<StatusCount>

    @Query("SELECT COUNT(*) FROM vote_records WHERE status = 'VALID'")
    fun observeTotalValid(): Flow<Int>
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: VotingSessionEntity)

    @Query("SELECT * FROM voting_sessions WHERE id = :id")
    suspend fun get(id: String): VotingSessionEntity?

    @Query("SELECT * FROM voting_sessions WHERE electionId = :electionId ORDER BY startedAt")
    suspend fun getForElection(electionId: String): List<VotingSessionEntity>

    @Query("UPDATE voting_sessions SET status = 'ABANDONED', endedAt = :now WHERE electionId = :electionId AND status = 'OPEN'")
    suspend fun abandonOpen(electionId: String, now: Long)
}

@Dao
interface ResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ElectionResultEntity)

    @Query("SELECT * FROM election_results WHERE electionId = :id")
    fun observe(id: String): Flow<ElectionResultEntity?>

    @Query("SELECT * FROM election_results WHERE electionId = :id")
    suspend fun get(id: String): ElectionResultEntity?

    @Query("DELETE FROM election_results WHERE electionId = :id")
    suspend fun delete(id: String)
}

@Dao
interface PairingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: DevicePairingEntity)

    @Query("SELECT * FROM device_pairings ORDER BY lastConnectedAt DESC")
    fun observeAll(): Flow<List<DevicePairingEntity>>

    @Query("DELETE FROM device_pairings")
    suspend fun deleteAll()
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(a: ElectionAuditEntity)

    @Query("SELECT * FROM election_audit WHERE electionId = :electionId ORDER BY timestamp DESC")
    fun observeForElection(electionId: String): Flow<List<ElectionAuditEntity>>

    @Query("SELECT * FROM election_audit WHERE electionId = :electionId ORDER BY timestamp")
    suspend fun getForElection(electionId: String): List<ElectionAuditEntity>

    @Query("DELETE FROM election_audit WHERE electionId = :electionId")
    suspend fun deleteForElection(electionId: String)

    @Query("DELETE FROM election_audit")
    suspend fun deleteAll()
}

@Database(
    entities = [
        ElectionEntity::class, PartyEntity::class, CandidateEntity::class,
        ElectionCandidateEntity::class, VoteRecordEntity::class, VotingSessionEntity::class,
        ElectionResultEntity::class, DevicePairingEntity::class, ElectionAuditEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun electionDao(): ElectionDao
    abstract fun partyDao(): PartyDao
    abstract fun candidateDao(): CandidateDao
    abstract fun electionCandidateDao(): ElectionCandidateDao
    abstract fun voteDao(): VoteDao
    abstract fun sessionDao(): SessionDao
    abstract fun resultDao(): ResultDao
    abstract fun pairingDao(): PairingDao
    abstract fun auditDao(): AuditDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE elections ADD COLUMN passwordHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE elections ADD COLUMN passwordSalt TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE elections ADD COLUMN ballotStyle TEXT NOT NULL DEFAULT 'MODERN'")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE candidates ADD COLUMN emoji TEXT")
                db.execSQL("ALTER TABLE parties ADD COLUMN emoji TEXT")
                db.execSQL("ALTER TABLE election_candidates ADD COLUMN emoji TEXT")
                db.execSQL("ALTER TABLE election_candidates ADD COLUMN partyEmoji TEXT")
            }
        }
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "matam.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
