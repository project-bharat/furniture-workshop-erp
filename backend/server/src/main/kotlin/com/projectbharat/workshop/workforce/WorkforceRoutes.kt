package com.projectbharat.workshop.workforce

import com.projectbharat.workshop.auth.AuthFailure
import com.projectbharat.workshop.auth.AuthService
import com.projectbharat.workshop.auth.AuthUser
import com.projectbharat.workshop.db.Database
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

@Serializable data class EmployeeInput(val fullName:String,val roleTitle:String,val monthlySalary:Double,val employeeCode:String?=null,val phone:String?=null,val email:String?=null,val employmentType:String="FULL_TIME",val employmentStatus:String="ACTIVE",val joiningDate:String?=null,val exitDate:String?=null,val weeklyOffDay:Int=0,val branchId:String?=null,val notes:String?=null)
@Serializable data class EmployeeDto(val id:String,val employeeCode:String?,val fullName:String,val roleTitle:String,val phone:String?,val email:String?,val employmentType:String,val employmentStatus:String,val joiningDate:String?,val exitDate:String?,val monthlySalary:Double,val payBasis:String,val weeklyOffDay:Int,val branchId:String?,val notes:String?,val createdAt:String,val updatedAt:String)
@Serializable data class AttendanceInput(val employeeId:String,val attendanceDate:String,val status:String,val checkInAt:String?=null,val checkOutAt:String?=null,val regularHours:Double=0.0,val overtimeHours:Double=0.0,val note:String?=null)
@Serializable data class AttendanceBatch(val entries:List<AttendanceInput>)
@Serializable data class AttendanceDto(val id:String,val employeeId:String,val attendanceDate:String,val status:String,val checkInAt:String?,val checkOutAt:String?,val regularHours:Double,val overtimeHours:Double,val note:String?,val updatedAt:String)
@Serializable data class SalaryComponentInput(val componentCode:String,val componentName:String,val componentType:String,val calculationType:String="FIXED",val amount:Double,val effectiveFrom:String,val effectiveTo:String?=null)
@Serializable data class SalaryComponentDto(val id:String,val employeeId:String,val componentCode:String,val componentName:String,val componentType:String,val calculationType:String,val amount:Double,val active:Boolean,val effectiveFrom:String,val effectiveTo:String?)
@Serializable data class AdjustmentInput(val employeeId:String,val adjustmentDate:String,val type:String,val amount:Double,val reason:String)
@Serializable data class AdjustmentDto(val id:String,val employeeId:String,val adjustmentDate:String,val type:String,val amount:Double,val recoveredAmount:Double,val outstandingAmount:Double,val status:String,val reason:String)
@Serializable data class PaymentInput(val amount:Double,val paymentDate:String,val paymentMethod:String,val referenceNumber:String?=null,val note:String?=null,val idempotencyKey:String)
@Serializable data class PaymentDto(val id:String,val payrollItemId:String,val employeeId:String,val paymentDate:String,val amount:Double,val paymentMethod:String,val referenceNumber:String?,val note:String?)
@Serializable data class PayrollRunRequest(val payrollMonth:String)
@Serializable data class PayrollItemDto(val id:String,val employeeId:String,val employeeName:String,val roleTitle:String,val monthlySalary:Double,val workingDays:Double,val presentDays:Double,val halfDays:Double,val paidLeaveDays:Double,val unpaidDays:Double,val grossPay:Double,val otherDeductions:Double,val advanceRecovery:Double,val netPay:Double,val paidAmount:Double,val balance:Double)
@Serializable data class PayrollRunDto(val id:String,val payrollMonth:String,val status:String,val employeeCount:Int,val grossTotal:Double,val deductionTotal:Double,val netTotal:Double,val items:List<PayrollItemDto>)
@Serializable data class PayrollOverview(val runs:List<PayrollRunDto>,val payments:List<PaymentDto>)
@Serializable data class ApiError(val error:String)

private val attendanceStates=setOf("PRESENT","ABSENT","HALF_DAY","PAID_LEAVE","UNPAID_LEAVE","HOLIDAY","WEEKLY_OFF")
private val employmentTypes=setOf("FULL_TIME","PART_TIME","CONTRACT","DAILY_WAGE")
private val employmentStates=setOf("ACTIVE","ON_LEAVE","EXITED")
private val adjustmentStates=setOf("ADVANCE","DEDUCTION","INCENTIVE","BONUS")
private val paymentStates=setOf("CASH","BANK_TRANSFER","UPI","CHEQUE","OTHER")

private suspend fun ApplicationCall.authorize(service:AuthService,permission:String):AuthUser {
 val h=request.headers[HttpHeaders.Authorization]
 val token=h?.takeIf{it.startsWith("Bearer ",true)}?.substringAfter(' ')?:throw AuthFailure(401,"Bearer access token is required")
 val u=service.authenticate(token);if(permission !in u.permissions)throw AuthFailure(403,"Permission denied");return u
}
private suspend fun ApplicationCall.fail(e:AuthFailure){respond(HttpStatusCode.fromValue(e.status),ApiError(e.message))}
private fun date(v:String,label:String)=runCatching{LocalDate.parse(v)}.getOrElse{throw AuthFailure(400,label+" must use YYYY-MM-DD")}
private fun amount(v:Double,label:String):BigDecimal{if(!v.isFinite()||v<0)throw AuthFailure(400,label+" must be non-negative");return BigDecimal.valueOf(v).setScale(2,RoundingMode.HALF_UP)}
private fun Connection.audit(u:AuthUser,action:String,type:String,id:UUID){prepareStatement("INSERT INTO audit_logs(organization_id,actor_user_id,action_key,entity_type,entity_id) VALUES(?,?,?,?,?)").use{s->s.setObject(1,UUID.fromString(u.organizationId));s.setObject(2,UUID.fromString(u.id));s.setString(3,action);s.setString(4,type);s.setObject(5,id);s.executeUpdate()}}
private fun Connection.hasEmployee(org:UUID,id:UUID)=prepareStatement("SELECT 1 FROM employees WHERE organization_id=? AND id=? AND archived_at IS NULL").use{s->s.setObject(1,org);s.setObject(2,id);s.executeQuery().use{it.next()}}

fun Route.workforceRoutes(auth:AuthService) {
 route("/employees") {
  get { try {
   val u=call.authorize(auth,"workforce.view")
   val rows=Database.dataSource().connection.use{c->c.prepareStatement("""SELECT * FROM employees WHERE organization_id=? AND archived_at IS NULL ORDER BY lower(full_name)""").use{s->s.setObject(1,UUID.fromString(u.organizationId));s.executeQuery().use{rs->buildList{while(rs.next())add(rs.employeeDto())}}}}
   call.respond(rows)
  }catch(e:AuthFailure){call.fail(e)} }
  post { try {
   val u=call.authorize(auth,"workforce.manage");val x=call.receive<EmployeeInput>();val org=UUID.fromString(u.organizationId)
   if(x.fullName.isBlank()||x.fullName.length>180||x.roleTitle.isBlank()||x.roleTitle.length>100)throw AuthFailure(400,"Employee name and role are required")
   if(x.employmentType !in employmentTypes||x.employmentStatus !in employmentStates||x.weeklyOffDay !in 0..6)throw AuthFailure(400,"Employment fields are invalid")
   val salary=amount(x.monthlySalary,"Monthly salary");val joining=x.joiningDate?.let{date(it,"Joining date")};val exit=x.exitDate?.let{date(it,"Exit date")}
   if(joining!=null&&exit!=null&&exit.isBefore(joining))throw AuthFailure(400,"Exit date cannot precede joining date")
   val branch=x.branchId?.let{runCatching{UUID.fromString(it)}.getOrNull()?:throw AuthFailure(400,"Branch id is invalid")}
   val row=Database.dataSource().connection.use{c->c.autoCommit=false;try{
    if(branch!=null&&!c.prepareStatement("SELECT 1 FROM branches WHERE organization_id=? AND id=?").use{s->s.setObject(1,org);s.setObject(2,branch);s.executeQuery().use{it.next()}})throw AuthFailure(400,"Branch is outside this organization")
    val dto=c.prepareStatement("""INSERT INTO employees(organization_id,branch_id,employee_code,full_name,role_title,phone,email,employment_type,employment_status,joining_date,exit_date,monthly_salary,weekly_off_day,notes,created_by)
    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING *""").use{s->
     s.setObject(1,org);s.setObject(2,branch);s.setString(3,x.employeeCode?.trim());s.setString(4,x.fullName.trim());s.setString(5,x.roleTitle.trim());s.setString(6,x.phone?.trim());s.setString(7,x.email?.trim());s.setString(8,x.employmentType);s.setString(9,x.employmentStatus);s.setObject(10,joining);s.setObject(11,exit);s.setBigDecimal(12,salary);s.setInt(13,x.weeklyOffDay);s.setString(14,x.notes?.trim());s.setObject(15,UUID.fromString(u.id))
     s.executeQuery().use{it.next();it.employeeDto()}
    }
    c.audit(u,"employee.created","employee",UUID.fromString(dto.id));c.commit();dto
   }catch(t:Throwable){c.rollback();throw t}}
   call.respond(HttpStatusCode.Created,row)
  }catch(e:AuthFailure){call.fail(e)}catch(e:java.sql.SQLException){call.fail(AuthFailure(409,"Employee code conflicts with an existing record"))} }
  patch("/{id}") { try {
   val u=call.authorize(auth,"workforce.manage");val id=call.parameters["id"]?.let{runCatching{UUID.fromString(it)}.getOrNull()}?:throw AuthFailure(400,"Employee id is invalid");val x=call.receive<EmployeeInput>();val org=UUID.fromString(u.organizationId)
   if(x.fullName.isBlank()||x.roleTitle.isBlank()||x.weeklyOffDay !in 0..6||x.employmentType !in employmentTypes||x.employmentStatus !in employmentStates)throw AuthFailure(400,"Employee fields are invalid")
   val joining=x.joiningDate?.let{date(it,"Joining date")};val exit=x.exitDate?.let{date(it,"Exit date")}
   if(joining!=null&&exit!=null&&exit.isBefore(joining))throw AuthFailure(400,"Exit date cannot precede joining date")
   val branch=x.branchId?.let{runCatching{UUID.fromString(it)}.getOrNull()?:throw AuthFailure(400,"Branch id is invalid")}
   val n=Database.dataSource().connection.use{c->c.prepareStatement("""UPDATE employees SET branch_id=?,employee_code=?,full_name=?,role_title=?,phone=?,email=?,employment_type=?,employment_status=?,joining_date=?,exit_date=?,monthly_salary=?,weekly_off_day=?,notes=?,updated_at=now()
    WHERE organization_id=? AND id=? AND archived_at IS NULL AND (?::uuid IS NULL OR EXISTS(SELECT 1 FROM branches b WHERE b.organization_id=? AND b.id=?))""").use{s->
    s.setObject(1,branch);s.setString(2,x.employeeCode?.trim());s.setString(3,x.fullName.trim());s.setString(4,x.roleTitle.trim());s.setString(5,x.phone?.trim());s.setString(6,x.email?.trim());s.setString(7,x.employmentType);s.setString(8,x.employmentStatus);s.setObject(9,joining);s.setObject(10,exit);s.setBigDecimal(11,amount(x.monthlySalary,"Monthly salary"));s.setInt(12,x.weeklyOffDay);s.setString(13,x.notes?.trim());s.setObject(14,org);s.setObject(15,id);s.setObject(16,branch);s.setObject(17,org);s.setObject(18,branch);s.executeUpdate()
   }.also{if(it>0)c.audit(u,"employee.updated","employee",id)}}
   if(n==0)throw AuthFailure(404,"Employee not found or branch is invalid");call.respond(mapOf("status" to "updated"))
  }catch(e:AuthFailure){call.fail(e)}catch(e:java.sql.SQLException){call.fail(AuthFailure(409,"Employee code conflicts with an existing record"))} }
  delete("/{id}") { try {
   val u=call.authorize(auth,"workforce.manage");val id=call.parameters["id"]?.let{runCatching{UUID.fromString(it)}.getOrNull()}?:throw AuthFailure(400,"Employee id is invalid")
   val n=Database.dataSource().connection.use{c->c.prepareStatement("UPDATE employees SET archived_at=now(),employment_status='EXITED',exit_date=COALESCE(exit_date,CURRENT_DATE),updated_at=now() WHERE organization_id=? AND id=? AND archived_at IS NULL").use{s->s.setObject(1,UUID.fromString(u.organizationId));s.setObject(2,id);s.executeUpdate()}.also{if(it>0)c.audit(u,"employee.archived","employee",id)}}
   if(n==0)throw AuthFailure(404,"Employee not found");call.respond(HttpStatusCode.NoContent)
  }catch(e:AuthFailure){call.fail(e)} }
  route("/{id}/salary-components") {
   get { try {
    val u=call.authorize(auth,"payroll.view");val id=call.parameters["id"]?.let{runCatching{UUID.fromString(it)}.getOrNull()}?:throw AuthFailure(400,"Employee id is invalid")
    val rows=Database.dataSource().connection.use{c->c.prepareStatement("SELECT * FROM employee_salary_components WHERE organization_id=? AND employee_id=? ORDER BY effective_from DESC,component_name").use{s->s.setObject(1,UUID.fromString(u.organizationId));s.setObject(2,id);s.executeQuery().use{rs->buildList{while(rs.next())add(rs.salaryComponentDto())}}}}
    call.respond(rows)
   }catch(e:AuthFailure){call.fail(e)} }
   post { try {
    val u=call.authorize(auth,"payroll.manage");val id=call.parameters["id"]?.let{runCatching{UUID.fromString(it)}.getOrNull()}?:throw AuthFailure(400,"Employee id is invalid")
    val x=call.receive<SalaryComponentInput>();val code=x.componentCode.trim().uppercase();val name=x.componentName.trim()
    if(code.isBlank()||code.length>40||name.isBlank()||name.length>100)throw AuthFailure(400,"Component code and name are required")
    if(x.componentType !in setOf("EARNING","DEDUCTION")||x.calculationType !in setOf("FIXED","PERCENTAGE"))throw AuthFailure(400,"Component type or calculation is invalid")
    val amt=amount(x.amount,"Component amount");if(amt<=BigDecimal.ZERO)throw AuthFailure(400,"Component amount must be greater than zero")
    if(x.calculationType=="PERCENTAGE"&&amt>BigDecimal("100"))throw AuthFailure(400,"Percentage components cannot exceed 100")
    val from=date(x.effectiveFrom,"Effective from");val to=x.effectiveTo?.let{date(it,"Effective to")};if(to!=null&&to.isBefore(from))throw AuthFailure(400,"Effective to cannot precede effective from")
    val org=UUID.fromString(u.organizationId)
    val row=Database.dataSource().connection.use{c->c.autoCommit=false;try{
     if(!c.hasEmployee(org,id))throw AuthFailure(404,"Employee not found")
     val dto=c.prepareStatement("INSERT INTO employee_salary_components(organization_id,employee_id,component_code,component_name,component_type,calculation_type,amount,effective_from,effective_to) VALUES(?,?,?,?,?,?,?,?,?) RETURNING *").use{s->s.setObject(1,org);s.setObject(2,id);s.setString(3,code);s.setString(4,name);s.setString(5,x.componentType);s.setString(6,x.calculationType);s.setBigDecimal(7,amt);s.setObject(8,from);s.setObject(9,to);s.executeQuery().use{it.next();it.salaryComponentDto()}}
     c.audit(u,"payroll.salary_component_created","salary_component",UUID.fromString(dto.id));c.commit();dto
    }catch(t:Throwable){c.rollback();throw t}}
    call.respond(HttpStatusCode.Created,row)
   }catch(e:AuthFailure){call.fail(e)}catch(e:java.sql.SQLException){call.fail(AuthFailure(409,"A component with this code and effective date already exists"))} }
   delete("/{componentId}") { try {
    val u=call.authorize(auth,"payroll.manage");val id=call.parameters["id"]?.let{runCatching{UUID.fromString(it)}.getOrNull()}?:throw AuthFailure(400,"Employee id is invalid")
    val component=call.parameters["componentId"]?.let{runCatching{UUID.fromString(it)}.getOrNull()}?:throw AuthFailure(400,"Component id is invalid")
    val n=Database.dataSource().connection.use{c->c.prepareStatement("UPDATE employee_salary_components SET is_active=false,effective_to=CASE WHEN effective_from>CURRENT_DATE THEN effective_from ELSE LEAST(COALESCE(effective_to,CURRENT_DATE),CURRENT_DATE) END WHERE organization_id=? AND employee_id=? AND id=? AND is_active").use{s->s.setObject(1,UUID.fromString(u.organizationId));s.setObject(2,id);s.setObject(3,component);s.executeUpdate()}.also{if(it>0)c.audit(u,"payroll.salary_component_disabled","salary_component",component)}}
    if(n==0)throw AuthFailure(404,"Active salary component not found");call.respond(HttpStatusCode.NoContent)
   }catch(e:AuthFailure){call.fail(e)} }
  }
 }
 route("/attendance") {
  get { try {
   val u=call.authorize(auth,"attendance.view");val org=UUID.fromString(u.organizationId)
   val from=call.request.queryParameters["from"]?.let{date(it,"from")};val to=call.request.queryParameters["to"]?.let{date(it,"to")}
   val employee=call.request.queryParameters["employeeId"]?.let{runCatching{UUID.fromString(it)}.getOrNull()?:throw AuthFailure(400,"Employee id is invalid")}
   if(from!=null&&to!=null&&to.isBefore(from))throw AuthFailure(400,"to cannot precede from")
   val rows=Database.dataSource().connection.use{c->c.prepareStatement("""SELECT * FROM attendance_entries WHERE organization_id=? AND (?::uuid IS NULL OR employee_id=?) AND (?::date IS NULL OR attendance_date>=?) AND (?::date IS NULL OR attendance_date<=?) ORDER BY attendance_date DESC LIMIT 5000""").use{s->
    s.setObject(1,org);s.setObject(2,employee);s.setObject(3,employee);s.setObject(4,from);s.setObject(5,from);s.setObject(6,to);s.setObject(7,to);s.executeQuery().use{rs->buildList{while(rs.next())add(rs.attendanceDto())}}
   }}
   call.respond(rows)
  }catch(e:AuthFailure){call.fail(e)} }
  post("/batch") { try {
   val u=call.authorize(auth,"attendance.manage");val batch=call.receive<AttendanceBatch>();if(batch.entries.isEmpty()||batch.entries.size>500)throw AuthFailure(400,"Batch must contain 1 to 500 entries")
   val org=UUID.fromString(u.organizationId)
   val normalized=batch.entries.map{x->val id=runCatching{UUID.fromString(x.employeeId)}.getOrNull()?:throw AuthFailure(400,"Employee id is invalid");val d=date(x.attendanceDate,"Attendance date");val st=x.status.uppercase();if(st !in attendanceStates)throw AuthFailure(400,"Attendance status is invalid");if(!x.regularHours.isFinite()||!x.overtimeHours.isFinite()||x.regularHours !in 0.0..24.0||x.overtimeHours !in 0.0..24.0)throw AuthFailure(400,"Hours must be between 0 and 24");Triple(x,id to d,st)}
   if(normalized.map{it.second to it.third}.distinct().size!=normalized.size)throw AuthFailure(400,"Duplicate employee/date entries in batch")
   val rows=Database.dataSource().connection.use{c->c.autoCommit=false;try{
     normalized.forEach{(x,key,st)->val id=key.first;val d=key.second
     if(!c.hasEmployee(org,id))throw AuthFailure(404,"Employee not found")
     val ci=x.checkInAt?.let{Timestamp.from(runCatching{Instant.parse(it)}.getOrElse{throw AuthFailure(400,"checkInAt must be ISO-8601")})}
     val co=x.checkOutAt?.let{Timestamp.from(runCatching{Instant.parse(it)}.getOrElse{throw AuthFailure(400,"checkOutAt must be ISO-8601")})}
     if(ci!=null&&co!=null&&co.before(ci))throw AuthFailure(400,"Check-out cannot precede check-in")
     c.prepareStatement("""INSERT INTO attendance_entries(organization_id,employee_id,attendance_date,status,check_in_at,check_out_at,regular_hours,overtime_hours,note,recorded_by)
      VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(organization_id,employee_id,attendance_date) DO UPDATE SET status=EXCLUDED.status,check_in_at=EXCLUDED.check_in_at,check_out_at=EXCLUDED.check_out_at,regular_hours=EXCLUDED.regular_hours,overtime_hours=EXCLUDED.overtime_hours,note=EXCLUDED.note,recorded_by=EXCLUDED.recorded_by,source='MANUAL',updated_at=now()""").use{s->
      s.setObject(1,org);s.setObject(2,id);s.setObject(3,d);s.setString(4,st);s.setTimestamp(5,ci);s.setTimestamp(6,co);s.setBigDecimal(7,BigDecimal.valueOf(x.regularHours));s.setBigDecimal(8,BigDecimal.valueOf(x.overtimeHours));s.setString(9,x.note?.trim());s.setObject(10,UUID.fromString(u.id));s.executeUpdate()
     }
    }
    c.audit(u,"attendance.batch_saved","attendance",UUID.randomUUID());c.commit()
    normalized.map{(_,key,_)->c.prepareStatement("SELECT * FROM attendance_entries WHERE organization_id=? AND employee_id=? AND attendance_date=?").use{s->s.setObject(1,org);s.setObject(2,key.first);s.setObject(3,key.second);s.executeQuery().use{it.next();it.attendanceDto()}}}
   }catch(t:Throwable){c.rollback();throw t}}
   call.respond(rows)
  }catch(e:AuthFailure){call.fail(e)} }
 }
 route("/payroll/adjustments") {
  get { try {
   val u=call.authorize(auth,"payroll.view");val emp=call.request.queryParameters["employeeId"]?.let{runCatching{UUID.fromString(it)}.getOrNull()?:throw AuthFailure(400,"Employee id is invalid")}
   val rows=Database.dataSource().connection.use{c->c.prepareStatement("""SELECT * FROM payroll_adjustments WHERE organization_id=? AND (?::uuid IS NULL OR employee_id=?) AND status<>'VOID' ORDER BY adjustment_date DESC LIMIT 2000""").use{s->s.setObject(1,UUID.fromString(u.organizationId));s.setObject(2,emp);s.setObject(3,emp);s.executeQuery().use{rs->buildList{while(rs.next())add(rs.adjustmentDto())}}}}
   call.respond(rows)
  }catch(e:AuthFailure){call.fail(e)} }
  post { try {
   val u=call.authorize(auth,"payroll.manage");val x=call.receive<AdjustmentInput>();val emp=runCatching{UUID.fromString(x.employeeId)}.getOrNull()?:throw AuthFailure(400,"Employee id is invalid")
   val d=date(x.adjustmentDate,"Adjustment date");val type=x.type.uppercase();if(type !in adjustmentStates)throw AuthFailure(400,"Adjustment type is invalid")
   val amt=amount(x.amount,"Adjustment amount");if(amt<=BigDecimal.ZERO||x.reason.isBlank()||x.reason.length>1000)throw AuthFailure(400,"Positive amount and reason are required")
   val row=Database.dataSource().connection.use{c->c.autoCommit=false;try{
    val org=UUID.fromString(u.organizationId);if(!c.hasEmployee(org,emp))throw AuthFailure(404,"Employee not found")
    val dto=c.prepareStatement("INSERT INTO payroll_adjustments(organization_id,employee_id,adjustment_type,adjustment_date,amount,reason,created_by) VALUES(?,?,?,?,?,?,?) RETURNING *").use{s->s.setObject(1,org);s.setObject(2,emp);s.setString(3,type);s.setObject(4,d);s.setBigDecimal(5,amt);s.setString(6,x.reason.trim());s.setObject(7,UUID.fromString(u.id));s.executeQuery().use{it.next();it.adjustmentDto()}}
    c.audit(u,"payroll.adjustment_created","payroll_adjustment",UUID.fromString(dto.id));c.commit();dto
   }catch(t:Throwable){c.rollback();throw t}}
   call.respond(HttpStatusCode.Created,row)
  }catch(e:AuthFailure){call.fail(e)} }
 }
 route("/payroll") {
  get { try {
   val u=call.authorize(auth,"payroll.view");val org=UUID.fromString(u.organizationId)
   val month=call.request.queryParameters["month"]?.let{runCatching{YearMonth.parse(it)}.getOrNull()?:throw AuthFailure(400,"month must use YYYY-MM")}
   val runs=Database.dataSource().connection.use{c->
    val ids=c.prepareStatement("SELECT id FROM payroll_runs WHERE organization_id=? AND (?::date IS NULL OR payroll_month=?) ORDER BY payroll_month DESC LIMIT 24").use{s->s.setObject(1,org);s.setObject(2,month?.atDay(1));s.setObject(3,month?.atDay(1));s.executeQuery().use{rs->buildList{while(rs.next())add(rs.getObject(1,UUID::class.java))}}}
    ids.map{c.loadRun(org,it)}
   }
   val payments=Database.dataSource().connection.use{c->c.prepareStatement("""SELECT p.* FROM payroll_payments p WHERE p.organization_id=? AND (?::date IS NULL OR date_trunc('month',p.payment_date)=?::date) ORDER BY p.payment_date DESC LIMIT 1000""").use{s->s.setObject(1,org);s.setObject(2,month?.atDay(1));s.setObject(3,month?.atDay(1));s.executeQuery().use{rs->buildList{while(rs.next())add(rs.paymentDto())}}}}
   call.respond(PayrollOverview(runs,payments))
  }catch(e:AuthFailure){call.fail(e)} }
  post("/runs") { try {
   val u=call.authorize(auth,"payroll.manage");val x=call.receive<PayrollRunRequest>();val month=runCatching{YearMonth.parse(x.payrollMonth)}.getOrNull()?:throw AuthFailure(400,"Payroll month must use YYYY-MM")
   val org=UUID.fromString(u.organizationId);val first=month.atDay(1);val last=month.atEndOfMonth();val today=LocalDate.now(ZoneId.of("Asia/Kolkata"));if(first.isAfter(today))throw AuthFailure(400,"Future payroll months cannot be calculated");val effectiveEnd=if(month==YearMonth.from(today))minOf(last,today) else last
   val result=Database.dataSource().connection.use{c->c.autoCommit=false;try{
    c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))").use{s->s.setString(1,org.toString()+month);s.execute()}
    val old=c.prepareStatement("SELECT id,status FROM payroll_runs WHERE organization_id=? AND payroll_month=? AND branch_id IS NULL FOR UPDATE").use{s->s.setObject(1,org);s.setObject(2,first);s.executeQuery().use{if(it.next())it.getObject(1,UUID::class.java) to it.getString(2) else null}}
    if(old!=null&&old.second!="DRAFT")throw AuthFailure(409,"Only draft payroll can be recalculated")
    val run=old?.first?:c.prepareStatement("INSERT INTO payroll_runs(organization_id,payroll_month,prepared_by) VALUES(?,?,?) RETURNING id").use{s->s.setObject(1,org);s.setObject(2,first);s.setObject(3,UUID.fromString(u.id));s.executeQuery().use{it.next();it.getObject(1,UUID::class.java)}}
    if(old!=null){c.prepareStatement("DELETE FROM payroll_adjustment_recoveries WHERE organization_id=? AND payroll_item_id IN (SELECT id FROM payroll_items WHERE organization_id=? AND payroll_run_id=?)").use{s->s.setObject(1,org);s.setObject(2,org);s.setObject(3,run);s.executeUpdate()};c.prepareStatement("DELETE FROM payroll_items WHERE organization_id=? AND payroll_run_id=?").use{s->s.setObject(1,org);s.setObject(2,run);s.executeUpdate()}}
    val employees=c.prepareStatement("SELECT id,full_name,role_title,monthly_salary,weekly_off_day,joining_date,exit_date FROM employees WHERE organization_id=? AND archived_at IS NULL AND COALESCE(joining_date,?)<=? AND (exit_date IS NULL OR exit_date>=?) AND (employment_status IN('ACTIVE','ON_LEAVE') OR (employment_status='EXITED' AND exit_date>=?)) ORDER BY lower(full_name)").use{s->s.setObject(1,org);s.setObject(2,effectiveEnd);s.setObject(3,effectiveEnd);s.setObject(4,first);s.setObject(5,first);s.executeQuery().use{rs->buildList{while(rs.next())add(EmployeePay(rs.getObject(1,UUID::class.java),rs.getString(2),rs.getString(3),rs.getBigDecimal(4),rs.getInt(5),rs.getObject(6,LocalDate::class.java),rs.getObject(7,LocalDate::class.java)))}}}
    var grossTotal=BigDecimal.ZERO;var deductTotal=BigDecimal.ZERO;var netTotal=BigDecimal.ZERO
    employees.forEach{e->
     val start=maxOf(first,e.joining?:first);val attendanceEnd=minOf(effectiveEnd,e.exit?:effectiveEnd);val scheduleEnd=minOf(last,e.exit?:last)
     val att=c.prepareStatement("SELECT attendance_date,status FROM attendance_entries WHERE organization_id=? AND employee_id=? AND attendance_date BETWEEN ? AND ?").use{s->s.setObject(1,org);s.setObject(2,e.id);s.setObject(3,start);s.setObject(4,scheduleEnd);s.executeQuery().use{rs->buildMap{while(rs.next())put(rs.getObject(1,LocalDate::class.java),rs.getString(2))}}}
     var work=0;var elapsedWork=0;var present=0.0;var half=0.0;var paidLeave=0.0;var unpaid=0.0;var missing=0;var d=start
     while(!d.isAfter(scheduleEnd)){val status=att[d];val scheduled=d.dayOfWeek.value != (if(e.off==0) 7 else e.off) && status!="HOLIDAY" && status!="WEEKLY_OFF";if(scheduled){work++;if(!d.isAfter(attendanceEnd)){elapsedWork++;if(status==null)missing++;when(status){"PRESENT"->present++; "HALF_DAY"->{half++;unpaid+=.5};"PAID_LEAVE"->paidLeave++;"ABSENT","UNPAID_LEAVE"->unpaid++}}};d=d.plusDays(1)}
     if(missing>0)throw AuthFailure(409,"Attendance incomplete for "+e.name+": "+missing+" scheduled workday(s) are unmarked")
     val base=e.salary.multiply(BigDecimal.valueOf((elapsedWork-unpaid).coerceAtLeast(0.0))).divide(BigDecimal.valueOf(work.coerceAtLeast(1).toLong()),2,RoundingMode.HALF_UP)
     var earnings=BigDecimal.ZERO;var deductions=BigDecimal.ZERO
     val components=c.prepareStatement("SELECT component_name,component_type,calculation_type,amount FROM employee_salary_components WHERE organization_id=? AND employee_id=? AND is_active AND effective_from<=? AND (effective_to IS NULL OR effective_to>=?)").use{s->s.setObject(1,org);s.setObject(2,e.id);s.setObject(3,last);s.setObject(4,first);s.executeQuery().use{rs->buildList{while(rs.next())add(Component(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4)))}}}
     components.forEach{v->val a=if(v.calc=="PERCENTAGE")e.salary.multiply(v.amount).divide(BigDecimal("100"),2,RoundingMode.HALF_UP) else v.amount;if(v.type=="EARNING")earnings=earnings.add(a) else deductions=deductions.add(a)}
     val adj=c.prepareStatement("SELECT adjustment_type,amount FROM payroll_adjustments WHERE organization_id=? AND employee_id=? AND adjustment_date BETWEEN ? AND ? AND status<>'VOID'").use{s->s.setObject(1,org);s.setObject(2,e.id);s.setObject(3,first);s.setObject(4,last);s.executeQuery().use{rs->buildList{while(rs.next())add(rs.getString(1) to rs.getBigDecimal(2))}}}
     adj.forEach{(t,a)->when(t){"INCENTIVE"->earnings=earnings.add(a);"BONUS"->earnings=earnings.add(a);"DEDUCTION"->deductions=deductions.add(a)}}
     val gross=base.add(earnings);val available=gross.subtract(deductions).coerceAtLeast(BigDecimal.ZERO)
     val advances=c.prepareStatement("SELECT id,amount,recovered_amount FROM payroll_adjustments WHERE organization_id=? AND employee_id=? AND adjustment_type='ADVANCE' AND adjustment_date<=? AND status IN('OPEN','PARTIALLY_SETTLED') ORDER BY adjustment_date,id FOR UPDATE").use{s->s.setObject(1,org);s.setObject(2,e.id);s.setObject(3,last);s.executeQuery().use{rs->buildList{while(rs.next())add(Advance(rs.getObject(1,UUID::class.java),rs.getBigDecimal(2),rs.getBigDecimal(3)))}}}
     var recovery=BigDecimal.ZERO;advances.forEach{a->val remaining=a.amount.subtract(a.recovered).coerceAtLeast(BigDecimal.ZERO);recovery=recovery.add(remaining.min(available.subtract(recovery).coerceAtLeast(BigDecimal.ZERO)))}
     val net=available.subtract(recovery).coerceAtLeast(BigDecimal.ZERO)
     val item=c.prepareStatement("""INSERT INTO payroll_items(organization_id,payroll_run_id,employee_id,employee_name_snapshot,role_snapshot,monthly_salary_snapshot,working_days,present_days,half_days,paid_leave_days,unpaid_days,gross_pay,advance_recovery,net_pay)
      VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id""").use{s->s.setObject(1,org);s.setObject(2,run);s.setObject(3,e.id);s.setString(4,e.name);s.setString(5,e.role);s.setBigDecimal(6,e.salary);s.setBigDecimal(7,BigDecimal.valueOf(work.toLong()));s.setBigDecimal(8,BigDecimal.valueOf(present));s.setBigDecimal(9,BigDecimal.valueOf(half));s.setBigDecimal(10,BigDecimal.valueOf(paidLeave));s.setBigDecimal(11,BigDecimal.valueOf(unpaid));s.setBigDecimal(12,gross);s.setBigDecimal(13,recovery);s.setBigDecimal(14,net);s.executeQuery().use{it.next();it.getObject(1,UUID::class.java)}}
     var left=recovery;advances.forEach{a->val take=a.amount.subtract(a.recovered).coerceAtLeast(BigDecimal.ZERO).min(left);if(take>BigDecimal.ZERO){c.prepareStatement("INSERT INTO payroll_adjustment_recoveries(organization_id,adjustment_id,payroll_item_id,amount) VALUES(?,?,?,?)").use{s->s.setObject(1,org);s.setObject(2,a.id);s.setObject(3,item);s.setBigDecimal(4,take);s.executeUpdate()};left=left.subtract(take)}}
     grossTotal=grossTotal.add(gross);deductTotal=deductTotal.add(deductions).add(recovery);netTotal=netTotal.add(net)
    }
    c.prepareStatement("UPDATE payroll_runs SET employee_count=?,gross_total=?,deduction_total=?,net_total=?,updated_at=now() WHERE organization_id=? AND id=?").use{s->s.setInt(1,employees.size);s.setBigDecimal(2,grossTotal);s.setBigDecimal(3,deductTotal);s.setBigDecimal(4,netTotal);s.setObject(5,org);s.setObject(6,run);s.executeUpdate()}
    c.audit(u,"payroll.draft_calculated","payroll_run",run);c.commit();c.loadRun(org,run)
   }catch(t:Throwable){c.rollback();throw t}}
   call.respond(HttpStatusCode.Created,result)
  }catch(e:AuthFailure){call.fail(e)} }
  post("/runs/{id}/approve") { try {
   val u=call.authorize(auth,"payroll.approve");val id=call.parameters["id"]?.let{runCatching{UUID.fromString(it)}.getOrNull()}?:throw AuthFailure(400,"Payroll run id is invalid");val org=UUID.fromString(u.organizationId)
   val result=Database.dataSource().connection.use{c->c.autoCommit=false;try{
    val status=c.prepareStatement("SELECT status FROM payroll_runs WHERE organization_id=? AND id=? FOR UPDATE").use{s->s.setObject(1,org);s.setObject(2,id);s.executeQuery().use{if(it.next())it.getString(1)else null}}?:throw AuthFailure(404,"Payroll run not found")
    if(status!="DRAFT")throw AuthFailure(409,"Only draft payroll can be approved")
    c.prepareStatement("""UPDATE payroll_adjustments a SET recovered_amount=a.recovered_amount+x.amount,status=CASE WHEN a.recovered_amount+x.amount>=a.amount THEN 'SETTLED' ELSE 'PARTIALLY_SETTLED' END,updated_at=now()
     FROM payroll_adjustment_recoveries x JOIN payroll_items i ON i.id=x.payroll_item_id WHERE x.adjustment_id=a.id AND i.organization_id=? AND i.payroll_run_id=? AND a.adjustment_type='ADVANCE'""").use{s->s.setObject(1,org);s.setObject(2,id);s.executeUpdate()}
    c.prepareStatement("UPDATE payroll_runs SET status='APPROVED',approved_by=?,approved_at=now(),updated_at=now() WHERE organization_id=? AND id=?").use{s->s.setObject(1,UUID.fromString(u.id));s.setObject(2,org);s.setObject(3,id);s.executeUpdate()}
    c.audit(u,"payroll.approved","payroll_run",id);c.commit();c.loadRun(org,id)
   }catch(t:Throwable){c.rollback();throw t}}
   call.respond(result)
  }catch(e:AuthFailure){call.fail(e)} }
  post("/items/{id}/payments") { try {
   val u=call.authorize(auth,"payroll.pay");val id=call.parameters["id"]?.let{runCatching{UUID.fromString(it)}.getOrNull()}?:throw AuthFailure(400,"Payroll item id is invalid")
   val x=call.receive<PaymentInput>();val amt=amount(x.amount,"Payment amount");if(amt<=BigDecimal.ZERO)throw AuthFailure(400,"Payment must be greater than zero")
   if(x.paymentMethod !in paymentStates)throw AuthFailure(400,"Payment method is invalid");val d=date(x.paymentDate,"Payment date");val key=runCatching{UUID.fromString(x.idempotencyKey)}.getOrNull()?:throw AuthFailure(400,"Idempotency key must be a UUID");val org=UUID.fromString(u.organizationId)
   val result=Database.dataSource().connection.use{c->c.autoCommit=false;try{
    val item=c.prepareStatement("SELECT i.employee_id,i.net_pay,i.paid_amount,r.status,i.payroll_run_id FROM payroll_items i JOIN payroll_runs r ON r.id=i.payroll_run_id AND r.organization_id=i.organization_id WHERE i.organization_id=? AND i.id=? FOR UPDATE OF i,r").use{s->s.setObject(1,org);s.setObject(2,id);s.executeQuery().use{if(it.next())PayTarget(it.getObject(1,UUID::class.java),it.getBigDecimal(2),it.getBigDecimal(3),it.getString(4),it.getObject(5,UUID::class.java))else null}}?:throw AuthFailure(404,"Payroll item not found")
    if(item.status !in setOf("APPROVED","PARTIALLY_PAID"))throw AuthFailure(409,"Payroll must be approved before payment")
    val existing=c.prepareStatement("SELECT id,payroll_item_id,employee_id,payment_date,amount,payment_method,reference_number,note FROM payroll_payments WHERE organization_id=? AND idempotency_key=?").use{s->s.setObject(1,org);s.setObject(2,key);s.executeQuery().use{if(it.next())it.paymentDto()else null}}
    if(existing!=null){if(existing.payrollItemId!=id.toString())throw AuthFailure(409,"Idempotency key already used");c.commit();existing}
    else{
     if(item.paid.add(amt)>item.net)throw AuthFailure(400,"Payment exceeds outstanding salary")
     val dto=c.prepareStatement("INSERT INTO payroll_payments(organization_id,payroll_item_id,employee_id,payment_date,amount,payment_method,reference_number,note,idempotency_key,recorded_by) VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING *").use{s->s.setObject(1,org);s.setObject(2,id);s.setObject(3,item.employee);s.setObject(4,d);s.setBigDecimal(5,amt);s.setString(6,x.paymentMethod);s.setString(7,x.referenceNumber?.trim());s.setString(8,x.note?.trim());s.setObject(9,key);s.setObject(10,UUID.fromString(u.id));s.executeQuery().use{it.next();it.paymentDto()}}
     val paid=item.paid.add(amt);c.prepareStatement("UPDATE payroll_items SET paid_amount=? WHERE organization_id=? AND id=?").use{s->s.setBigDecimal(1,paid);s.setObject(2,org);s.setObject(3,id);s.executeUpdate()}
     c.prepareStatement("""UPDATE payroll_runs SET status=CASE WHEN NOT EXISTS(SELECT 1 FROM payroll_items WHERE organization_id=? AND payroll_run_id=? AND paid_amount<net_pay) THEN 'PAID' WHEN EXISTS(SELECT 1 FROM payroll_items WHERE organization_id=? AND payroll_run_id=? AND paid_amount>0) THEN 'PARTIALLY_PAID' ELSE status END,updated_at=now() WHERE organization_id=? AND id=?""").use{s->s.setObject(1,org);s.setObject(2,item.run);s.setObject(3,org);s.setObject(4,item.run);s.setObject(5,org);s.setObject(6,item.run);s.executeUpdate()}
     c.audit(u,"payroll.payment_recorded","payroll_item",id);c.commit();dto
    }
   }catch(t:Throwable){c.rollback();throw t}}
   call.respond(HttpStatusCode.Created,result)
  }catch(e:AuthFailure){call.fail(e)} }
 }
}

private data class EmployeePay(val id:UUID,val name:String,val role:String,val salary:BigDecimal,val off:Int,val joining:LocalDate?,val exit:LocalDate?)
private data class Component(val name:String,val type:String,val calc:String,val amount:BigDecimal)
private data class Advance(val id:UUID,val amount:BigDecimal,val recovered:BigDecimal)
private data class PayTarget(val employee:UUID,val net:BigDecimal,val paid:BigDecimal,val status:String,val run:UUID)
private fun java.sql.ResultSet.employeeDto()=EmployeeDto(getObject("id",UUID::class.java).toString(),getString("employee_code"),getString("full_name"),getString("role_title"),getString("phone"),getString("email"),getString("employment_type"),getString("employment_status"),getObject("joining_date",LocalDate::class.java)?.toString(),getObject("exit_date",LocalDate::class.java)?.toString(),getBigDecimal("monthly_salary").toDouble(),getString("pay_basis"),getInt("weekly_off_day"),getObject("branch_id",UUID::class.java)?.toString(),getString("notes"),getTimestamp("created_at").toInstant().toString(),getTimestamp("updated_at").toInstant().toString())
private fun java.sql.ResultSet.attendanceDto()=AttendanceDto(getObject("id",UUID::class.java).toString(),getObject("employee_id",UUID::class.java).toString(),getObject("attendance_date",LocalDate::class.java).toString(),getString("status"),getTimestamp("check_in_at")?.toInstant()?.toString(),getTimestamp("check_out_at")?.toInstant()?.toString(),getBigDecimal("regular_hours").toDouble(),getBigDecimal("overtime_hours").toDouble(),getString("note"),getTimestamp("updated_at").toInstant().toString())
private fun java.sql.ResultSet.salaryComponentDto()=SalaryComponentDto(getObject("id",UUID::class.java).toString(),getObject("employee_id",UUID::class.java).toString(),getString("component_code"),getString("component_name"),getString("component_type"),getString("calculation_type"),getBigDecimal("amount").toDouble(),getBoolean("is_active"),getObject("effective_from",LocalDate::class.java).toString(),getObject("effective_to",LocalDate::class.java)?.toString())
private fun java.sql.ResultSet.adjustmentDto()=AdjustmentDto(getObject("id",UUID::class.java).toString(),getObject("employee_id",UUID::class.java).toString(),getObject("adjustment_date",LocalDate::class.java).toString(),getString("adjustment_type"),getBigDecimal("amount").toDouble(),getBigDecimal("recovered_amount").toDouble(),getBigDecimal("amount").subtract(getBigDecimal("recovered_amount")).toDouble(),getString("status"),getString("reason"))
private fun java.sql.ResultSet.paymentDto()=PaymentDto(getObject("id",UUID::class.java).toString(),getObject("payroll_item_id",UUID::class.java).toString(),getObject("employee_id",UUID::class.java).toString(),getObject("payment_date",LocalDate::class.java).toString(),getBigDecimal("amount").toDouble(),getString("payment_method"),getString("reference_number"),getString("note"))
private data class PayrollItem(val id:String,val employee:String,val name:String,val role:String,val salary:Double,val working:Double,val present:Double,val half:Double,val paidLeave:Double,val unpaid:Double,val gross:Double,val recovery:Double,val net:Double,val paid:Double)
private fun Connection.loadRun(org:UUID,id:UUID):PayrollRunDto{
 val h=prepareStatement("SELECT * FROM payroll_runs WHERE organization_id=? AND id=?").use{s->s.setObject(1,org);s.setObject(2,id);s.executeQuery().use{if(!it.next())throw AuthFailure(404,"Payroll run not found");listOf(it.getObject("id",UUID::class.java).toString(),it.getObject("payroll_month",LocalDate::class.java).toString(),it.getString("status"),it.getInt("employee_count"),it.getBigDecimal("gross_total").toDouble(),it.getBigDecimal("deduction_total").toDouble(),it.getBigDecimal("net_total").toDouble())}}
 val items=prepareStatement("SELECT * FROM payroll_items WHERE organization_id=? AND payroll_run_id=? ORDER BY employee_name_snapshot").use{s->s.setObject(1,org);s.setObject(2,id);s.executeQuery().use{rs->buildList{while(rs.next()){val net=rs.getBigDecimal("net_pay").toDouble();val paid=rs.getBigDecimal("paid_amount").toDouble();add(PayrollItemDto(rs.getObject("id",UUID::class.java).toString(),rs.getObject("employee_id",UUID::class.java).toString(),rs.getString("employee_name_snapshot"),rs.getString("role_snapshot"),rs.getBigDecimal("monthly_salary_snapshot").toDouble(),rs.getBigDecimal("working_days").toDouble(),rs.getBigDecimal("present_days").toDouble(),rs.getBigDecimal("half_days").toDouble(),rs.getBigDecimal("paid_leave_days").toDouble(),rs.getBigDecimal("unpaid_days").toDouble(),rs.getBigDecimal("gross_pay").toDouble(),(rs.getBigDecimal("gross_pay").subtract(rs.getBigDecimal("advance_recovery")).subtract(rs.getBigDecimal("net_pay"))).coerceAtLeast(BigDecimal.ZERO).toDouble(),rs.getBigDecimal("advance_recovery").toDouble(),net,paid,(net-paid).coerceAtLeast(0.0)))}}}}
 return PayrollRunDto(h[0] as String,h[1] as String,h[2] as String,h[3] as Int,h[4] as Double,h[5] as Double,h[6] as Double,items)
}
