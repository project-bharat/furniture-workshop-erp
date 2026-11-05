package com.projectbharat.furnitureworkshoperp

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "work_records")
data class WorkRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val detail: String,
    val status: String,
    val amount: Double = 0.0,
    val kind: String,
    val phone: String = "",
    val role: String = "",
    val attendance: String = "",
    val task: String = "",
    val monthlySalary: Double = 0.0
)

@Entity(
    tableName = "payroll_adjustments",
    indices = [Index(value = ["personId", "adjustmentDate"])]
)
data class PayrollAdjustment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val personId: Int,
    val adjustmentDate: String,
    val type: String,
    val amount: Double,
    val recoveredAmount: Double = 0.0,
    val note: String = ""
)

@Entity(
    tableName = "payroll_records",
    indices = [Index(value = ["personId", "payrollMonth"], unique = true)]
)
data class PayrollRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val personId: Int,
    val payrollMonth: String,
    val monthlySalary: Double,
    val workingDays: Double,
    val presentDays: Double,
    val halfDays: Double,
    val paidLeaveDays: Double,
    val unpaidDays: Double,
    val paidDays: Double,
    val incentives: Double,
    val bonuses: Double,
    val otherDeductions: Double,
    val advanceRecovery: Double,
    val grossPay: Double,
    val netPay: Double,
    val status: String = "DRAFT",
    val createdAt: String,
    val approvedAt: String = ""
)

@Entity(
    tableName = "payroll_payments",
    indices = [Index(value = ["payrollRecordId"]), Index(value = ["personId", "paymentDate"])]
)
data class PayrollPayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val payrollRecordId: Int,
    val personId: Int,
    val paymentDate: String,
    val amount: Double,
    val method: String,
    val reference: String = "",
    val note: String = ""
)

@Entity(
    tableName = "attendance_entries",
    indices = [Index(value = ["personId", "attendanceDate"], unique = true)]
)
data class AttendanceEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val personId: Int,
    val attendanceDate: String,
    val status: String,
    val note: String = ""
)

@Dao
interface WorkRecordDao {
    @Query("SELECT * FROM work_records ORDER BY id DESC")
    fun observeAll(): Flow<List<WorkRecord>>

    @Query("SELECT COUNT(*) FROM work_records")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WorkRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<WorkRecord>)

    @Update
    suspend fun update(record: WorkRecord)

    @Query("DELETE FROM work_records WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_entries WHERE personId = :personId ORDER BY attendanceDate DESC")
    fun observeForPerson(personId: Int): Flow<List<AttendanceEntry>>

    @Query("SELECT * FROM attendance_entries WHERE personId = :personId AND attendanceDate = :date LIMIT 1")
    suspend fun findForDate(personId: Int, date: String): AttendanceEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: AttendanceEntry)

    @Query("DELETE FROM attendance_entries WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Int)
}

@Dao
interface PayrollDao {
    @Query("SELECT * FROM payroll_adjustments ORDER BY adjustmentDate DESC, id DESC")
    fun observeAdjustments(): Flow<List<PayrollAdjustment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAdjustment(adjustment: PayrollAdjustment): Long

    @Query("UPDATE payroll_adjustments SET recoveredAmount = :recovered WHERE id = :id")
    suspend fun setRecoveredAmount(id: Int, recovered: Double)

    @Query("SELECT * FROM payroll_records ORDER BY payrollMonth DESC, id DESC")
    fun observePayroll(): Flow<List<PayrollRecord>>

    @Query("SELECT * FROM payroll_records WHERE personId = :personId AND payrollMonth = :month LIMIT 1")
    suspend fun findPayroll(personId: Int, month: String): PayrollRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePayroll(record: PayrollRecord): Long

    @Query("UPDATE payroll_records SET status = :status, approvedAt = :approvedAt WHERE id = :id")
    suspend fun updatePayrollStatus(id: Int, status: String, approvedAt: String)

    @Query("SELECT * FROM payroll_payments ORDER BY paymentDate DESC, id DESC")
    fun observePayments(): Flow<List<PayrollPayment>>

    @Insert
    suspend fun savePayment(payment: PayrollPayment): Long
}

@Database(
    entities = [WorkRecord::class, AttendanceEntry::class, PayrollAdjustment::class, PayrollRecord::class, PayrollPayment::class],
    version = 3,
    exportSchema = false
)
abstract class WorkshopDatabase : RoomDatabase() {
    abstract fun workRecordDao(): WorkRecordDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun payrollDao(): PayrollDao

    companion object {
        @Volatile private var INSTANCE: WorkshopDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE work_records ADD COLUMN monthlySalary REAL NOT NULL DEFAULT 0.0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attendance_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        personId INTEGER NOT NULL,
                        attendanceDate TEXT NOT NULL,
                        status TEXT NOT NULL,
                        note TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_entries_personId_attendanceDate ON attendance_entries(personId, attendanceDate)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS payroll_adjustments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        personId INTEGER NOT NULL,
                        adjustmentDate TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amount REAL NOT NULL,
                        recoveredAmount REAL NOT NULL DEFAULT 0.0,
                        note TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_adjustments_personId_adjustmentDate ON payroll_adjustments(personId, adjustmentDate)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS payroll_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        personId INTEGER NOT NULL,
                        payrollMonth TEXT NOT NULL,
                        monthlySalary REAL NOT NULL,
                        workingDays REAL NOT NULL,
                        presentDays REAL NOT NULL,
                        halfDays REAL NOT NULL,
                        paidLeaveDays REAL NOT NULL,
                        unpaidDays REAL NOT NULL,
                        paidDays REAL NOT NULL,
                        incentives REAL NOT NULL,
                        bonuses REAL NOT NULL,
                        otherDeductions REAL NOT NULL,
                        advanceRecovery REAL NOT NULL,
                        grossPay REAL NOT NULL,
                        netPay REAL NOT NULL,
                        status TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        approvedAt TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_records_personId_payrollMonth ON payroll_records(personId, payrollMonth)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS payroll_payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        payrollRecordId INTEGER NOT NULL,
                        personId INTEGER NOT NULL,
                        paymentDate TEXT NOT NULL,
                        amount REAL NOT NULL,
                        method TEXT NOT NULL,
                        reference TEXT NOT NULL,
                        note TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_payments_payrollRecordId ON payroll_payments(payrollRecordId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_payments_personId_paymentDate ON payroll_payments(personId, paymentDate)")
            }
        }

        fun getInstance(context: Context): WorkshopDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkshopDatabase::class.java,
                    "aakruti_workshop.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}

fun initialWorkshopRecords() = listOf(
    WorkRecord(1,"Sharma Residence","Modular kitchen and wardrobes · Amit Sharma","Site visit",485000.0,"Enquiry",phone="9876543210"),
    WorkRecord(2,"Mehta Living Room","TV unit and wall panels · Neha Mehta","Quotation sent",185000.0,"Enquiry",phone="9876501234"),
    WorkRecord(3,"Kulkarni Villa","Full home interiors · Rohan Kulkarni","In production",1250000.0,"Project"),
    WorkRecord(4,"Patil Bedroom Set","Bed, side tables and wardrobe · Sonal Patil","Design approved",275000.0,"Project"),
    WorkRecord(5,"Kitchen carcass · Cutting","Kulkarni Villa · Assigned to Ramesh","In progress",0.0,"Production",role="Carpentry"),
    WorkRecord(6,"Wardrobe panels · Polishing","Patil Bedroom Set · Assigned to Suresh","Pending",0.0,"Production",role="Finishing"),
    WorkRecord(7,"BWP Plywood 18mm","Greenply · Godown A · 12 sheets","In stock",0.0,"Inventory",role="Sheets"),
    WorkRecord(8,"Hettich soft-close hinges","Hardware · Godown A · 8 units · reorder at 20","Low stock",0.0,"Inventory",role="Hardware"),
    WorkRecord(9,"Ramesh Pawar","Carpenter · 9876543211","Present",0.0,"Workforce",phone="9876543211",role="Carpenter",attendance="Present",task="Kitchen carcass cutting",monthlySalary=22000.0),
    WorkRecord(10,"Suresh Jadhav","Polisher · 9876543212","Present",0.0,"Workforce",phone="9876543212",role="Finisher",attendance="Present",task="Wardrobe panel polishing",monthlySalary=20000.0),
    WorkRecord(11,"Imran Shaikh","Installer · 9876543213","Absent",0.0,"Workforce",phone="9876543213",role="Installer",attendance="Absent",task="Site measurement",monthlySalary=24000.0),
    WorkRecord(12,"Purchase · Edge band rolls","City Hardware · 18,500","Awaiting approval",18500.0,"Approval"),
    WorkRecord(13,"Quotation · Kulkarni Villa","Final estimate","Awaiting approval",1250000.0,"Approval")
)
