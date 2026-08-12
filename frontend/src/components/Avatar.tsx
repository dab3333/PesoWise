/** Default profile picture until real avatars exist — first initial on a flat accent tint. */
export function Avatar({ name, size = 'size-9' }: { name?: string | null; size?: string }) {
  const initial = name?.trim()?.[0]?.toUpperCase() ?? '?'

  return (
    <span
      aria-hidden
      className={`grid ${size} shrink-0 place-items-center rounded-full bg-accent-soft text-sm font-semibold text-accent`}
    >
      {initial}
    </span>
  )
}
