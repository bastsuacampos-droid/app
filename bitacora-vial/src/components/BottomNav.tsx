import { NavLink } from 'react-router-dom';
import { IconHome, IconCubicacion, IconAsistencia, IconFotos, IconHistorial, IconMas } from './Icon';

const items = [
  { to: '/', label: 'Inicio', Icon: IconHome, end: true },
  { to: '/cubicacion', label: 'Cubicación', Icon: IconCubicacion },
  { to: '/asistencia', label: 'Asistencia', Icon: IconAsistencia },
  { to: '/fotos', label: 'Fotos', Icon: IconFotos },
  { to: '/historial', label: 'Historial', Icon: IconHistorial },
  { to: '/mas', label: 'Más', Icon: IconMas },
];

export function BottomNav() {
  return (
    <nav className="bottom-nav">
      {items.map(({ to, label, Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
        >
          <Icon />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
