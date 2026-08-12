import { useState } from 'react'
import { PageHeader } from '@/components/PageHeader'
import { Alert, Button, Card, CardTitle } from '@/components/ui'
import { downloadUsersReport } from '@/api/useAdmin'
import { ApiError } from '@/lib/api'

export function AdminReportsPage() {
  return (
    <>
      <PageHeader title="Reports" subtitle="Exports for offline analysis." />

      <div className="grid gap-6">
        <ReportCard
          title="Users"
          description="Every account: role, verification and disabled status, and when it was created."
          filename="users.csv"
          download={downloadUsersReport}
        />
      </div>
    </>
  )
}

function ReportCard({
  title,
  description,
  filename,
  download,
}: {
  title: string
  description: string
  filename: string
  download: () => Promise<void>
}) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onDownload() {
    setLoading(true)
    setError(null)
    try {
      await download()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not download that report.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card>
      <CardTitle>{title}</CardTitle>
      {error && (
        <div className="mb-4">
          <Alert>{error}</Alert>
        </div>
      )}
      <p className="mb-4 text-sm text-muted">{description}</p>
      <Button variant="secondary" loading={loading} onClick={onDownload}>
        Download {filename}
      </Button>
    </Card>
  )
}
