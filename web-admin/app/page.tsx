const modules = [
  { title: "Enquiries", description: "Customer requirements and follow-ups", icon: "01" },
  { title: "Projects", description: "Rooms, furniture schedules and milestones", icon: "02" },
  { title: "Production", description: "Workshop stages, jobs and assignments", icon: "03" },
  { title: "Inventory", description: "Materials, stock and purchase requests", icon: "04" },
  { title: "Workforce", description: "Attendance, tasks and work logs", icon: "05" },
  { title: "Approvals", description: "Quotations, purchases and expenses", icon: "06" },
];

export default function HomePage() {
  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="Workshop ERP home">
          <span className="brand-mark">A</span>
          <span><strong>WORKSHOP</strong><small>MANAGEMENT ERP</small></span>
        </a>
        <div className="workspace-label">WORKSPACE</div>
        <nav className="nav-list" aria-label="Main navigation">
          <a className="nav-item active" href="#"><span>▦</span> Overview</a>
          <a className="nav-item" href="#"><span>◷</span> Enquiries</a>
          <a className="nav-item" href="#"><span>▤</span> Projects</a>
          <a className="nav-item" href="#"><span>⚒</span> Production</a>
          <a className="nav-item" href="#"><span>▧</span> Inventory</a>
          <a className="nav-item" href="#"><span>♙</span> Workforce</a>
          <a className="nav-item" href="#"><span>✓</span> Approvals</a>
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
                <span className="module-arrow">↗</span>
              </article>
            ))}
          </div>

          <section className="setup-card">
            <div className="setup-icon">✓</div>
            <div><h2>Foundation workspace created</h2><p>Authentication, organization setup and live operational data will be connected in the next implementation steps. This screen currently uses no fabricated business figures.</p></div>
          </section>
          <footer>Furniture Workshop ERP <span>·</span> Offline-first operations</footer>
        </div>
      </section>
    </main>
  );
}
