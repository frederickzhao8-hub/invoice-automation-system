import { lazy } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { HostShell } from './components/HostShell';
import { RemoteSlot } from './components/RemoteSlot';

const InvoiceModule = lazy(() => import('invoice_app/InvoiceModule'));
const SupplyChainModule = lazy(() => import('supply_chain_app/SupplyChainModule'));
const DashboardModule = lazy(() => import('dashboard_app/DashboardModule'));

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<HostShell />}>
          <Route path="/" element={<Navigate to="/operations/overview" replace />} />
          <Route
            path="/operations/*"
            element={
              <RemoteSlot moduleName="dashboard-app">
                <DashboardModule />
              </RemoteSlot>
            }
          />
          <Route
            path="/invoices/*"
            element={
              <RemoteSlot moduleName="invoice-app">
                <InvoiceModule />
              </RemoteSlot>
            }
          />
          <Route
            path="/supply-chain/*"
            element={
              <RemoteSlot moduleName="supply-chain-app">
                <SupplyChainModule />
              </RemoteSlot>
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
