import { useNavigate } from 'react-router-dom';
import {
  IconClockPlus, IconFolder, IconGear, IconChevronRight, IconAsistencia, IconCubicacion,
} from '../../components/Icon';

export function MasPage() {
  const navigate = useNavigate();

  return (
    <>
      <div className="header">
        <div className="disp header-title">Más herramientas</div>
        <div className="header-subtitle">Documentación, horas extra y ajustes del proyecto</div>
      </div>

      <div className="content">
        <div className="section-label">Personal</div>
        <MenuCard
          Icon={IconAsistencia}
          bg="var(--blue-soft)"
          title="Cuadrilla"
          subtitle="Editar, agregar o dar de baja trabajadores"
          onClick={() => navigate('/cuadrilla')}
        />
        <MenuCard
          Icon={IconClockPlus}
          bg="var(--accent-soft)"
          title="Horas Extra"
          subtitle="Reporte mensual de horas trabajadas extra"
          onClick={() => navigate('/horas-extra')}
        />

        <div className="section-label" style={{ marginTop: 20 }}>Proyecto</div>
        <MenuCard
          Icon={IconCubicacion}
          bg="var(--yellow-soft)"
          iconColor="var(--yellow-text)"
          title="Frentes de trabajo"
          subtitle="Editar, agregar o eliminar puntos de trabajo"
          onClick={() => navigate('/frentes')}
        />
        <MenuCard
          Icon={IconFolder}
          bg="var(--blue-soft)"
          title="Documentos"
          subtitle="Planos, permisos, contratos y fichas técnicas"
          onClick={() => navigate('/documentos')}
        />

        <div className="section-label" style={{ marginTop: 20 }}>Aplicación</div>
        <MenuCard
          Icon={IconGear}
          bg="var(--surface-alt)"
          iconColor="var(--text-soft)"
          title="Configuración"
          subtitle="Perfil, preferencias y permisos"
          onClick={() => navigate('/configuracion')}
        />
      </div>
    </>
  );
}

function MenuCard({ Icon, bg, iconColor, title, subtitle, onClick }: { Icon: typeof IconGear; bg: string; iconColor?: string; title: string; subtitle: string; onClick: () => void }) {
  return (
    <button onClick={onClick} className="card list-row" style={{ width: '100%', textAlign: 'left' }}>
      <div style={{ width: 42, height: 42, borderRadius: 11, background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <Icon size={21} color={iconColor ?? 'var(--accent-dark)'} />
      </div>
      <div style={{ flexGrow: 1 }}>
        <div style={{ fontSize: 14, fontWeight: 700 }}>{title}</div>
        <div className="text-soft" style={{ fontSize: 11.5 }}>{subtitle}</div>
      </div>
      <IconChevronRight color="var(--text-soft)" />
    </button>
  );
}
