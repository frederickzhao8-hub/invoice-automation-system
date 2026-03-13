import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { OperationsOverviewPage } from './pages/OperationsOverviewPage';

export default function DashboardModule() {
  return (
    <>
      <section className="panel mfe-route-frame">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Dashboard Remote</p>
            <h3>Analytics and AI insight panels packaged as a standalone remote</h3>
            <p className="hero-copy compact-copy">
              This remote surfaces cross-domain operational reporting without changing the existing
              backend, analytics-service, or ai-service APIs.
            </p>
          </div>
        </div>

        <nav className="section-nav mfe-route-nav" aria-label="Operations dashboard routes">
          <NavLink
            to="/operations/overview"
            className={({ isActive }) => `section-link${isActive ? ' active' : ''}`}
          >
            Overview
          </NavLink>
        </nav>
      </section>

      <Routes>
        <Route index element={<Navigate to="overview" replace />} />
        <Route path="overview" element={<OperationsOverviewPage />} />
      </Routes>
    </>
  );
}
