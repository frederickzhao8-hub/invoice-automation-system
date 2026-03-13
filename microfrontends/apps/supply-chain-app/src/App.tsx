import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { StandaloneFrame } from '@mfe-shared/components/StandaloneFrame';
import SupplyChainModule from './SupplyChainModule';

export default function App() {
  return (
    <BrowserRouter>
      <StandaloneFrame
        eyebrow="Supply Chain Remote"
        title="Supply-chain execution module running independently from the host shell."
        description="This remote keeps the full supply-chain route tree, including dashboards, order management, alerts, SLA configuration, and the AI explanation section."
        runtimeLabel="supply-chain-app :3002"
        navItems={[
          { to: '/supply-chain/dashboard', label: 'Dashboard' },
          { to: '/supply-chain/orders', label: 'Orders' },
          { to: '/supply-chain/alerts', label: 'Alerts' },
        ]}
      >
        <Routes>
          <Route path="/" element={<Navigate to="/supply-chain/dashboard" replace />} />
          <Route path="/supply-chain/*" element={<SupplyChainModule />} />
        </Routes>
      </StandaloneFrame>
    </BrowserRouter>
  );
}
