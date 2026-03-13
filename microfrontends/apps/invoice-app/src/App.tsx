import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { StandaloneFrame } from '@mfe-shared/components/StandaloneFrame';
import InvoiceModule from './InvoiceModule';

export default function App() {
  return (
    <BrowserRouter>
      <StandaloneFrame
        eyebrow="Invoice Remote"
        title="Finance operations workspace running as an independent remote."
        description="This standalone app reuses the existing invoice dashboard page while exposing the same module to the host through Module Federation."
        runtimeLabel="invoice-app :3001"
        navItems={[{ to: '/invoices', label: 'Invoice Dashboard' }]}
      >
        <Routes>
          <Route path="/" element={<Navigate to="/invoices" replace />} />
          <Route path="/invoices/*" element={<InvoiceModule />} />
        </Routes>
      </StandaloneFrame>
    </BrowserRouter>
  );
}
