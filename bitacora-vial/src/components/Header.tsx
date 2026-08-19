import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { IconChevronLeft } from './Icon';

interface HeaderProps {
  title: string;
  subtitle?: string;
  back?: boolean;
  actions?: ReactNode;
  children?: ReactNode; // extra rows (search bars, tabs, selectors)
}

export function Header({ title, subtitle, back, actions, children }: HeaderProps) {
  const navigate = useNavigate();
  return (
    <div className="header">
      <div className="header-row" style={{ marginBottom: children ? 14 : 0 }}>
        {back && (
          <button className="header-back" onClick={() => navigate(-1)} aria-label="Volver">
            <IconChevronLeft />
          </button>
        )}
        <div style={{ flexGrow: 1 }}>
          <div className="disp header-title">{title}</div>
          {subtitle && <div className="header-subtitle">{subtitle}</div>}
        </div>
        {actions}
      </div>
      {children}
    </div>
  );
}
