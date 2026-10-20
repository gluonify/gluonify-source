/** Extracts the roles of a JWT: claims `roles`, `realm_access.roles` and `scope` (space separated). Tolerant: never throws. */
export function rolesFromToken(jwt) {
  try {
    const part = String(jwt || '').split('.')[1]
    if (!part) return []
    const b64 = part.replaceAll('-', '+').replaceAll('_', '/')
    const bytes = Uint8Array.from(atob(b64.padEnd(Math.ceil(b64.length / 4) * 4, '=')), (c) => c.codePointAt(0))
    const claims = JSON.parse(new TextDecoder().decode(bytes))
    const out = new Set()
    const add = (v) => { if (Array.isArray(v)) v.forEach((x) => typeof x === 'string' && x && out.add(x)) }
    add(claims.roles)
    add(claims.realm_access?.roles)
    if (typeof claims.scope === 'string') claims.scope.split(/\s+/).filter(Boolean).forEach((s) => out.add(s))
    return [...out]
  } catch {
    return []
  }
}

/** True when `role` matches `pattern`: exact, or a trailing wildcard (`gluonify:fermion:*`, `*`). */
export function roleMatches(pattern, role) {
  if (pattern === '*') return true
  if (pattern.endsWith('*')) return role.startsWith(pattern.slice(0, -1))
  return pattern === role
}

/** Entries the person may see: one of their roles matches one of the entry's requiredRoles. */
export function visibleApps(catalog, roles) {
  const mine = Array.isArray(roles) ? roles : []
  return (Array.isArray(catalog) ? catalog : []).filter((app) =>
    (app.requiredRoles || []).some((p) => mine.some((r) => roleMatches(p, r))))
}
