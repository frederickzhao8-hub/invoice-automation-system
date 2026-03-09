import type { BatchUploadResult } from '../types/invoice';

interface BatchUploadResultsPanelProps {
  result: BatchUploadResult | null;
}

function statusLabel(status: BatchUploadResult['results'][number]['status']) {
  switch (status) {
    case 'SUCCESS':
      return 'Success';
    case 'DUPLICATE':
      return 'Duplicate';
    case 'FAILED':
      return 'Failed';
    case 'PROCESSING':
      return 'Processing';
    default:
      return status;
  }
}

export function BatchUploadResultsPanel({ result }: BatchUploadResultsPanelProps) {
  if (!result) {
    return null;
  }

  return (
    <section className="panel batch-results-card">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Batch Results</p>
          <h3>Per-file AI processing outcome</h3>
        </div>
        <span className="helper-text">
          {result.successCount} success / {result.duplicateCount} duplicate / {result.failureCount}{' '}
          failed
        </span>
      </div>

      <div className="table-wrapper">
        <table className="batch-results-table">
          <thead>
            <tr>
              <th>File</th>
              <th>Status</th>
              <th>Vendor</th>
              <th>Invoice #</th>
              <th>Message</th>
            </tr>
          </thead>
          <tbody>
            {result.results.map((item) => (
              <tr key={`${item.fileName}-${item.invoiceId ?? item.status}`}>
                <td>{item.fileName}</td>
                <td>
                  <span className={`parse-pill ${item.status.toLowerCase()}`}>
                    {statusLabel(item.status)}
                  </span>
                </td>
                <td>{item.vendorName ?? 'n/a'}</td>
                <td>{item.invoiceNumber ?? 'n/a'}</td>
                <td>{item.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
