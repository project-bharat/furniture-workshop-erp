import {
  ArrowUpRight,
  CheckCircle2,
  ClipboardCheck,
  FolderKanban,
  Hammer,
  LayoutDashboard,
  MessageSquare,
  Package,
  Users,
} from "lucide-react";

const modules = [
  { title: "Enquiries", description: "Customer requirements and follow-ups", icon: <MessageSquare size={17} /> },
  { title: "Projects", description: "Rooms, furniture schedules and milestones", icon: <FolderKanban size={17} /> },
  { title: "Production", description: "Workshop stages, jobs and assignments", icon: <Hammer size={17} /> },
  { title: "Inventory", description: "Materials, stock and purchase requests", icon: <Package size={17} /> },
  { title: "Workforce", description: "Attendance, tasks and work logs", icon: <Users size={17} /> },
  { title: "Approvals", description: "Quotations, purchases and expenses", icon: <ClipboardCheck size={17} /> },
];

export default function HomePage() {
  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="Aakruti Workshop ERP home">
          <span className="brand-mark">A</span>
          <span><strong>AAKRUTI</strong><small>FURNITURE WORKSHOP ERP</small></span>
        </a>
        <div className="workspace-label">WORKSPACE</div>
        <nav className="nav-list" aria-label="Main navigation">
          <a className="nav-item active" href="#"><LayoutDashboard size={16} /> Overview</a>
          <a className="nav-item" href="#"><MessageSquare size={16} /> Enquiries</a>
          <a className="nav-item" href="#"><FolderKanban size={16} /> Projects</a>
          <a className="nav-item" href="#"><Hammer size={16} /> Production</a>
          <a className="nav-item" href="#"><Package size={16} /> Inventory</a>
          <a className="nav-item" href="#"><Users size={16} /> Workforce</a>
          <a className="nav-item" href="#"><ClipboardCheck size={16} /> Approvals</a>
        </nav>
        <div className="sidebar-bottom">
          <span className="connection-dot" />
          <span>Local workspace preview</span>
        </div>
      </aside>

      <section className="main-area">
        <header className="topbar">
          <div className="breadcrumb">Workspace <span>/</span> Overview</div>
          <div className="profile">
            <span className="avatar">A</span>
            <span><strong>Administrator</strong><small>Owner access</small></span>
          </div>
        </header>

        <div className="content">
          <div className="page-heading">
            <div>
              <p className="eyebrow">WORKSHOP CONTROL CENTRE</p>
              <h1>Overview</h1>
              <p className="subheading">A clear view of your workshop operations.</p>
            </div>
            <span className="setup-pill">SETUP IN PROGRESS</span>
          </div>

          <div className="metric-grid" aria-label="Operational summary">
            {["Active projects", "Open enquiries", "Jobs in production", "Pending approvals"].map((label) => (
              <article className="metric-card" key={label}>
                <span>{label}</span><strong>—</strong><small>Connect your workspace to load data</small>
              </article>
            ))}
          </div>

          <div className="section-heading">
            <div><h2>Operations</h2><p>Core areas of your furniture business</p></div>
          </div>
          <div className="module-grid">
            {modules.map((module) => (
              <article className="module-card" key={module.title}>
                <span className="module-number">{module.icon}</span>
                <div><h3>{module.title}</h3><p>{module.description}</p></div>
                <ArrowUpRight className="module-arrow" size={15} />
              </article>
            ))}
          </div>

          <section className="setup-card">
            <div className="setup-icon"><CheckCircle2 size={16} /></div>
            <div><h2>Foundation workspace created</h2><p>Authentication, organization setup and live operational data will be connected in the next implementation steps. This screen currently uses no fabricated business figures.</p></div>
          </section>
          <footer>Aakruti Enterprises <span>·</span> Furniture Workshop ERP <span>·</span> Offline-first operations</footer>
        </div>
      </section>
    </main>
  );
}
