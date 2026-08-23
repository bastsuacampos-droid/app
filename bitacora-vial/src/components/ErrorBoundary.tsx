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
        <div style={{ padding: 24, color: 'var(--text)', background: 'var(--bg)', height: '100%', fontFamily: 'sans-serif' }}>
          <h2 style={{ color: 'var(--red)' }}>Algo salió mal</h2>
          <p style={{ fontSize: 13, color: 'var(--text-soft)' }}>{this.state.error.message || this.state.error.name}</p>
          <div className="flex-row gap-8" style={{ marginTop: 16 }}>
            <button
              onClick={() => this.setState({ error: null })}
              style={{ background: 'var(--accent)', color: '#fff', border: 'none', borderRadius: 10, padding: '10px 16px', fontWeight: 700 }}
            >
              Reintentar
            </button>
            {/* Plain hash navigation, not react-router's navigate() — this is a class component
             * with no router context, and a crash inside the current screen shouldn't require
             * one to still work. Always available, regardless of which screen broke or whether
             * its own header/back button is part of what crashed. */}
            <button
              onClick={() => { window.location.hash = '/'; this.setState({ error: null }); }}
              style={{ background: 'var(--surface-alt)', color: 'var(--text)', border: 'none', borderRadius: 10, padding: '10px 16px', fontWeight: 700 }}
            >
              Volver al inicio
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
