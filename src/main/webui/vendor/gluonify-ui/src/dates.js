/**
 * Dates and times as every Gluonify console writes them: ISO 8601 in every language, in the browser's time zone
 * (README, "Dates and times"; decision of the user, 2026-11-03). `2027-11-03` for a date, `2027-11-03 14:30` or
 * `2027-11-03 14:30:00` with a time. Never a locale-dependent form.
 *
 * An instant is a `Date`, a number of milliseconds since the epoch, or a string `Date` parses (an ISO 8601 timestamp
 * from the API). A date alone (`2027-11-03`) is a calendar day, not UTC midnight: it renders as written in every time
 * zone. Anything else (null, empty, unparsable, NaN) renders as `''`, so the console decides what an absent value shows.
 */

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/

/** `2027-11-03`, or `''` when `instant` is absent or invalid. */
export function formatDate(instant) {
  const d = toDate(instant)
  return d ? datePart(d) : ''
}

/** `2027-11-03 14:30`, or `2027-11-03 14:30:00` with `{ seconds: true }`; `''` when `instant` is absent or invalid. */
export function formatDateTime(instant, { seconds = false } = {}) {
  const d = toDate(instant)
  if (!d) return ''
  const parts = [d.getHours(), d.getMinutes()]
  if (seconds) parts.push(d.getSeconds())
  return `${datePart(d)} ${parts.map(pad).join(':')}`
}

/** The local `Date` of `instant`, or null. */
function toDate(instant) {
  let d = null
  if (instant instanceof Date) d = new Date(instant)
  else if (typeof instant === 'number') d = new Date(instant)
  else if (typeof instant === 'string' && instant.trim() !== '') d = parse(instant.trim())
  return d && Number.isFinite(d.getTime()) ? d : null
}

function parse(text) {
  const day = DATE_ONLY.exec(text)
  if (!day) return new Date(text)
  const [year, month, date] = [Number(day[1]), Number(day[2]), Number(day[3])]
  const d = new Date(2000, 0, 1) // local midnight; setFullYear keeps years below 100 as written
  d.setFullYear(year, month - 1, date)
  // `2027-02-30` would roll over to March: a day that does not exist is invalid, not another day.
  return d.getFullYear() === year && d.getMonth() === month - 1 && d.getDate() === date ? d : null
}

function datePart(d) {
  return `${String(d.getFullYear()).padStart(4, '0')}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function pad(n) {
  return String(n).padStart(2, '0')
}
