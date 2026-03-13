import { NavLink, Outlet } from 'react-router-dom';

const moduleLinks = [
  { to: '/operations/overview', label: 'Operations Dashboard' },
  { to: '/invoices', label: 'Invoice App' },
  { to: '/supply-chain/dashboard', label: 'Supply Chain App' },
];

export function HostShell() {
  return (
    <main className="app-shell">
      <div className="background-orb background-orb-left" />
      <div className="background-orb background-orb-right" />

      <header className="workspace-header panel">
        <div className="workspace-brand">
          <p className="eyebrow">Module Federation Demo</p>
          <h1>Enterprise shell orchestrating independently deployable operations modules.</h1>
          <p className="hero-copy">
            The host application owns the navigation, layout, and route composition. Each major
            frontend subsystem is served from its own Webpack dev server and loaded dynamically at
            runtime.
          </p>
        </div>

        <div className="workspace-nav-stack">
          <nav className="module-nav" aria-label="Host application navigation">
            {moduleLinks.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                className={({ isActive }) => `module-link${isActive ? ' active' : ''}`}
              >
                {link.label}
              </NavLink>
            ))}
          </nav>

          <div className="mfe-badge-row">
            <span className="status-pill neutral">host-app :3000</span>
            <span className="status-pill neutral">invoice-app :3001</span>
            <span className="status-pill neutral">supply-chain-app :3002</span>
            <span className="status-pill neutral">dashboard-app :3003</span>
          </div>
        </div>
      </header>

      <div className="mfe-shell-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Shell Responsibilities</p>
              <h3>Main navigation, consistent layout, and remote route boundaries</h3>
            </div>
          </div>
          <p className="mfe-shell-copy">
            This shell does not duplicate business pages. It keeps the control-plane framing
            consistent while loading invoice, supply-chain, and operations insight remotes through
            Module Federation.
          </p>
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Runtime</p>
              <h3>Shared dependencies</h3>
            </div>
          </div>
          <div className="mfe-meta-list">
            <div>
              <strong>Shared as singletons</strong>
              <span className="helper-text">react, react-dom, react-router-dom</span>
            </div>
            <div>
              <strong>Backend integrations</strong>
              <span className="helper-text">
                Existing Spring Boot, analytics-service, and ai-service endpoints are reused as-is.
              </span>
            </div>
          </div>
        </section>
      </div>

      <Outlet />
    </main>
  );
}
