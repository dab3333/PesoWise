import { Card, EmptyState } from '@/components/ui'
import { PageHeader } from '@/components/PageHeader'

/**
 * Stands in for pages whose backend slice is not built yet, so the shell and navigation are
 * fully walkable from the start. Each is replaced by the real page in its build step.
 */
export function PlaceholderPage({ title, step }: { title: string; step: string }) {
  return (
    <>
      <PageHeader title={title} />
      <Card>
        <EmptyState message={`${title} arrives in ${step}.`} />
      </Card>
    </>
  )
}
