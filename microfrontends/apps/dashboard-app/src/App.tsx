import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { StandaloneFrame } from '@mfe-shared/components/StandaloneFrame';
import DashboardModule from './DashboardModule';

export default function App() {
  return (
    <BrowserRouter>
      <StandaloneFrame
        eyebrow="Dashboard Remote"
        title="Operations analytics and AI insight remote."
        description="This standalone app focuses on analytics-service metrics and ai-service summaries, while the host loads the same module through Module Federation."
        runtimeLabel="dashboard-app :3003"
        navItems={[{ to: '/operations/overview', label: 'Overview' }]}
      >
        <Routes>
          <Route path="/" element={<Navigate to="/operations/overview" replace />} />
          <Route path="/operations/*" element={<DashboardModule />} />
        </Routes>
      </StandaloneFrame>
    </BrowserRouter>
  );
}
