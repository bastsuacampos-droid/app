/** Pulsing placeholder for the split-second a useLiveQuery hasn't resolved yet — without this,
 * screens fell back straight to `?? 0` / `?? []`, so "0%" or an empty list briefly rendered as
 * if it were real data before the actual numbers popped in. */
export function Skeleton({ width, height, radius = 6, circle = false }: { width: number | string; height: number; radius?: number; circle?: boolean }) {
  return (
    <div
      className="skeleton-pulse"
      style={{ width, height, borderRadius: circle ? '50%' : radius, background: 'var(--surface-alt)', flexShrink: 0 }}
    />
  );
}
