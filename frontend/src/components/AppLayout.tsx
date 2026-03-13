import { NavLink, Outlet, useLocation } from 'react-router-dom';

const supplyChainLinks = [
  { to: '/supply-chain/dashboard', label: 'Dashboard' },
  { to: '/supply-chain/orders', label: 'Orders' },
  { to: '/supply-chain/alerts', label: 'Alerts' },
  { to: '/supply-chain/sla-rules', label: 'SLA Rules' },
];

export function AppLayout() {
  const location = useLocation();
  const isSupplyChainRoute = location.pathname.startsWith('/supply-chain');

  return (
    <main className="app-shell">
      <div className="background-orb background-orb-left" />
      <div className="background-orb background-orb-right" />

      <header className="workspace-header panel">
        <div className="workspace-brand">
          <p className="eyebrow">Operations Workspace</p>
          <h1>Invoice automation and supply-chain execution in one control plane.</h1>
          <p className="hero-copy">
            Use the invoice module for finance operations and the new supply-chain module for order
            milestones, SLA monitoring, and operational alerts.
          </p>
        </div>

        <div className="workspace-nav-stack">
          <nav className="module-nav" aria-label="Modules">
            <NavLink
              to="/supply-chain/dashboard"
              className={({ isActive }) => `module-link${isActive ? ' active' : ''}`}
            >
              Supply Chain
            </NavLink>
            <NavLink
              to="/invoices"
              className={({ isActive }) => `module-link${isActive ? ' active' : ''}`}
            >
              Invoices
            </NavLink>
          </nav>

          {isSupplyChainRoute ? (
            <nav className="section-nav" aria-label="Supply chain pages">
              {supplyChainLinks.map((link) => (
                <NavLink
                  key={link.to}
                  to={link.to}
                  className={({ isActive }) => `section-link${isActive ? ' active' : ''}`}
                >
                  {link.label}
                </NavLink>
              ))}
            </nav>
          ) : null}
        </div>
      </header>

      <Outlet />
    </main>
  );
}
