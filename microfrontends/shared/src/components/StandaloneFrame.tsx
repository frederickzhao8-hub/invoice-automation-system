import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';

interface NavItem {
  to: string;
  label: string;
}

interface StandaloneFrameProps {
  eyebrow: string;
  title: string;
  description: string;
  runtimeLabel: string;
  navItems?: NavItem[];
  children: ReactNode;
}

export function StandaloneFrame({
  eyebrow,
  title,
  description,
  runtimeLabel,
  navItems = [],
  children,
}: StandaloneFrameProps) {
  return (
    <main className="app-shell">
      <div className="background-orb background-orb-left" />
      <div className="background-orb background-orb-right" />

      <header className="workspace-header panel">
        <div className="workspace-brand">
          <p className="eyebrow">{eyebrow}</p>
          <h1>{title}</h1>
          <p className="hero-copy">{description}</p>
        </div>

        <div className="workspace-nav-stack">
          {navItems.length > 0 ? (
            <nav className="module-nav" aria-label="Standalone module navigation">
              {navItems.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) => `module-link${isActive ? ' active' : ''}`}
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          ) : null}

          <div className="mfe-badge-row">
            <span className="status-pill neutral">{runtimeLabel}</span>
          </div>
        </div>
      </header>

      {children}
    </main>
  );
}
