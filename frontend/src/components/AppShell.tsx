import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { isAdmin, useAuth } from '@/auth/AuthContext'
import { Avatar } from '@/components/Avatar'
import { Logo } from '@/components/Logo'
import { cn } from '@/components/ui'
import { ConfirmDialog, Modal } from '@/components/Modal'
import { ThemeToggle } from '@/components/ThemeToggle'

/** Inline SVGs rather than an icon package — a dozen glyphs is not worth a dependency. */
const icons = {
  dashboard: 'M3 3h7v7H3zM14 3h7v4h-7zM14 10h7v11h-7zM3 14h7v7H3z',
  transactions: 'M4 7h16M4 7l4-4M4 7l4 4M20 17H4m16 0l-4-4m4 4l-4 4',
  budgets: 'M3 13h4v8H3zM10 8h4v13h-4zM17 3h4v18h-4z',
  debts: 'M3 6h18v12H3zM3 10h18M7 15h4',
  goals: 'M12 21V8m0 0L7 3m5 5l5-5M5 21h14',
  recurring: 'M4 12a8 8 0 0113.7-5.7L20 8m0-5v5h-5M20 12a8 8 0 01-13.7 5.7L4 16m0 5v-5h5',
  settings:
    'M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.7 1.7 0 00.3 1.9l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-2.9 1.2V21a2 2 0 11-4 0v-.1A1.7 1.7 0 007 19.4a1.7 1.7 0 00-1.9.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1A1.7 1.7 0 003 15a1.7 1.7 0 00-1.7-1.1H1a2 2 0 110-4h.2A1.7 1.7 0 003 9a1.7 1.7 0 00-.3-1.9l-.1-.1a2 2 0 112.8-2.8l.1.1A1.7 1.7 0 009 3V3a2 2 0 114 0v.2A1.7 1.7 0 0017 4.6a1.7 1.7 0 001.9-.3l.1-.1a2 2 0 112.8 2.8l-.1.1A1.7 1.7 0 0021 9v0a2 2 0 110 4h-.2a1.7 1.7 0 00-1.4 1z',
  about: 'M12 16v-4M12 8h.01M12 21a9 9 0 100-18 9 9 0 000 18z',
  adminOverview: 'M12 2l8 4v6c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-4z',
  adminUsers: 'M16 14a4 4 0 10-8 0M12 12a4 4 0 100-8 4 4 0 000 8zM4 21a8 8 0 0116 0',
  adminFeedback:
    'M21 11.5a8.38 8.38 0 01-.9 3.8 8.5 8.5 0 01-7.6 4.7 8.38 8.38 0 01-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 01-.9-3.8 8.5 8.5 0 014.7-7.6 8.38 8.38 0 013.8-.9h.5a8.48 8.48 0 018 8v.5z',
  adminReports: 'M12 3v12m0 0l-4-4m4 4l4-4M5 21h14',
} as const

/** Three filled dots — distinct enough from the stroked glyphs above not to read as another page. */
function MoreIcon() {
  return (
    <svg aria-hidden viewBox="0 0 24 24" className="size-5 shrink-0">
      <circle cx="5" cy="12" r="1.75" fill="currentColor" />
      <circle cx="12" cy="12" r="1.75" fill="currentColor" />
      <circle cx="19" cy="12" r="1.75" fill="currentColor" />
    </svg>
  )
}

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
  { to: '/about', label: 'About', icon: 'about' },
]

// The sidebar has room for all eight; the phone-width bottom bar does not. Split into the three
// used most often plus a "More" sheet for the rest, rather than cramming eight tabs into ~390px.
const MOBILE_PRIMARY_COUNT = 3
const mobilePrimaryItems = navItems.slice(0, MOBILE_PRIMARY_COUNT)
const mobileMoreItems = navItems.slice(MOBILE_PRIMARY_COUNT)

/**
 * Shown only to admins, in a separate group under a divider rather than merged into navItems —
 * these are a different kind of page (managing other people's data, not the signed-in user's
 * own) and reordering them in with everyone's regular nav would bury that distinction.
 */
const adminNavItems: NavItem[] = [
  { to: '/admin', label: 'Overview', icon: 'adminOverview' },
  { to: '/admin/users', label: 'Users', icon: 'adminUsers' },
  { to: '/admin/feedback', label: 'Feedback', icon: 'adminFeedback' },
  { to: '/admin/reports', label: 'Reports', icon: 'adminReports' },
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
  const admin = isAdmin(user)
  const location = useLocation()
  const [moreOpen, setMoreOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [confirmingSignOut, setConfirmingSignOut] = useState(false)
  const isMoreActive = [...mobileMoreItems, ...(admin ? adminNavItems : [])].some(
    (item) => item.to === location.pathname,
  )

  // React Router does not reset scroll on navigation by itself — without this, following a link
  // from partway down a tall page (e.g. "Manage budgets" near the bottom of the dashboard) lands
  // on the next page still scrolled to that same position, looking like it opened at its bottom.
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [location.pathname])

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

          {admin && (
            <>
              <div className="mx-3 my-2 border-t border-line" />
              <p className="px-3 pb-1 text-xs font-medium uppercase tracking-wide text-subtle">
                Admin
              </p>
              {adminNavItems.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/admin'}
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
            </>
          )}
        </nav>

        <div className="border-t border-line p-3">
          <div className="flex items-center justify-between gap-2 px-2 py-1">
            <div className="flex min-w-0 items-center gap-2">
              <Avatar name={user?.displayName} />
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-ink">{user?.displayName}</p>
                <p className="truncate text-xs text-muted">{user?.email}</p>
              </div>
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
            onClick={() => setProfileOpen(true)}
            aria-label="Account"
            className="rounded-full transition-opacity hover:opacity-80"
          >
            <Avatar name={user?.displayName} />
          </button>
        </div>
      </header>

      {/* pb-24 clears the fixed bottom bar on mobile. Keyed by path so the animation re-runs on
          every navigation, not just the first mount. */}
      <main className="px-4 pt-6 pb-24 md:ml-60 md:px-8 md:pb-10">
        <div key={location.pathname} className="mx-auto max-w-6xl page-transition">
          <Outlet />
        </div>
      </main>

      <nav className="fixed inset-x-0 bottom-0 z-10 flex justify-around border-t border-line bg-surface md:hidden">
        {mobilePrimaryItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              cn(
                'flex min-w-0 flex-1 flex-col items-center gap-1 overflow-hidden py-2 text-[11px] font-medium',
                isActive ? 'text-accent' : 'text-muted',
              )
            }
          >
            <Icon name={item.icon} />
            {/* The two longest labels ("Dashboard", "Transactions") would otherwise overflow
                their column and bleed into the next tab's label at 7-across on a phone width. */}
            <span className="w-full truncate text-center">{item.label}</span>
          </NavLink>
        ))}
        <button
          type="button"
          onClick={() => setMoreOpen(true)}
          className={cn(
            'flex min-w-0 flex-1 flex-col items-center gap-1 overflow-hidden py-2 text-[11px] font-medium',
            isMoreActive ? 'text-accent' : 'text-muted',
          )}
        >
          <MoreIcon />
          <span className="w-full truncate text-center">More</span>
        </button>
      </nav>

      {moreOpen && (
        <Modal open onClose={() => setMoreOpen(false)} title="More">
          <nav className="flex flex-col gap-1">
            {mobileMoreItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={() => setMoreOpen(false)}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-accent-soft text-accent'
                      : 'text-body hover:bg-surface-muted',
                  )
                }
              >
                <Icon name={item.icon} />
                {item.label}
              </NavLink>
            ))}

            {admin && (
              <>
                <div className="mx-1 my-2 border-t border-line" />
                <p className="px-3 pb-1 text-xs font-medium uppercase tracking-wide text-subtle">
                  Admin
                </p>
                {adminNavItems.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.to === '/admin'}
                    onClick={() => setMoreOpen(false)}
                    className={({ isActive }) =>
                      cn(
                        'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                        isActive
                          ? 'bg-accent-soft text-accent'
                          : 'text-body hover:bg-surface-muted',
                      )
                    }
                  >
                    <Icon name={item.icon} />
                    {item.label}
                  </NavLink>
                ))}
              </>
            )}
          </nav>
        </Modal>
      )}

      {profileOpen && (
        <>
          {/* An invisible full-screen button, not a Modal backdrop — this is a dropdown menu
              (dismiss on outside tap), unlike the app's form dialogs (which stay open until an
              explicit close, per the earlier modal-dismissal fix). */}
          <button
            type="button"
            aria-label="Close account menu"
            onClick={() => setProfileOpen(false)}
            className="fixed inset-0 z-20 cursor-default md:hidden"
          />
          <div className="fixed top-16 right-4 z-20 w-64 rounded-xl border border-line bg-surface p-4 md:hidden">
            <div className="flex items-center gap-3">
              <Avatar name={user?.displayName} size="size-10" />
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-ink">{user?.displayName}</p>
                <p className="truncate text-xs text-muted">{user?.email}</p>
              </div>
            </div>
            <button
              type="button"
              className="mt-3 w-full rounded-lg px-3 py-2 text-left text-sm font-medium text-muted transition-colors hover:bg-surface-muted hover:text-body"
              onClick={() => {
                setProfileOpen(false)
                setConfirmingSignOut(true)
              }}
            >
              Sign out
            </button>
          </div>
        </>
      )}

      <ConfirmDialog
        open={confirmingSignOut}
        onClose={() => setConfirmingSignOut(false)}
        onConfirm={logout}
        confirmLabel="Sign out"
        title="Sign out?"
        message="You'll need to sign in again to get back to your budget."
      />
    </div>
  )
}
