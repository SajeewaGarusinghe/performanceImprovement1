import axios from 'axios'

// During dev, use Vite proxy by default (`/api` → backend). In prod, set VITE_API_BASE.
const API_BASE = import.meta.env.VITE_API_BASE ?? '/api/performance'

export type PerfResponse = {
  executionTimeMs: number
  result: unknown
}

export const api = {
  nplus1Before: (ids: number[]) => axios.post<PerfResponse>(`${API_BASE}/nplus1/before`, ids).then(r => r.data),
  nplus1After: (ids: number[]) => axios.post<PerfResponse>(`${API_BASE}/nplus1/after`, ids).then(r => r.data),
  memoryBefore: (size: number) => axios.get<PerfResponse>(`${API_BASE}/memory/before`, { params: { size } }).then(r => r.data),
  memoryAfter: (size: number) => axios.get<PerfResponse>(`${API_BASE}/memory/after`, { params: { size } }).then(r => r.data),
  lookupBefore: (size: number, target: number) => axios.get<PerfResponse>(`${API_BASE}/lookup/before`, { params: { size, target } }).then(r => r.data),
  lookupAfter: (size: number, target: number) => axios.get<PerfResponse>(`${API_BASE}/lookup/after`, { params: { size, target } }).then(r => r.data),
  streamBefore: (size: number) => axios.get<PerfResponse>(`${API_BASE}/stream/before`, { params: { size } }).then(r => r.data),
  streamAfter: (size: number) => axios.get<PerfResponse>(`${API_BASE}/stream/after`, { params: { size } }).then(r => r.data),
  cacheBefore: (ids: number[]) => axios.post<PerfResponse>(`${API_BASE}/cache/before`, ids).then(r => r.data),
  cacheAfter: (ids: number[]) => axios.post<PerfResponse>(`${API_BASE}/cache/after`, ids).then(r => r.data),
  parallelBefore: (size: number) => axios.get<PerfResponse>(`${API_BASE}/parallel/before`, { params: { size } }).then(r => r.data),
  parallelAfter: (size: number) => axios.get<PerfResponse>(`${API_BASE}/parallel/after`, { params: { size } }).then(r => r.data)
}


