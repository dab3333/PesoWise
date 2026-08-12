import type { ReactNode } from 'react'
import { Logo } from '@/components/Logo'

const TAGLINE = 'Make Every Peso Count.'

const FEATURES = [
  'Every peso categorized automatically',
  'Debts, savings goals, and bills — one place',
  'Built around the 70-20-10 budgeting rule',
]

/**
 * The shell every signed-out page sits in: sign in, register, forgot password, reset password,
 * and email confirmation.
 *
 * <p>They shared this markup by copy-paste before there were five of them. Having one place to
 * change it is what let the brand panel below land in one file instead of five.
 *
 * <p>A flat jade brand panel fills the left column from md up — logo, tagline, three one-line
 * features, no gradient (per design-system.md, jade is the only accent and it stays flat). Below
 * md there's no room for a second column, so the panel disappears and the tagline moves above
 * the form card instead; the features don't follow it there, since a phone screen this narrow
 * should get to the form quickly rather than scroll past a features list first.
 *
 * <p>Fixed to the viewport height (`h-dvh`, not `min-h-dvh`) with each column scrolling on its
 * own: register collects enough fields to outgrow a short screen, and without this the whole
 * page — brand panel included — grew and scrolled together, taking the tagline off screen along
 * with everything else. The form column centers its content vertically with `safe center`
 * rather than plain `center` — ordinary flex centering hides whatever overflows above the fold
 * with no way to scroll up to it, so a short form (login) still lands dead-centre while a long
 * one (register) falls back to flush-top instead of clipping.
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
    <main className="flex h-dvh flex-col overflow-hidden bg-canvas md:flex-row">
      <div className="hidden flex-col justify-center gap-10 overflow-y-auto bg-accent px-12 py-16 text-accent-on md:flex md:w-[42%] lg:w-[38%]">
        <Logo inverted />
        <div>
          <h2 className="text-3xl font-extrabold leading-tight text-accent-on lg:text-4xl">
            {TAGLINE}
          </h2>
          <ul className="mt-8 flex flex-col gap-4">
            {FEATURES.map((feature) => (
              <FeatureLine key={feature}>{feature}</FeatureLine>
            ))}
          </ul>
        </div>
      </div>

      <div className="flex flex-1 flex-col items-center justify-center-safe overflow-y-auto px-4 py-10">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex flex-col items-center gap-2 md:hidden">
            <Logo />
            <p className="text-sm font-medium text-muted">{TAGLINE}</p>
          </div>

          <div className="page-transition rounded-xl border border-line bg-surface p-6">
            <h1 className="text-xl font-semibold">{title}</h1>
            {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
            <div className="mt-6">{children}</div>
          </div>

          {footer && <div className="mt-6 text-center text-sm text-muted">{footer}</div>}
        </div>
      </div>
    </main>
  )
}

function FeatureLine({ children }: { children: ReactNode }) {
  return (
    <li className="flex items-start gap-2.5 text-sm text-accent-on/85">
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="mt-0.5 size-4 shrink-0"
      >
        <path d="M5 13l4 4L19 7" />
      </svg>
      {children}
    </li>
  )
}
