import { Route, Routes } from 'react-router-dom';
import { InvoiceDashboardPage } from '@frontend/pages/InvoiceDashboardPage';

export default function InvoiceModule() {
  return (
    <Routes>
      <Route index element={<InvoiceDashboardPage />} />
    </Routes>
  );
}
