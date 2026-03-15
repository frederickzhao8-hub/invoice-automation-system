import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { DeliveryImageRecognitionPage } from './pages/DeliveryImageRecognitionPage';
import { InvoiceDashboardPage } from './pages/InvoiceDashboardPage';
import { SupplyChainAlertsPage } from './pages/SupplyChainAlertsPage';
import { SupplyChainDashboardPage } from './pages/SupplyChainDashboardPage';
import { SupplyChainOrderDetailPage } from './pages/SupplyChainOrderDetailPage';
import { SupplyChainOrdersPage } from './pages/SupplyChainOrdersPage';
import { SupplyChainSlaConfigPage } from './pages/SupplyChainSlaConfigPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/supply-chain/dashboard" replace />} />
          <Route path="/invoices" element={<InvoiceDashboardPage />} />
          <Route path="/delivery-images" element={<DeliveryImageRecognitionPage />} />
          <Route path="/supply-chain/dashboard" element={<SupplyChainDashboardPage />} />
          <Route path="/supply-chain/orders" element={<SupplyChainOrdersPage />} />
          <Route path="/supply-chain/orders/:orderId" element={<SupplyChainOrderDetailPage />} />
          <Route path="/supply-chain/alerts" element={<SupplyChainAlertsPage />} />
          <Route path="/supply-chain/sla-rules" element={<SupplyChainSlaConfigPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
