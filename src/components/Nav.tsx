import { NavLink } from 'react-router-dom'

const items = [
  { to: '/', label: 'Inicio', icon: '🏠' },
  { to: '/rendiciones', label: 'Rendiciones', icon: '📁' },
  { to: '/resumen', label: 'Resumen', icon: '📊' },
]

export default function Nav() {
  return (
    <nav className="fixed bottom-0 left-1/2 w-full max-w-[480px] -translate-x-1/2 border-t border-gray-200 bg-white">
      <div className="flex">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center gap-0.5 py-2.5 text-xs font-medium ${
                isActive ? 'text-emerald-600' : 'text-gray-400'
              }`
            }
          >
            <span className="text-lg leading-none">{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}
