import { useEffect, useRef, useState } from 'react'
import { Icon } from './Icon'

export function Sheet({
  title,
  left,
  right,
  children,
  footer,
  onClose,
}: {
  title: string
  left?: React.ReactNode
  right?: React.ReactNode
  children: React.ReactNode
  footer?: React.ReactNode
  onClose: () => void
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="sheet" onClick={onClose}>
      <div className="sheet-card" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-head">
          <button className="link-btn" onClick={onClose}>{left ?? 'Отмена'}</button>
          <div className="ttl">{title}</div>
          <div style={{ minWidth: 56, textAlign: 'right' }}>{right}</div>
        </div>
        <div className="sheet-body">{children}</div>
        {footer ? <div className="sheet-foot">{footer}</div> : null}
      </div>
    </div>
  )
}

export function Modal({
  title,
  subtitle,
  children,
  onClose,
  wide,
}: {
  title: string
  subtitle?: string
  children: React.ReactNode
  onClose: () => void
  wide?: boolean
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="modal-wrap" onClick={onClose}>
      <div className="modal" style={wide ? { width: 720 } : undefined} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
          <div>
            <h3>{title}</h3>
            {subtitle ? <p style={{ marginBottom: 12 }}>{subtitle}</p> : null}
          </div>
          <button className="link-btn" onClick={onClose} aria-label="Закрыть">
            <Icon name="x" size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

/** Барабан выбора времени как в референсе: часы и минуты, шаг 5 */
export function TimeWheel({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [h, m] = value.split(':').map(Number)
  const hours = Array.from({ length: 24 }, (_, i) => i)
  const minutes = Array.from({ length: 12 }, (_, i) => i * 5)
  return (
    <div className="wheel">
      <WheelColumn items={hours.map((x) => String(x).padStart(2, '0'))} value={h} onPick={(i) => onChange(`${String(hours[i]).padStart(2, '0')}:${String(m).padStart(2, '0')}`)} />
      <WheelColumn items={minutes.map((x) => String(x).padStart(2, '0'))} value={Math.max(0, Math.round(minutes.indexOf(minutes.reduce((p, c) => (Math.abs(c - m) < Math.abs(p - m) ? c : p), minutes[0]))))} onPick={(i) => onChange(`${String(h).padStart(2, '0')}:${String(minutes[i]).padStart(2, '0')}`)} />
    </div>
  )
}

function WheelColumn({ items, value, onPick }: { items: string[]; value: number; onPick: (i: number) => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const lock = useRef(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    lock.current = true
    el.scrollTop = value * 44
    const t = setTimeout(() => (lock.current = false), 220)
    return () => clearTimeout(t)
  }, [value])

  return (
    <div
      className="wheel-col"
      ref={ref}
      onScroll={(e) => {
        if (lock.current) return
        const el = e.target as HTMLDivElement
        const idx = Math.round(el.scrollTop / 44)
        if (idx !== value) onPick(Math.max(0, Math.min(items.length - 1, idx)))
      }}
    >
      <div className="wheel-pad" />
      {items.map((it, i) => (
        <div key={it} className={`wheel-item${i === value ? ' on' : ''}`} onClick={() => { ref.current?.scrollTo({ top: i * 44, behavior: 'smooth' }); onPick(i) }}>
          {it}
        </div>
      ))}
      <div className="wheel-pad" />
    </div>
  )
}

export function Switch({ on, onChange }: { on: boolean; onChange: (v: boolean) => void }) {
  return <button className={`switch${on ? ' on' : ''}`} onClick={() => onChange(!on)} aria-pressed={on} />
}

export function Ring({ value, max, size = 54, label }: { value: number; max: number; size?: number; label?: string }) {
  const pct = max ? Math.min(1, value / max) : 0
  const r = size / 2 - 4
  const c = 2 * Math.PI * r
  return (
    <div style={{ position: 'relative', width: size, height: size }} title={label}>
      <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="rgba(255,255,255,0.12)" strokeWidth="3" />
        <circle
          cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#e6eaf3" strokeWidth="3"
          strokeDasharray={c} strokeDashoffset={c * (1 - pct)} strokeLinecap="round"
          style={{ transition: 'stroke-dashoffset 0.5s ease' }}
        />
      </svg>
      <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', fontSize: size > 60 ? 20 : 13.5, fontVariantNumeric: 'tabular-nums' }}>
        {value}/{max}
      </div>
    </div>
  )
}

export function useDebounced<T>(value: T, ms = 200) {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}
