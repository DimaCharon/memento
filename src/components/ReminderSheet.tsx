import { useState } from 'react'
import { Icon, ICON_CHOICES } from './Icon'
import { Sheet, TimeWheel } from './Sheet'
import { useStore } from '../store'
import type { Priority, Reminder, Repeat } from '../types'
import { addDaysISO, fullDateLabel, leadLabel, priorityLabel, repeatLabel, todayISO } from '../lib/date'

const REPEATS: Repeat[] = ['none', 'daily', 'weekdays', 'weekly', 'monthly']
const PRIORITIES: Priority[] = ['low', 'medium', 'high']
const LEADS = [0, 5, 10, 30, 60, 1440]

export function ReminderSheet({ reminder, onClose }: { reminder: Reminder | 'new'; onClose: () => void }) {
  const { addReminder, updateReminder, deleteReminder, toast } = useStore()
  const isNew = reminder === 'new'
  const r = isNew ? null : reminder

  const [title, setTitle] = useState(r?.title || '')
  const [icon, setIcon] = useState(r?.icon || 'bell')
  const [date, setDate] = useState(r?.date || todayISO())
  const [time, setTime] = useState(r?.time || '09:00')
  const [repeat, setRepeat] = useState<Repeat>(r?.repeat || 'none')
  const [priority, setPriority] = useState<Priority>(r?.priority || 'medium')
  const [notes, setNotes] = useState(r?.notes || '')
  const [place, setPlace] = useState(r?.place || '')
  const [lead, setLead] = useState(r?.leadMinutes ?? 10)
  const [picker, setPicker] = useState<null | 'time' | 'repeat' | 'priority' | 'lead' | 'icon'>(null)

  const save = () => {
    const payload = { title: title.trim() || 'Новое напоминание', icon, date, time, repeat, priority, notes, place, leadMinutes: lead }
    if (isNew) {
      addReminder({ ...payload, source: 'Пользователь' })
      toast(`Напоминание создано на ${date} в ${time}`, 'ok')
    } else {
      updateReminder(r!.id, payload)
      toast('Изменения сохранены', 'ok')
    }
    onClose()
  }

  return (
    <Sheet
      title={isNew ? 'Новое напоминание' : 'Напоминание'}
      left="Отмена"
      right={<button className="link-btn" onClick={save}><b>Готово</b></button>}
      onClose={onClose}
      footer={
        <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
          <button className="btn" onClick={save}>Сохранить</button>
          {!isNew ? (
            <button
              className="btn danger"
              onClick={() => {
                deleteReminder(r!.id)
                onClose()
              }}
            >
              <Icon name="trash" size={17} /> Удалить напоминание
            </button>
          ) : null}
        </div>
      }
    >
      <div className="field">
        <button className="agent-ico" style={{ width: 42, height: 42, borderRadius: 14 }} onClick={() => setPicker(picker === 'icon' ? null : 'icon')} title="Выбрать иконку">
          <Icon name={icon} size={19} />
        </button>
        <div className="f-body">
          <div className="f-label">Название</div>
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Например: позвонить клиенту" autoFocus={isNew} />
        </div>
      </div>

      {picker === 'icon' ? (
        <div className="chips" style={{ marginTop: 10 }}>
          {ICON_CHOICES.map((k) => (
            <button key={k} className={`chip${icon === k ? ' on' : ''}`} onClick={() => { setIcon(k); setPicker(null) }}>
              <Icon name={k} size={15} />
            </button>
          ))}
        </div>
      ) : null}

      <button className="field" style={{ width: '100%', textAlign: 'left' }} onClick={() => document.getElementById('memento-date-input')?.click()}>
        <span className="f-ico"><Icon name="calendar" size={18} /></span>
        <div className="f-body">
          <div className="f-label">Дата</div>
          <div className="f-value">{fullDateLabel(date)}</div>
        </div>
        <Icon name="chevronRight" size={17} className="chev" />
        <input
          id="memento-date-input"
          type="date"
          value={date}
          onChange={(e) => e.target.value && setDate(e.target.value)}
          style={{ position: 'absolute', width: 1, height: 1, opacity: 0, pointerEvents: 'none' }}
          tabIndex={-1}
          aria-hidden="true"
        />
      </button>
      <div className="chips" style={{ marginTop: 9 }}>
        {[
          ['Сегодня', todayISO()],
          ['Завтра', addDaysISO(todayISO(), 1)],
          ['+2 дня', addDaysISO(todayISO(), 2)],
          ['Через неделю', addDaysISO(todayISO(), 7)],
        ].map(([label, d]) => (
          <button key={label} className={`chip${date === d ? ' on' : ''}`} onClick={() => setDate(d as string)}>{label}</button>
        ))}
        <input
          type="date"
          value={date}
          onChange={(e) => e.target.value && setDate(e.target.value)}
          style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid var(--stroke)', borderRadius: 20, padding: '6px 10px', fontSize: 12, colorScheme: 'dark' }}
        />
      </div>

      <button className="field" style={{ marginTop: 9, width: '100%', textAlign: 'left' }} onClick={() => setPicker(picker === 'time' ? null : 'time')}>
        <span className="f-ico"><Icon name="clock" size={18} /></span>
        <div className="f-body">
          <div className="f-label">Время</div>
          <div className="f-value" style={{ fontSize: 15 }}>{time}</div>
        </div>
        <Icon name={picker === 'time' ? 'chevronUp' : 'chevronDown'} size={17} className="chev" />
      </button>
      {picker === 'time' ? <TimeWheel value={time} onChange={setTime} /> : null}

      <button className="field" style={{ marginTop: 9, width: '100%', textAlign: 'left' }} onClick={() => setPicker(picker === 'repeat' ? null : 'repeat')}>
        <span className="f-ico"><Icon name="repeat" size={18} /></span>
        <div className="f-body">
          <div className="f-label">Повтор</div>
        </div>
        <span style={{ fontSize: 13 }}>{repeatLabel(repeat)}</span>
        <Icon name="chevronRight" size={17} className="chev" />
      </button>
      {picker === 'repeat' ? (
        <div className="chips" style={{ marginTop: 9 }}>
          {REPEATS.map((x) => (
            <button key={x} className={`chip${repeat === x ? ' on' : ''}`} onClick={() => { setRepeat(x); setPicker(null) }}>{repeatLabel(x)}</button>
          ))}
        </div>
      ) : null}

      <div className="field" style={{ marginTop: 9, alignItems: 'flex-start' }}>
        <span className="f-ico" style={{ marginTop: 3 }}><Icon name="note" size={18} /></span>
        <div className="f-body">
          <div className="f-label">Заметки</div>
          <textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Что уточнить, контекст, ссылки" />
        </div>
      </div>

      <div className="field" style={{ marginTop: 9 }}>
        <span className="f-ico"><Icon name="pin" size={18} /></span>
        <div className="f-body">
          <div className="f-label">Место</div>
          <input value={place} onChange={(e) => setPlace(e.target.value)} placeholder="Офис / Онлайн" />
        </div>
      </div>

      <button className="field" style={{ marginTop: 9, width: '100%', textAlign: 'left' }} onClick={() => setPicker(picker === 'lead' ? null : 'lead')}>
        <span className="f-ico"><Icon name="bell" size={18} /></span>
        <div className="f-body">
          <div className="f-label">Уведомление</div>
        </div>
        <span style={{ fontSize: 13 }}>{leadLabel(lead)}</span>
        <Icon name="chevronRight" size={17} className="chev" />
      </button>
      {picker === 'lead' ? (
        <div className="chips" style={{ marginTop: 9 }}>
          {LEADS.map((x) => (
            <button key={x} className={`chip${lead === x ? ' on' : ''}`} onClick={() => { setLead(x); setPicker(null) }}>{leadLabel(x)}</button>
          ))}
        </div>
      ) : null}

      <button className="field" style={{ marginTop: 9, width: '100%', textAlign: 'left' }} onClick={() => setPicker(picker === 'priority' ? null : 'priority')}>
        <span className="f-ico"><Icon name="flag" size={18} /></span>
        <div className="f-body">
          <div className="f-label">Приоритет</div>
        </div>
        <span className={`dot ${priority}`} />
        <span style={{ fontSize: 13 }}>{priorityLabel(priority)}</span>
        <Icon name="chevronRight" size={17} className="chev" />
      </button>
      {picker === 'priority' ? (
        <div className="chips" style={{ marginTop: 9 }}>
          {PRIORITIES.map((x) => (
            <button key={x} className={`chip${priority === x ? ' on' : ''}`} onClick={() => { setPriority(x); setPicker(null) }}>
              <span className={`dot ${x}`} /> {priorityLabel(x)}
            </button>
          ))}
        </div>
      ) : null}
    </Sheet>
  )
}
