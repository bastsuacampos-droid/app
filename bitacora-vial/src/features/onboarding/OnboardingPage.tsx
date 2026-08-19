import { useState } from 'react';
import { db } from '../../lib/db';
import { IconLogo, IconFotos, IconFolder, IconLocation, IconBell } from '../../components/Icon';

const PERMISOS = [
  {
    Icon: IconFotos,
    bg: 'var(--orange-soft)',
    color: 'var(--orange-dark)',
    title: 'Cámara',
    text: 'Para fotografiar el avance de obra y anotar sobre las fotos.',
  },
  {
    Icon: IconFolder,
    bg: 'var(--blue-soft)',
    color: 'var(--blue)',
    title: 'Fotos y archivos',
    text: 'Para guardar tus fotos y respaldar documentos del proyecto.',
  },
  {
    Icon: IconLocation,
    bg: 'var(--yellow-soft)',
    color: 'var(--yellow-text)',
    title: 'Ubicación · opcional',
    text: 'Registra automáticamente la progresiva (Km) del frente donde estás.',
  },
  {
    Icon: IconBell,
    bg: 'var(--green-soft)',
    color: 'var(--green)',
    title: 'Notificaciones',
    text: 'Para avisarte si falta respaldar un parte o registrar el día.',
  },
];

/** Some browsers never resolve a permission prompt (denied silently, blocked context, no
 * device). Race it against a timeout so onboarding can never hang forever on "Continuar". */
function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) => setTimeout(() => reject(new Error('timeout')), ms)),
  ]);
}

export function OnboardingPage() {
  const [working, setWorking] = useState(false);

  async function handleContinue() {
    setWorking(true);

    let permisoCamara = false;
    try {
      const stream = await withTimeout(navigator.mediaDevices.getUserMedia({ video: true }), 8000);
      stream.getTracks().forEach((t) => t.stop());
      permisoCamara = true;
    } catch {
      permisoCamara = false;
    }

    let permisoNotificaciones = false;
    try {
      if ('Notification' in window) {
        const result = await withTimeout(Notification.requestPermission(), 8000);
        permisoNotificaciones = result === 'granted';
      }
    } catch {
      permisoNotificaciones = false;
    }

    await db.settings.update('app', {
      onboardingComplete: true,
      permisoCamara,
      permisoNotificaciones,
      permisoUbicacion: false,
    });
  }

  return (
    <div style={{ background: 'var(--charcoal)', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div className="content" style={{ paddingTop: 48, color: '#fff' }}>
        <div className="flex-row gap-10" style={{ marginBottom: 28 }}>
          <IconLogo size={30} stroke="var(--amber)" />
          <span className="disp" style={{ fontSize: 19, fontWeight: 800 }}>Bitácora Vial</span>
        </div>

        <div className="disp" style={{ fontSize: 22, fontWeight: 800, marginBottom: 10 }}>Antes de empezar</div>
        <div style={{ color: '#c9c3b8', fontSize: 13.5, lineHeight: 1.5, marginBottom: 26 }}>
          Para registrar tus partes diarios en terreno necesitamos estos permisos. Puedes cambiarlos después en
          Configuración.
        </div>

        <div className="stack">
          {PERMISOS.map(({ Icon, bg, color, title, text }) => (
            <div
              key={title}
              style={{
                background: 'rgba(255,255,255,.06)',
                border: '1px solid rgba(255,255,255,.1)',
                borderRadius: 14,
                padding: 14,
                display: 'flex',
                gap: 13,
                alignItems: 'flex-start',
              }}
            >
              <div style={{ width: 38, height: 38, borderRadius: 10, background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Icon size={19} color={color} />
              </div>
              <div>
                <div style={{ fontSize: 13.5, fontWeight: 700, marginBottom: 2 }}>{title}</div>
                <div style={{ color: '#c9c3b8', fontSize: 11.5, lineHeight: 1.4 }}>{text}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div style={{ flexShrink: 0, padding: '14px 24px 30px' }}>
        <button className="btn btn-primary btn-block" disabled={working} onClick={handleContinue}>
          {working ? 'Solicitando permisos…' : 'Continuar'}
        </button>
        <div style={{ textAlign: 'center', fontSize: 11, color: '#8a8478', marginTop: 10 }}>
          Podrás revisar y cambiar estos permisos cuando quieras
        </div>
      </div>
    </div>
  );
}
