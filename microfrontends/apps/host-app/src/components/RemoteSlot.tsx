import { Suspense, type ReactNode } from 'react';
import { RemoteErrorBoundary } from '@mfe-shared/components/RemoteErrorBoundary';

interface RemoteSlotProps {
  moduleName: string;
  children: ReactNode;
}

export function RemoteSlot({ moduleName, children }: RemoteSlotProps) {
  return (
    <RemoteErrorBoundary moduleName={moduleName}>
      <Suspense
        fallback={
          <section className="panel">
            <p className="empty-state">Loading {moduleName} remote...</p>
          </section>
        }
      >
        {children}
      </Suspense>
    </RemoteErrorBoundary>
  );
}
