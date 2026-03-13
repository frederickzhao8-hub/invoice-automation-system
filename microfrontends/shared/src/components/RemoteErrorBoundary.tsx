import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  moduleName: string;
  children: ReactNode;
}

interface State {
  error: Error | null;
}

export class RemoteErrorBoundary extends Component<Props, State> {
  state: State = {
    error: null,
  };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Keep enough detail in the console for local federation debugging.
    console.error(`Failed to load remote module "${this.props.moduleName}".`, error, errorInfo);
  }

  render() {
    if (this.state.error) {
      return (
        <section className="panel">
          <div className="error-banner">
            Unable to load the {this.props.moduleName} remote.
          </div>
          <p className="hero-copy compact-copy">
            Confirm that the remote dev server is running on its assigned port and that
            `remoteEntry.js` is reachable.
          </p>
          <p className="helper-text">{this.state.error.message}</p>
        </section>
      );
    }

    return this.props.children;
  }
}
