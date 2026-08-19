import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function base(size: number, props: IconProps) {
  const { size: _s, ...rest } = props;
  return {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none' as const,
    stroke: 'currentColor',
    strokeWidth: 1.9,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    ...rest,
  };
}

export const IconHome = (p: IconProps) => (
  <svg {...base(p.size ?? 21, p)}>
    <path d="M4 11.5 12 4l8 7.5" />
    <path d="M6 10v9a1 1 0 0 0 1 1h3v-6h4v6h3a1 1 0 0 0 1-1v-9" />
  </svg>
);

export const IconCubicacion = (p: IconProps) => (
  <svg {...base(p.size ?? 21, p)}>
    <path d="M5 20V14" /><path d="M12 20V8" /><path d="M19 20v-6" /><path d="M3 20h18" />
  </svg>
);

export const IconAsistencia = (p: IconProps) => (
  <svg {...base(p.size ?? 21, p)}>
    <circle cx="8" cy="8" r="3.2" />
    <path d="M2.5 20c0-3.6 2.5-6.5 5.5-6.5s5.5 2.9 5.5 6.5" />
    <circle cx="17" cy="9" r="2.6" />
    <path d="M14.8 13.7c2.6.4 4.7 3 4.7 6.3" />
  </svg>
);

export const IconFotos = (p: IconProps) => (
  <svg {...base(p.size ?? 21, p)}>
    <path d="M4 8h3l1.5-2h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z" />
    <circle cx="12" cy="13" r="3.4" />
  </svg>
);

export const IconHistorial = (p: IconProps) => (
  <svg {...base(p.size ?? 21, p)}>
    <circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" />
  </svg>
);

export const IconMas = (p: IconProps) => (
  <svg {...base(p.size ?? 21, p)}>
    <circle cx="5" cy="12" r="1.6" fill="currentColor" stroke="none" />
    <circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none" />
    <circle cx="19" cy="12" r="1.6" fill="currentColor" stroke="none" />
  </svg>
);

export const IconGear = (p: IconProps) => (
  <svg {...base(p.size ?? 21, p)} strokeWidth={1.7}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 13a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V19a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 17.58a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
  </svg>
);

export const IconChevronLeft = (p: IconProps) => (
  <svg {...base(p.size ?? 21, p)} strokeWidth={2}><path d="M14.5 5.5 8 12l6.5 6.5" /></svg>
);

export const IconChevronRight = (p: IconProps) => (
  <svg {...base(p.size ?? 16, p)} strokeWidth={2.2}><path d="M9.5 5.5 16 12l-6.5 6.5" /></svg>
);

export const IconPlus = (p: IconProps) => (
  <svg {...base(p.size ?? 19, p)} strokeWidth={2.2}><path d="M12 5v14" /><path d="M5 12h14" /></svg>
);

export const IconCloud = (p: IconProps) => (
  <svg {...base(p.size ?? 22, p)} strokeWidth={1.8}>
    <path d="M7 18h10a4 4 0 0 0 .5-7.97A5.5 5.5 0 0 0 7.1 9.1 4 4 0 0 0 7 18z" />
  </svg>
);

export const IconCheck = (p: IconProps) => (
  <svg {...base(p.size ?? 16, p)} strokeWidth={2.4}><path d="M5 13l4 4 10-10" /></svg>
);

export const IconX = (p: IconProps) => (
  <svg {...base(p.size ?? 14, p)} strokeWidth={2.4}><path d="M6 6l12 12" /><path d="M18 6 6 18" /></svg>
);

export const IconPencil = (p: IconProps) => (
  <svg {...base(p.size ?? 19, p)}>
    <path d="M4 20l.9-3.6L16.6 4.7a1.5 1.5 0 0 1 2.1 0l.6.6a1.5 1.5 0 0 1 0 2.1L7.6 19.1 4 20z" />
  </svg>
);

export const IconArrow = (p: IconProps) => (
  <svg {...base(p.size ?? 19, p)}><path d="M5 19 19 5" /><path d="M9 5h10v10" /></svg>
);

export const IconText = (p: IconProps) => (
  <svg {...base(p.size ?? 19, p)}><path d="M5 6h14" /><path d="M12 6v13" /></svg>
);

export const IconSquare = (p: IconProps) => (
  <svg {...base(p.size ?? 19, p)}><rect x="4.5" y="4.5" width="15" height="15" rx="2" /></svg>
);

export const IconCircle = (p: IconProps) => (
  <svg {...base(p.size ?? 19, p)}><circle cx="12" cy="12" r="7.5" /></svg>
);

export const IconUndo = (p: IconProps) => (
  <svg {...base(p.size ?? 19, p)}><path d="M8 8H4V4" /><path d="M4.5 8.5A8 8 0 1 1 6 17" /></svg>
);

export const IconSun = (p: IconProps) => (
  <svg {...base(p.size ?? 17, p)} strokeWidth={2}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1" />
  </svg>
);

export const IconCloudOutline = (p: IconProps) => (
  <svg {...base(p.size ?? 17, p)} strokeWidth={1.8}>
    <path d="M7 18h10a4 4 0 0 0 .5-7.97A5.5 5.5 0 0 0 7.1 9.1 4 4 0 0 0 7 18z" />
  </svg>
);

export const IconRain = (p: IconProps) => (
  <svg {...base(p.size ?? 17, p)} strokeWidth={1.8}>
    <path d="M7 15h10a4 4 0 0 0 .5-7.97A5.5 5.5 0 0 0 7.1 6.1 4 4 0 0 0 7 15z" />
    <path d="M9 19l-1 2M13 19l-1 2M17 19l-1 2" />
  </svg>
);

export const IconClockRain = (p: IconProps) => (
  <svg {...base(p.size ?? 17, p)} strokeWidth={1.8}>
    <circle cx="12" cy="12" r="8.5" /><path d="M12 8v5" /><circle cx="12" cy="16" r="0.6" fill="currentColor" stroke="none" />
  </svg>
);

export const IconCalendar = (p: IconProps) => (
  <svg {...base(p.size ?? 17, p)} strokeWidth={1.8}>
    <rect x="4" y="5.5" width="16" height="15" rx="2" /><path d="M4 10h16" /><path d="M8 3.5v3M16 3.5v3" />
  </svg>
);

export const IconSearch = (p: IconProps) => (
  <svg {...base(p.size ?? 15, p)} strokeWidth={2}><circle cx="11" cy="11" r="6.5" /><path d="M20 20l-4.3-4.3" /></svg>
);

export const IconFolder = (p: IconProps) => (
  <svg {...base(p.size ?? 21, p)} strokeWidth={1.9}>
    <path d="M3 7a2 2 0 0 1 2-2h4l2 2.5h8a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z" />
  </svg>
);

export const IconUpload = (p: IconProps) => (
  <svg {...base(p.size ?? 23, p)} strokeWidth={2.2}>
    <path d="M12 16V4" /><path d="M7 9l5-5 5 5" /><path d="M4 20h16" />
  </svg>
);

export const IconDownload = (p: IconProps) => (
  <svg {...base(p.size ?? 17, p)} strokeWidth={1.8}>
    <path d="M12 15V4" /><path d="M7 10l5 5 5-5" /><path d="M4 19h16" />
  </svg>
);

export const IconDoc = (p: IconProps) => (
  <svg {...base(p.size ?? 22, p)} strokeWidth={1.7}>
    <rect x="6" y="3" width="12" height="18" rx="1.5" /><path d="M9 12h6M9 15.5h6M9 8.5h3" />
  </svg>
);

export const IconTable = (p: IconProps) => (
  <svg {...base(p.size ?? 22, p)} strokeWidth={1.7}>
    <rect x="4" y="4" width="16" height="16" rx="2" /><path d="M4 10h16M10 4v16" />
  </svg>
);

export const IconClockPlus = (p: IconProps) => (
  <svg {...base(p.size ?? 19, p)} strokeWidth={1.9}>
    <circle cx="10.5" cy="13.5" r="7.5" /><path d="M10.5 9.5v4l2.6 2" />
    <circle cx="18" cy="6" r="4.2" fill="var(--amber)" stroke="none" />
    <path d="M18 3.8v4.4M15.8 6h4.4" stroke="#fff" strokeWidth={1.6} />
  </svg>
);

export const IconBell = (p: IconProps) => (
  <svg {...base(p.size ?? 17, p)} strokeWidth={1.9}>
    <path d="M18 8a6 6 0 0 0-12 0c0 5-2 6-2 6h16s-2-1-2-6" /><path d="M10.5 21a1.7 1.7 0 0 0 3 0" />
  </svg>
);

export const IconLocation = (p: IconProps) => (
  <svg {...base(p.size ?? 17, p)} strokeWidth={1.9}>
    <path d="M12 2c-4 4.5-7 8-7 11.5A7 7 0 0 0 12 21a7 7 0 0 0 7-7.5C19 10 16 6.5 12 2z" />
  </svg>
);

export const IconRefresh = (p: IconProps) => (
  <svg {...base(p.size ?? 19, p)} strokeWidth={1.9}>
    <path d="M23 4v6h-6" /><path d="M1 20v-6h6" />
    <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10" /><path d="M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
  </svg>
);

export const IconCamera = IconFotos;

export const IconLogo = (p: IconProps) => (
  <svg {...base(p.size ?? 26, p)} strokeWidth={2}>
    <path d="M3 18h18" /><path d="M6 18l4-11h4l4 11" /><path d="M10 11h4" />
  </svg>
);
