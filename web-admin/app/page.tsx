"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import {
  Activity, ArrowDownToLine, Banknote, CalendarDays, Check, ChevronRight, ClipboardCheck,
  Clock3, CreditCard, FileText, LayoutDashboard, LogOut, Plus, RefreshCw, Search,
  ShieldCheck, Users, Wallet, X,
} from "lucide-react";

type User = { id:string; name:string; email:string|null; organizationName:string; role:string; permissions:string[] };
type Employee = { id:string; employeeCode:string|null; fullName:string; roleTitle:string; phone:string|null; email:string|null; employmentType:string; employmentStatus:string; joiningDate:string|null; exitDate:string|null; monthlySalary:number; weeklyOffDay:number; notes:string|null };
type Attendance = { id:string; employeeId:string; attendanceDate:string; status:string; checkInAt:string|null; checkOutAt:string|null; regularHours:number; overtimeHours:number; note:string|null };
type PayrollItem = { id:string; employeeId:string; employeeName:string; roleTitle:string; monthlySalary:number; workingDays:number; presentDays:number; halfDays:number; paidLeaveDays:number; unpaidDays:number; grossPay:number; advanceRecovery:number; netPay:number; paidAmount:number; balance:number };
type PayrollRun = { id:string; payrollMonth:string; status:string; employeeCount:number; grossTotal:number; deductionTotal:number; netTotal:number; items:PayrollItem[] };
type Payment = { id:string; payrollItemId:string; employeeId:string; paymentDate:string; amount:number; paymentMethod:string; referenceNumber:string|null; note:string|null };
type PayrollOverview = { runs:PayrollRun[]; payments:Payment[] };
type Tab = "Overview"|"Workforce"|"Attendance"|"Payroll";
const money=(v:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",maximumFractionDigits:0}).format(v||0);
const today=()=>new Date().toLocaleDateString("en-CA");
const monthNow=()=>today().slice(0,7);
const monthEnd=(m:string)=>{const [y,mm]=m.split("-").map(Number);return new Date(y,mm,0).toISOString().slice(0,10);};
const statusLabel:Record<string,string>={PRESENT:"Present",ABSENT:"Absent",HALF_DAY:"Half day",PAID_LEAVE:"Paid leave",UNPAID_LEAVE:"Unpaid leave",HOLIDAY:"Holiday",WEEKLY_OFF:"Weekly off"};
const attendanceOptions=["PRESENT","ABSENT","HALF_DAY","PAID_LEAVE","UNPAID_LEAVE","HOLIDAY","WEEKLY_OFF"];

async function api<T>(path:string,init?:RequestInit):Promise<T>{
 const response=await fetch("/api/workshop/"+path,{...init,headers:{"content-type":"application/json",...(init?.headers||{})},cache:"no-store"});
 const data=await response.json().catch(()=>null);
 if(!response.ok)throw new Error(data?.error||"Request failed ("+response.status+")");
 return data as T;
}

export default function HomePage(){
 const [user,setUser]=useState<User|null>(null);
 const [tab,setTab]=useState<Tab>("Overview");
 const [loading,setLoading]=useState(true);
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState("");
 const [message,setMessage]=useState("");
 const [email,setEmail]=useState("");
 const [password,setPassword]=useState("");
 const [employees,setEmployees]=useState<Employee[]>([]);
 const [attendance,setAttendance]=useState<Attendance[]>([]);
 const [payroll,setPayroll]=useState<PayrollOverview>({runs:[],payments:[]});
 const [month,setMonth]=useState(monthNow());
 const [day,setDay]=useState(today());
 const [statuses,setStatuses]=useState<Record<string,string>>({});
 const [notes,setNotes]=useState<Record<string,string>>({});
 const [search,setSearch]=useState("");
 const [showEmployee,setShowEmployee]=useState(false);
 const [showAdjustment,setShowAdjustment]=useState<string|null>(null);
 const [showPayment,setShowPayment]=useState<PayrollItem|null>(null);
 const [employeeForm,setEmployeeForm]=useState({fullName:"",roleTitle:"Carpenter",employeeCode:"",phone:"",monthlySalary:"",employmentType:"FULL_TIME",joiningDate:today(),weeklyOffDay:"0"});
 const [adjustmentForm,setAdjustmentForm]=useState({type:"ADVANCE",amount:"",reason:""});
 const [paymentForm,setPaymentForm]=useState({amount:"",paymentMethod:"UPI",referenceNumber:"",note:""});
 const [expandedEmployee,setExpandedEmployee]=useState<string|null>(null);

 const loadData=useCallback(async()=>{
  const [people,days,pay]=await Promise.all([
   api<Employee[]>("employees"),
   api<Attendance[]>("attendance?from="+month+"-01&to="+monthEnd(month)),
   api<PayrollOverview>("payroll?month="+month),
  ]);
  setEmployees(people);setAttendance(days);setPayroll(pay);
 },[month]);

 const checkSession=useCallback(async()=>{
  try{const response=await fetch("/api/session",{cache:"no-store"});if(!response.ok)throw new Error("Sign in required");const u=await response.json() as User;setUser(u);try{await loadData();}catch(e){setError(e instanceof Error?e.message:"Unable to load workspace data");}}
  catch{setUser(null);}
  finally{setLoading(false);}
 },[loadData]);

 useEffect(()=>{void checkSession();},[checkSession]);
 useEffect(()=>{
  const map:Record<string,string>={};const memo:Record<string,string>={};
  attendance.filter(a=>a.attendanceDate===day).forEach(a=>{map[a.employeeId]=a.status;memo[a.employeeId]=a.note||"";});
  setStatuses(map);setNotes(memo);
 },[attendance,day]);

 const activeRun=useMemo(()=>payroll.runs.find(r=>r.payrollMonth===month+"-01")||payroll.runs.find(r=>r.payrollMonth.startsWith(month))||null,[payroll.runs,month]);
 const filteredEmployees=useMemo(()=>employees.filter(e=>(e.fullName+" "+e.roleTitle+" "+(e.employeeCode||"")).toLowerCase().includes(search.toLowerCase())),[employees,search]);
 const paidThisMonth=activeRun?.items.reduce((sum,i)=>sum+i.paidAmount,0)||0;
 const balanceThisMonth=activeRun?.items.reduce((sum,i)=>sum+i.balance,0)||0;
 const can=(permission:string)=>user?.permissions.includes(permission)||false;

 async function login(e:FormEvent){e.preventDefault();setBusy(true);setError("");try{const response=await fetch("/api/session",{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify({email,password})});const result=await response.json();if(!response.ok)throw new Error(result?.error||"Sign in failed");setUser(result.user);setPassword("");await loadData();}catch(err){setError(err instanceof Error?err.message:"Sign in failed");}finally{setBusy(false);}}
 async function logout(){await fetch("/api/session",{method:"DELETE"});setUser(null);setEmployees([]);setAttendance([]);setPayroll({runs:[],payments:[]});}
 async function refresh(){setBusy(true);setError("");try{await loadData();setMessage("Data refreshed");}catch(e){setError(e instanceof Error?e.message:"Refresh failed");}finally{setBusy(false);}}
 async function createEmployee(e:React.FormEvent){e.preventDefault();setBusy(true);setError("");try{
  await api<Employee>("employees",{method:"POST",body:JSON.stringify({...employeeForm,monthlySalary:Number(employeeForm.monthlySalary),weeklyOffDay:Number(employeeForm.weeklyOffDay),email:null,exitDate:null,branchId:null,notes:null})});
  setShowEmployee(false);setEmployeeForm({fullName:"",roleTitle:"Carpenter",employeeCode:"",phone:"",monthlySalary:"",employmentType:"FULL_TIME",joiningDate:today(),weeklyOffDay:"0"});await loadData();setMessage("Employee created");
 }catch(e){setError(e instanceof Error?e.message:"Could not create employee");}finally{setBusy(false);}}
 async function archiveEmployee(id:string){if(!confirm("Archive this employee? Their attendance and payroll history will remain."))return;setBusy(true);try{await api("employees/"+id,{method:"DELETE"});await loadData();setMessage("Employee archived");}catch(e){setError(e instanceof Error?e.message:"Archive failed");}finally{setBusy(false);}}
 async function saveAttendance(){const entries=employees.filter(e=>statuses[e.id]).map(e=>({employeeId:e.id,attendanceDate:day,status:statuses[e.id],regularHours:statuses[e.id]==="PRESENT"?8:statuses[e.id]==="HALF_DAY"?4:0,overtimeHours:0,note:notes[e.id]||null}));if(!entries.length){setError("Select attendance status for at least one employee.");return;}setBusy(true);setError("");try{await api<Attendance[]>("attendance/batch",{method:"POST",body:JSON.stringify({entries})});await loadData();setMessage("Attendance saved for "+entries.length+" employees");}catch(e){setError(e instanceof Error?e.message:"Attendance save failed");}finally{setBusy(false);}}
 async function calculatePayroll(){setBusy(true);setError("");try{await api<PayrollRun>("payroll/runs",{method:"POST",body:JSON.stringify({payrollMonth:month})});await loadData();setMessage("Draft payroll calculated. Review before approval.");}catch(e){setError(e instanceof Error?e.message:"Payroll calculation failed");}finally{setBusy(false);}}
 async function approvePayroll(id:string){if(!confirm("Approve and lock this payroll run? Approved salary figures cannot be recalculated."))return;setBusy(true);setError("");try{await api<PayrollRun>("payroll/runs/"+id+"/approve",{method:"POST",body:JSON.stringify({})});await loadData();setMessage("Payroll approved and locked");}catch(e){setError(e instanceof Error?e.message:"Approval failed");}finally{setBusy(false);}}
 async function saveAdjustment(e:React.FormEvent){e.preventDefault();if(!showAdjustment)return;setBusy(true);setError("");try{await api("payroll/adjustments",{method:"POST",body:JSON.stringify({employeeId:showAdjustment,adjustmentDate:today(),type:adjustmentForm.type,amount:Number(adjustmentForm.amount),reason:adjustmentForm.reason})});setShowAdjustment(null);setAdjustmentForm({type:"ADVANCE",amount:"",reason:""});await loadData();setMessage("Payroll adjustment saved");}catch(e){setError(e instanceof Error?e.message:"Adjustment failed");}finally{setBusy(false);}}
 async function savePayment(e:React.FormEvent){e.preventDefault();if(!showPayment)return;setBusy(true);setError("");try{await api("payroll/items/"+showPayment.id+"/payments",{method:"POST",body:JSON.stringify({amount:Number(paymentForm.amount),paymentDate:today(),paymentMethod:paymentForm.paymentMethod,referenceNumber:paymentForm.referenceNumber,note:paymentForm.note,idempotencyKey:crypto.randomUUID()})});setShowPayment(null);await loadData();setMessage("Salary payment recorded");}catch(e){setError(e instanceof Error?e.message:"Payment failed");}finally{setBusy(false);}}

 if(loading)return <main className="loading-screen">Loading secure workspace…</main>;
 if(!user)return <main className="login-shell"><form className="login-card" onSubmit={login}>
  <div className="login-brand"><span className="brand-mark">A</span><div><strong>AAKRUTI</strong><small>FURNITURE WORKSHOP ERP</small></div></div>
  <p className="eyebrow">SECURE WORKSPACE</p><h1>Sign in</h1><p className="subheading">Use your workshop owner or staff account.</p>
  <label>Email address<input type="email" autoComplete="username" required value={email} onChange={e=>setEmail(e.target.value)}/></label>
  <label>Password<input type="password" autoComplete="current-password" required value={password} onChange={e=>setPassword(e.target.value)}/></label>
  {error&&<div className="alert error">{error}</div>}<button className="primary full" disabled={busy}>{busy?"Signing in…":"Sign in securely"}</button>
  <small className="muted">Your session uses server-managed HttpOnly cookies. The API URL is configured by the deployment environment.</small>
 </form></main>;

 return <main className="app-shell">
  <aside className="sidebar">
   <a className="brand" href="#"><span className="brand-mark">A</span><span><strong>AAKRUTI</strong><small>FURNITURE WORKSHOP ERP</small></span></a>
   <div className="workspace-label">WORKSPACE</div>
   <nav className="nav-list" aria-label="Main navigation">
    {([["Overview",LayoutDashboard],["Workforce",Users],["Attendance",CalendarDays],["Payroll",Wallet]] as const).map(([name,Icon])=><button key={name} className={"nav-item "+(tab===name?"active":"")} onClick={()=>setTab(name)}><Icon size={16}/>{name}</button>)}
   </nav>
   <div className="sidebar-bottom"><span className="connection-dot"/><span>Secure API connection</span></div>
  </aside>
  <section className="main-area">
   <header className="topbar"><div className="breadcrumb">Workspace <span>/</span> {tab}</div><div className="profile"><span className="avatar">{user.name.slice(0,1).toUpperCase()}</span><span><strong>{user.name}</strong><small>{user.role.replaceAll("_"," ")}</small></span><button className="icon-button" onClick={refresh} aria-label="Refresh"><RefreshCw size={16}/></button><button className="icon-button" onClick={logout} aria-label="Sign out"><LogOut size={16}/></button></div></header>
   <div className="content">
    <div className="page-heading"><div><p className="eyebrow">WORKSHOP CONTROL CENTRE</p><h1>{tab}</h1><p className="subheading">{user.organizationName} · Live organization data</p></div><button className="secondary" onClick={refresh} disabled={busy}><RefreshCw size={14}/> Refresh</button></div>
    {(error||message)&&<div className={"alert "+(error?"error":"success")}>{error||message}<button onClick={()=>{setError("");setMessage("");}}><X size={14}/></button></div>}
    {tab==="Overview"&&<>
     <div className="metric-grid">
      <Metric label="Active employees" value={String(employees.filter(e=>e.employmentStatus==="ACTIVE").length)} detail="Current workforce"/>
      <Metric label="Payroll gross" value={money(activeRun?.grossTotal||0)} detail={activeRun?activeRun.status:"No payroll run for selected month"}/>
      <Metric label="Salary paid" value={money(paidThisMonth)} detail="Payments recorded"/>
      <Metric label="Salary outstanding" value={money(balanceThisMonth)} detail="Approved payroll balance"/>
     </div>
     <div className="section-heading"><div><h2>Monthly control</h2><p>Attendance and payroll status for the selected month</p></div><input className="month-input" type="month" value={month} onChange={e=>setMonth(e.target.value)}/></div>
     <div className="module-grid">
      <Module title="Workforce" text="Employee directory, employment details and salary basis" action={()=>setTab("Workforce")} icon={<Users size={18}/>}/>
      <Module title="Attendance" text="Daily attendance, leave and working-hour records" action={()=>setTab("Attendance")} icon={<CalendarDays size={18}/>}/>
      <Module title="Payroll" text="Monthly salary runs, adjustments and payment ledger" action={()=>setTab("Payroll")} icon={<Wallet size={18}/>}/>
     </div>
    </>}
    {tab==="Workforce"&&<section>
     <div className="section-heading"><div><h2>Employee directory</h2><p>{employees.length} employee records · salary and employment details</p></div><div className="toolbar"><div className="search-field"><Search size={15}/><input placeholder="Search employee" value={search} onChange={e=>setSearch(e.target.value)}/></div>{can("workforce.manage")&&<button className="primary" onClick={()=>setShowEmployee(true)}><Plus size={15}/> Add employee</button>}</div></div>
     <div className="table-wrap"><table><thead><tr><th>Employee</th><th>Role</th><th>Employment</th><th>Monthly salary</th><th>Joined</th><th/></tr></thead><tbody>{filteredEmployees.map(e=><tr key={e.id}><td><strong>{e.fullName}</strong><small>{e.employeeCode||e.phone||"—"}</small></td><td>{e.roleTitle}</td><td><span className={"status "+e.employmentStatus.toLowerCase()}>{e.employmentStatus.replaceAll("_"," ")}</span></td><td>{money(e.monthlySalary)}</td><td>{e.joiningDate||"—"}</td><td><button className="text-action" onClick={()=>setExpandedEmployee(expandedEmployee===e.id?null:e.id)}>{expandedEmployee===e.id?"Close":"Details"}</button></td></tr>)}{filteredEmployees.length===0&&<tr><td colSpan={6} className="empty">No employees found.</td></tr>}</tbody></table></div>
     {expandedEmployee&&(()=>{const e=employees.find(x=>x.id===expandedEmployee);if(!e)return null;return <div className="detail-panel"><div><strong>{e.fullName}</strong><p>{e.roleTitle} · {e.employmentType.replaceAll("_"," ")}</p><p>{e.phone||"No phone"} · Weekly off: {["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"][e.weeklyOffDay]}</p><p>Monthly salary: <b>{money(e.monthlySalary)}</b></p><p>{e.notes||"No employee notes"}</p></div>{can("workforce.manage")&&<button className="danger-outline" onClick={()=>archiveEmployee(e.id)}>Archive employee</button>}</div>})()}
    </section>}
    {tab==="Attendance"&&<section>
     <div className="section-heading"><div><h2>Daily attendance register</h2><p>Unmarked employees are not included when saving.</p></div><input type="date" className="date-input" value={day} onChange={e=>setDay(e.target.value)}/></div>
     <div className="attendance-toolbar"><span>{employees.length} employees · {Object.keys(statuses).length} marked for this date</span><button className="primary" onClick={saveAttendance} disabled={busy||!can("attendance.manage")}><Check size={15}/> Save attendance</button></div>
     <div className="table-wrap"><table><thead><tr><th>Employee</th><th>Role</th><th>Status</th><th>Note</th><th>Hours</th></tr></thead><tbody>{employees.map(e=><tr key={e.id}><td><strong>{e.fullName}</strong><small>{e.employeeCode||"Employee"}</small></td><td>{e.roleTitle}</td><td><select className="status-select" value={statuses[e.id]||""} disabled={!can("attendance.manage")} onChange={ev=>setStatuses(s=>({...s,[e.id]:ev.target.value}))}><option value="">Not marked</option>{attendanceOptions.map(s=><option key={s} value={s}>{statusLabel[s]}</option>)}</select></td><td><input className="note-input" placeholder="Optional note" value={notes[e.id]||""} onChange={ev=>setNotes(n=>({...n,[e.id]:ev.target.value}))}/></td><td>{statuses[e.id]==="PRESENT"?"8h":statuses[e.id]==="HALF_DAY"?"4h":"—"}</td></tr>)}</tbody></table></div>
     <div className="section-heading"><div><h2>Attendance history</h2><p>Selected month · {attendance.length} entries</p></div></div>
     <div className="history-grid">{employees.map(e=>{const entries=attendance.filter(a=>a.employeeId===e.id);const present=entries.filter(a=>a.status==="PRESENT").length;const absent=entries.filter(a=>["ABSENT","UNPAID_LEAVE"].includes(a.status)).length;return <article className="history-card" key={e.id}><strong>{e.fullName}</strong><div><span>{present} present</span><span>{absent} absent</span><span>{entries.filter(a=>a.status==="HALF_DAY").length} half-day</span></div></article>})}</div>
    </section>}
    {tab==="Payroll"&&<section>
     <div className="section-heading"><div><h2>Payroll register</h2><p>Draft → review → approve → pay · approved payroll is locked</p></div><div className="toolbar"><input className="month-input" type="month" value={month} onChange={e=>setMonth(e.target.value)}/>{can("payroll.manage")&&<button className="primary" onClick={calculatePayroll} disabled={busy}><Activity size={15}/> Calculate draft</button>}</div></div>
     <div className="metric-grid three"><Metric label="Gross payroll" value={money(activeRun?.grossTotal||0)} detail={activeRun?.status||"Not calculated"}/><Metric label="Paid" value={money(paidThisMonth)} detail="Payment ledger"/><Metric label="Outstanding" value={money(balanceThisMonth)} detail="Net salary balance"/></div>
     {!activeRun?<div className="empty-card"><FileText size={24}/><strong>No payroll run for {month}</strong><p>Calculate a draft after attendance and salary adjustments are updated.</p></div>:<>
      <div className="payroll-banner"><div><span className={"status "+activeRun.status.toLowerCase()}>{activeRun.status.replaceAll("_"," ")}</span><strong>{activeRun.employeeCount} employee payslips</strong><small>Gross {money(activeRun.grossTotal)} · Deductions {money(activeRun.deductionTotal)} · Net {money(activeRun.netTotal)}</small></div>{activeRun.status==="DRAFT"&&can("payroll.approve")&&<button className="primary" onClick={()=>approvePayroll(activeRun.id)} disabled={busy}><ShieldCheck size={15}/> Approve & lock</button>}</div>
      <div className="table-wrap"><table><thead><tr><th>Employee</th><th>Attendance</th><th>Gross</th><th>Advance recovery</th><th>Net pay</th><th>Paid</th><th>Balance</th><th/></tr></thead><tbody>{activeRun.items.map(i=><tr key={i.id}><td><strong>{i.employeeName}</strong><small>{i.roleTitle}</small></td><td>{i.presentDays} present · {i.halfDays} half · {i.unpaidDays} unpaid / {i.workingDays} days</td><td>{money(i.grossPay)}</td><td>{money(i.advanceRecovery)}</td><td><strong>{money(i.netPay)}</strong></td><td>{money(i.paidAmount)}</td><td>{money(i.balance)}</td><td>{can("payroll.pay")&&["APPROVED","PARTIALLY_PAID"].includes(activeRun.status)&&i.balance>0&&<button className="text-action" onClick={()=>{setShowPayment(i);setPaymentForm({amount:String(i.balance),paymentMethod:"UPI",referenceNumber:"",note:""});}}>Record payment</button>}</td></tr>)}</tbody></table></div>
     </>}
     <div className="section-heading"><div><h2>Advances, deductions and rewards</h2><p>Record a dated adjustment against an employee.</p></div></div>
     <div className="adjustment-grid">{employees.map(e=><article className="adjustment-card" key={e.id}><div><strong>{e.fullName}</strong><small>{e.roleTitle} · {money(e.monthlySalary)}/month</small></div><button className="secondary compact" disabled={!can("payroll.manage")} onClick={()=>{setShowAdjustment(e.id);setAdjustmentForm({type:"ADVANCE",amount:"",reason:""});}}><Plus size={14}/> Add component</button></article>)}</div>
     <div className="section-heading"><div><h2>Payment history</h2><p>{payroll.payments.length} payments in {month}</p></div></div>
     <div className="table-wrap"><table><thead><tr><th>Date</th><th>Employee</th><th>Method</th><th>Reference</th><th>Amount</th></tr></thead><tbody>{payroll.payments.map(p=><tr key={p.id}><td>{p.paymentDate}</td><td>{employees.find(e=>e.id===p.employeeId)?.fullName||"Employee"}</td><td>{p.paymentMethod.replaceAll("_"," ")}</td><td>{p.referenceNumber||"—"}</td><td><strong>{money(p.amount)}</strong></td></tr>)}{payroll.payments.length===0&&<tr><td colSpan={5} className="empty">No salary payments recorded for this month.</td></tr>}</tbody></table></div>
     <div className="section-heading"><div><h2>Previous payroll runs</h2><p>Approved and historical monthly snapshots</p></div></div>
     <div className="history-grid">{payroll.runs.filter(r=>r.payrollMonth.slice(0,7)!==month).map(r=><article className="history-card" key={r.id}><strong>{r.payrollMonth.slice(0,7)}</strong><span className={"status "+r.status.toLowerCase()}>{r.status}</span><b>{money(r.netTotal)}</b></article>)}</div>
    </section>}
    <footer>Aakruti Enterprises <span>·</span> Furniture Workshop ERP <span>·</span> Organization-scoped access and audit logging</footer>
   </div>
  </section>
  {showEmployee&&<Modal title="Add employee" close={()=>setShowEmployee(false)}><form className="modal-form" onSubmit={createEmployee}>
   <label>Full name<input required value={employeeForm.fullName} onChange={e=>setEmployeeForm({...employeeForm,fullName:e.target.value})}/></label>
   <div className="form-grid"><label>Role / designation<input required value={employeeForm.roleTitle} onChange={e=>setEmployeeForm({...employeeForm,roleTitle:e.target.value})}/></label><label>Employee code<input value={employeeForm.employeeCode} onChange={e=>setEmployeeForm({...employeeForm,employeeCode:e.target.value})}/></label></div>
   <div className="form-grid"><label>Phone<input value={employeeForm.phone} onChange={e=>setEmployeeForm({...employeeForm,phone:e.target.value})}/></label><label>Monthly salary (₹)<input type="number" min="0" step="0.01" required value={employeeForm.monthlySalary} onChange={e=>setEmployeeForm({...employeeForm,monthlySalary:e.target.value})}/></label></div>
   <div className="form-grid"><label>Employment type<select value={employeeForm.employmentType} onChange={e=>setEmployeeForm({...employeeForm,employmentType:e.target.value})}><option value="FULL_TIME">Full time</option><option value="PART_TIME">Part time</option><option value="CONTRACT">Contract</option><option value="DAILY_WAGE">Daily wage</option></select></label><label>Joining date<input type="date" value={employeeForm.joiningDate} onChange={e=>setEmployeeForm({...employeeForm,joiningDate:e.target.value})}/></label></div>
   <label>Weekly off<select value={employeeForm.weeklyOffDay} onChange={e=>setEmployeeForm({...employeeForm,weeklyOffDay:e.target.value})}>{["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"].map((d,i)=><option key={d} value={i}>{d}</option>)}</select></label>
   <button className="primary full" disabled={busy}>Save employee</button>
  </form></Modal>}
  {showAdjustment&&<Modal title="Payroll adjustment" close={()=>setShowAdjustment(null)}><form className="modal-form" onSubmit={saveAdjustment}>
   <label>Component<select value={adjustmentForm.type} onChange={e=>setAdjustmentForm({...adjustmentForm,type:e.target.value})}><option value="ADVANCE">Salary advance</option><option value="DEDUCTION">Deduction</option><option value="INCENTIVE">Incentive</option><option value="BONUS">Bonus</option></select></label>
   <label>Amount (₹)<input type="number" min="0.01" step="0.01" required value={adjustmentForm.amount} onChange={e=>setAdjustmentForm({...adjustmentForm,amount:e.target.value})}/></label>
   <label>Reason<input required maxLength={1000} value={adjustmentForm.reason} onChange={e=>setAdjustmentForm({...adjustmentForm,reason:e.target.value})}/></label>
   <button className="primary full" disabled={busy}>Save adjustment</button>
  </form></Modal>}
  {showPayment&&<Modal title={"Salary payment · "+showPayment.employeeName} close={()=>setShowPayment(null)}><form className="modal-form" onSubmit={savePayment}>
   <div className="payment-due">Outstanding balance <strong>{money(showPayment.balance)}</strong></div>
   <label>Payment amount (₹)<input type="number" min="0.01" max={showPayment.balance} step="0.01" required value={paymentForm.amount} onChange={e=>setPaymentForm({...paymentForm,amount:e.target.value})}/></label>
   <label>Payment method<select value={paymentForm.paymentMethod} onChange={e=>setPaymentForm({...paymentForm,paymentMethod:e.target.value})}><option value="UPI">UPI</option><option value="BANK_TRANSFER">Bank transfer</option><option value="CASH">Cash</option><option value="CHEQUE">Cheque</option><option value="OTHER">Other</option></select></label>
   <label>UTR / transaction reference<input value={paymentForm.referenceNumber} onChange={e=>setPaymentForm({...paymentForm,referenceNumber:e.target.value})}/></label>
   <label>Note<input value={paymentForm.note} onChange={e=>setPaymentForm({...paymentForm,note:e.target.value})}/></label>
   <button className="primary full" disabled={busy}>Record payment</button>
  </form></Modal>}
 </main>;
}

function Metric({label,value,detail}:{label:string;value:string;detail:string}){return <article className="metric-card"><span>{label}</span><strong>{value}</strong><small>{detail}</small></article>}
function Module({title,text,action,icon}:{title:string;text:string;action:()=>void;icon:ReactNode}){return <button className="module-card" onClick={action}><span className="module-number">{icon}</span><div><h3>{title}</h3><p>{text}</p></div><ChevronRight className="module-arrow" size={16}/></button>}
function Modal({title,close,children}:{title:string;close:()=>void;children:ReactNode}){return <div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)close();}}><section className="modal"><header><h2>{title}</h2><button className="icon-button" type="button" onClick={close}><X size={17}/></button></header>{children}</section></div>}
