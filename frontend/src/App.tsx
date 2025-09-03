import { useEffect, useMemo, useState } from 'react'
import { api, PerfResponse } from './api'

type RunResult = { label: string; data?: PerfResponse; error?: string }

function Row({ title, onBefore, onAfter }: { title: string; onBefore: () => Promise<PerfResponse>; onAfter: () => Promise<PerfResponse> }) {
  const [before, setBefore] = useState<RunResult>({ label: 'Before' })
  const [after, setAfter] = useState<RunResult>({ label: 'After' })
  const [loading, setLoading] = useState(false)

  const run = async () => {
    setLoading(true)
    try {
      const b = await onBefore()
      setBefore({ label: 'Before', data: b })
      const a = await onAfter()
      setAfter({ label: 'After', data: a })
    } catch (e: any) {
      const msg = e?.response ? `${e.response.status} ${e.response.statusText}` : (e?.message ?? 'Error')
      setBefore(p => ({ ...p, error: msg }))
      setAfter(p => ({ ...p, error: msg }))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ border: '1px solid #ddd', borderRadius: 8, padding: 16, marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ margin: 0 }}>{title}</h3>
        <button onClick={run} disabled={loading}>{loading ? 'Running...' : 'Run'}</button>
      </div>
      {(before.data?.executionTimeMs !== undefined && after.data?.executionTimeMs !== undefined) && (
        <div className="compare-summary">
          {(() => {
            const b = before.data!.executionTimeMs
            const a = after.data!.executionTimeMs
            const diff = b - a
            const pct = b > 0 ? Math.round((diff / b) * 100) : 0
            const worst = Math.max(b, a) || 1
            const bWidth = Math.max(2, Math.round((b / worst) * 100))
            const aWidth = Math.max(2, Math.round((a / worst) * 100))
            return (
              <>
                <span className={`chip ${diff > 0 ? 'good' : diff < 0 ? 'bad' : ''}`}>Δ {diff} ms</span>
                <span className={`chip ${diff > 0 ? 'good' : diff < 0 ? 'bad' : ''}`}>{diff > 0 ? `↓ ${pct}% faster` : diff < 0 ? `↑ ${Math.abs(pct)}% slower` : 'no change'}</span>
                <div className="bars" style={{ width: '100%', maxWidth: 480 }}>
                  <div className="bar before" style={{ width: bWidth + '%' }}><span>before {b} ms</span></div>
                  <div className="bar after" style={{ width: aWidth + '%' }}><span>after {a} ms</span></div>
                </div>
              </>
            )
          })()}
        </div>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 12 }}>
        {[before, after].map((r) => (
          <div key={r.label} className="result-card" style={{ padding: 12, borderRadius: 6 }}>
            <strong>{r.label}</strong>
            <div>Time: {r.data?.executionTimeMs ?? '-' } ms</div>
            <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{r.error ? `Error: ${r.error}` : JSON.stringify(r.data?.result ?? {}, null, 2)}</pre>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function App() {
  const [idsInput, setIdsInput] = useState('1,2,3,4,5')
  const ids = useMemo(() => idsInput.split(',').map(s => parseInt(s.trim(), 10)).filter(n => !Number.isNaN(n)), [idsInput])
  const [size, setSize] = useState(50000)
  const [target, setTarget] = useState(49999)
  const [apiHealth, setApiHealth] = useState<{ status: 'idle'|'ok'|'error', msg?: string }>({ status: 'idle' })

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const res = await api.streamBefore(1000)
        if (!cancelled) setApiHealth({ status: 'ok', msg: `OK (${res.executionTimeMs} ms)` })
      } catch (e: any) {
        const msg = e?.response ? `${e.response.status} ${e.response.statusText}` : (e?.message ?? 'Error')
        if (!cancelled) setApiHealth({ status: 'error', msg })
      }
    })()
    return () => { cancelled = true }
  }, [])

  return (
    <div style={{ maxWidth: 1100, margin: '24px auto', padding: '0 16px', fontFamily: 'Inter, system-ui, Arial' }}>
      <h1>Performance Demo</h1>
      <div style={{ padding: 10, borderRadius: 6, background: apiHealth.status === 'ok' ? '#e9f8ee' : apiHealth.status === 'error' ? '#fdeaea' : '#f5f5f5', color: '#333', marginBottom: 12 }}>
        <strong>API:</strong> {apiHealth.status === 'idle' ? 'Checking...' : apiHealth.status === 'ok' ? apiHealth.msg : `Error: ${apiHealth.msg}`}
      </div>
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
        <label>
          Customer IDs
          <input value={idsInput} onChange={e => setIdsInput(e.target.value)} style={{ marginLeft: 8 }} />
        </label>
        <label>
          Size
          <input type="number" value={size} onChange={e => setSize(parseInt(e.target.value, 10))} style={{ marginLeft: 8, width: 120 }} />
        </label>
        <label>
          Target
          <input type="number" value={target} onChange={e => setTarget(parseInt(e.target.value, 10))} style={{ marginLeft: 8, width: 120 }} />
        </label>
      </div>

      <Row title="1) N+1 vs Batch Query"
        onBefore={() => api.nplus1Before(ids)}
        onAfter={() => api.nplus1After(ids)}
      />

      <Row title="2) Memory Cleanup"
        onBefore={() => api.memoryBefore(size)}
        onAfter={() => api.memoryAfter(size)}
      />

      <Row title="3) Lookup: O(n) vs O(1)"
        onBefore={() => api.lookupBefore(size, target)}
        onAfter={() => api.lookupAfter(size, target)}
      />

      <Row title="4) Streams Side-Effects vs For-Loop"
        onBefore={() => api.streamBefore(size)}
        onAfter={() => api.streamAfter(size)}
      />

      <Row title="5) Cache Prefetch"
        onBefore={() => api.cacheBefore(ids)}
        onAfter={() => api.cacheAfter(ids)}
      />

      <Row title="6) Parallel Processing"
        onBefore={() => api.parallelBefore(size)}
        onAfter={() => api.parallelAfter(size)}
      />
    </div>
  )
}


