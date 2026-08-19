import { Component, type ReactNode } from 'react';

interface Props { children: ReactNode }
interface State { error: Error | null }

/** Catches render-time errors (e.g. a bad useLiveQuery read) so the app shows something
 * recoverable instead of a blank screen, and logs full details for debugging. */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: { componentStack: string }) {
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary]', error.name, error.message, error.stack, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <div style={{ padding: 24, color: '#fff', background: '#242220', height: '100%', fontFamily: 'sans-serif' }}>
          <h2 style={{ color: '#f2b705' }}>Algo salió mal</h2>
          <p style={{ fontSize: 13, color: '#c9c3b8' }}>{this.state.error.message || this.state.error.name}</p>
          <button
            onClick={() => this.setState({ error: null })}
            style={{ marginTop: 16, background: '#e8600c', color: '#fff', border: 'none', borderRadius: 10, padding: '10px 16px', fontWeight: 700 }}
          >
            Reintentar
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
