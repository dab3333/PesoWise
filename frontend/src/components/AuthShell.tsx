import type { ReactNode } from 'react'
import { Logo } from '@/components/Logo'

/**
 * The centred card every signed-out page sits in: sign in, register, forgot password, reset
 * password, and email confirmation.
 *
 * <p>They shared this markup by copy-paste before there were five of them. Having one place to
 * change it is what lets the brand panel land later without touching five files.
 */
export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle?: string
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center bg-canvas px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex justify-center">
          <Logo />
        </div>

        <div className="page-transition rounded-xl border border-line bg-surface p-6">
          <h1 className="text-xl font-semibold">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
          <div className="mt-6">{children}</div>
        </div>

        {footer && <div className="mt-6 text-center text-sm text-muted">{footer}</div>}
      </div>
    </main>
  )
}
