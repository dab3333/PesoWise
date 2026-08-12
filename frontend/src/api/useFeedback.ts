import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'

export type FeedbackCategory = 'BUG' | 'IDEA' | 'OTHER'
export type FeedbackStatus = 'NEW' | 'REVIEWING' | 'RESOLVED'

export const FEEDBACK_CATEGORY_LABELS: Record<FeedbackCategory, string> = {
  BUG: 'Something is broken',
  IDEA: 'Idea or suggestion',
  OTHER: 'Something else',
}

export const FEEDBACK_STATUS_LABELS: Record<FeedbackStatus, string> = {
  NEW: 'New',
  REVIEWING: 'Reviewing',
  RESOLVED: 'Resolved',
}

export interface Feedback {
  id: string
  userId: string
  userEmail: string
  userName: string
  category: FeedbackCategory
  subject: string
  message: string
  status: FeedbackStatus
  adminNote: string | null
  createdAt: string
  resolvedAt: string | null
}

export interface FeedbackPage {
  items: Feedback[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface FeedbackCounts {
  newCount: number
  reviewingCount: number
  resolvedCount: number
}

export interface SubmitFeedbackInput {
  category: FeedbackCategory
  userEmail: string
  userName: string
  subject: string
  message: string
}

export interface UpdateFeedbackStatusInput {
  status: FeedbackStatus
  adminNote?: string
}

/**
 * Every user's own submission — used by the About page, not the admin panel. There is nothing to
 * invalidate here: this page doesn't list what was sent, and the admin feedback list is a
 * separate query key that a non-admin can't read anyway.
 */
export function useSubmitFeedback() {
  return useMutation({
    mutationFn: (input: SubmitFeedbackInput) => api.post<Feedback>('/api/feedback', input),
  })
}

export function useAdminFeedback(params: { status?: string; page: number; size: number }) {
  const query = new URLSearchParams()
  if (params.status) query.set('status', params.status)
  query.set('page', String(params.page))
  query.set('size', String(params.size))

  return useQuery({
    queryKey: ['admin', 'feedback', params.status ?? null, params.page, params.size],
    queryFn: () => api.get<FeedbackPage>(`/api/admin/feedback?${query}`),
  })
}

export function useUpdateFeedbackStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateFeedbackStatusInput }) =>
      api.patch<Feedback>(`/api/admin/feedback/${id}`, input),
    onSuccess: () => {
      // The counts on /api/admin/overview and the audit trail both change with every transition.
      queryClient.invalidateQueries({ queryKey: ['admin', 'feedback'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'overview'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'audit'] })
    },
  })
}
