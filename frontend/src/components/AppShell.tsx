import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { Logo } from '@/components/Logo'
import { cn } from '@/components/ui'
import { ThemeToggle } from '@/components/ThemeToggle'

/** Inline SVGs rather than an icon package — eight glyphs is not worth a dependency. */
const icons = {
  dashboard: 'M3 3h7v7H3zM14 3h7v4h-7zM14 10h7v11h-7zM3 14h7v7H3z',
  transactions: 'M4 7h16M4 7l4-4M4 7l4 4M20 17H4m16 0l-4-4m4 4l-4 4',
  budgets: 'M3 13h4v8H3zM10 8h4v13h-4zM17 3h4v18h-4z',
  debts: 'M3 6h18v12H3zM3 10h18M7 15h4',
  goals: 'M12 21V8m0 0L7 3m5 5l5-5M5 21h14',
  recurring: 'M4 12a8 8 0 0113.7-5.7L20 8m0-5v5h-5M20 12a8 8 0 01-13.7 5.7L4 16m0 5v-5h5',
  settings:
    'M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.7 1.7 0 00.3 1.9l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-2.9 1.2V21a2 2 0 11-4 0v-.1A1.7 1.7 0 007 19.4a1.7 1.7 0 00-1.9.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1A1.7 1.7 0 003 15a1.7 1.7 0 00-1.7-1.1H1a2 2 0 110-4h.2A1.7 1.7 0 003 9a1.7 1.7 0 00-.3-1.9l-.1-.1a2 2 0 112.8-2.8l.1.1A1.7 1.7 0 009 3V3a2 2 0 114 0v.2A1.7 1.7 0 0017 4.6a1.7 1.7 0 001.9-.3l.1-.1a2 2 0 112.8 2.8l-.1.1A1.7 1.7 0 0021 9v0a2 2 0 110 4h-.2a1.7 1.7 0 00-1.4 1z',
} as const

interface NavItem {
  to: string
  label: string
  icon: keyof typeof icons
}

const navItems: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: 'dashboard' },
  { to: '/transactions', label: 'Transactions', icon: 'transactions' },
  { to: '/budgets', label: 'Budgets', icon: 'budgets' },
  { to: '/debts', label: 'Debts', icon: 'debts' },
  { to: '/goals', label: 'Goals', icon: 'goals' },
  { to: '/recurring', label: 'Recurring', icon: 'recurring' },
  { to: '/settings', label: 'Settings', icon: 'settings' },
]

function Icon({ name }: { name: keyof typeof icons }) {
  return (
    <svg
      aria-hidden
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-5 shrink-0"
    >
      <path d={icons[name]} />
    </svg>
  )
}

/**
 * Persistent sidebar on desktop, bottom tab bar on mobile. Both render the same nav list, so
 * a new page only needs adding to navItems once.
 */
export function AppShell() {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-dvh bg-canvas">
      {/* Sidebar — hidden below md, where the bottom bar takes over. */}
      <aside className="fixed inset-y-0 left-0 hidden w-60 flex-col border-r border-line bg-surface md:flex">
        <div className="flex h-16 items-center px-5">
          <Logo />
        </div>

        <nav className="flex flex-1 flex-col gap-1 px-3 py-2">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-accent-soft text-accent'
                    : 'text-muted hover:bg-surface-muted hover:text-body',
                )
              }
            >
              <Icon name={item.icon} />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-line p-3">
          <div className="flex items-center justify-between gap-2 px-2 py-1">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-ink">{user?.displayName}</p>
              <p className="truncate text-xs text-muted">{user?.email}</p>
            </div>
            <ThemeToggle />
          </div>
          <button
            type="button"
            onClick={logout}
            className="mt-1 w-full rounded-lg px-3 py-2 text-left text-sm font-medium text-muted transition-colors hover:bg-surface-muted hover:text-body"
          >
            Sign out
          </button>
        </div>
      </aside>

      {/* Mobile top bar, since the sidebar's logo and theme toggle are hidden there. */}
      <header className="sticky top-0 z-10 flex h-16 items-center justify-between border-b border-line bg-surface px-4 md:hidden">
        <Logo />
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <button
            type="button"
            onClick={logout}
            className="rounded-lg px-3 py-2 text-sm font-medium text-muted hover:bg-surface-muted"
          >
            Sign out
          </button>
        </div>
      </header>

      {/* pb-24 clears the fixed bottom bar on mobile. */}
      <main className="px-4 pt-6 pb-24 md:ml-60 md:px-8 md:pb-10">
        <div className="mx-auto max-w-6xl">
          <Outlet />
        </div>
      </main>

      <nav className="fixed inset-x-0 bottom-0 z-10 flex justify-around border-t border-line bg-surface md:hidden">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              cn(
                'flex flex-1 flex-col items-center gap-1 py-2 text-[11px] font-medium',
                isActive ? 'text-accent' : 'text-muted',
              )
            }
          >
            <Icon name={item.icon} />
            {item.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
