import { http } from './http'
import type { AdminCategory, CategorySaveRequest, EntityId } from '@/types/admin'

export const listCategories = () =>
  http.get<AdminCategory[], AdminCategory[]>('/admin/categories')

export const createCategory = (payload: CategorySaveRequest) =>
  http.post<number, number>('/admin/categories', payload)

export const updateCategory = (id: EntityId, payload: CategorySaveRequest) =>
  http.put<void, void>(`/admin/categories/${id}`, payload)

export const deleteCategory = (id: EntityId) =>
  http.delete<void, void>(`/admin/categories/${id}`)

export interface CategoryTreeNode extends AdminCategory {
  children: CategoryTreeNode[]
}

export const buildCategoryTree = (list: AdminCategory[]): CategoryTreeNode[] => {
  const map = new Map<EntityId, CategoryTreeNode>()
  const roots: CategoryTreeNode[] = []

  for (const c of list) {
    map.set(c.id, { ...c, children: [] })
  }
  for (const node of map.values()) {
    const pid = node.parentId ?? 0
    if (pid && pid !== 0 && map.has(pid)) {
      map.get(pid)!.children.push(node)
    } else {
      roots.push(node)
    }
  }
  const sortRec = (nodes: CategoryTreeNode[]) => {
    nodes.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    nodes.forEach((n) => sortRec(n.children))
  }
  sortRec(roots)
  return roots
}
