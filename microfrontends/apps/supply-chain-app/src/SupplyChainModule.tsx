import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { SupplyChainAlertsPage } from '@frontend/pages/SupplyChainAlertsPage';
import { SupplyChainDashboardPage } from '@frontend/pages/SupplyChainDashboardPage';
import { SupplyChainOrderDetailPage } from '@frontend/pages/SupplyChainOrderDetailPage';
import { SupplyChainOrdersPage } from '@frontend/pages/SupplyChainOrdersPage';
import { SupplyChainSlaConfigPage } from '@frontend/pages/SupplyChainSlaConfigPage';

const sectionLinks = [
  { to: '/supply-chain/dashboard', label: 'Dashboard' },
  { to: '/supply-chain/orders', label: 'Orders' },
  { to: '/supply-chain/alerts', label: 'Alerts' },
  { to: '/supply-chain/sla-rules', label: 'SLA Rules' },
];

export default function SupplyChainModule() {
  return (
    <>
      <section className="panel mfe-route-frame">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Supply Chain Remote</p>
            <h3>Operational milestones, alerts, SLA rules, and AI delay explanations</h3>
            <p className="hero-copy compact-copy">
              This remote owns the supply-chain routes and can run standalone or inside the host.
            </p>
          </div>
        </div>

        <nav className="section-nav mfe-route-nav" aria-label="Supply chain remote pages">
          {sectionLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) => `section-link${isActive ? ' active' : ''}`}
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
      </section>

      <Routes>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<SupplyChainDashboardPage />} />
        <Route path="orders" element={<SupplyChainOrdersPage />} />
        <Route path="orders/:orderId" element={<SupplyChainOrderDetailPage />} />
        <Route path="alerts" element={<SupplyChainAlertsPage />} />
        <Route path="sla-rules" element={<SupplyChainSlaConfigPage />} />
      </Routes>
    </>
  );
}
