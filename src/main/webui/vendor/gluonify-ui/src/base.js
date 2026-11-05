/**
 * Where the console is served, read at call time. The server writes it into the page it serves (`<base href>`, from the
 * `X-Forwarded-Prefix` header the Gluonify proxy sends when it strips a prefix): the build does not know it, its assets are
 * relative and one binary serves the root and `/<name>/` alike. Without a `<base>` (the Vite dev server), Vite's own `base`
 * applies when it is a path (`VITE_BASE`), else the root. Always with a trailing slash: `/`, `/fermion/`.
 */
export function appBase() {
  const served = typeof document === 'undefined' ? null : document.querySelector('base')?.getAttribute('href')
  if (isPath(served)) return `${trimSlashes(served)}/`
  const built = import.meta.env?.BASE_URL
  return isPath(built) ? `${trimSlashes(built)}/` : '/'
}

/** The base without its trailing slash: `''` at the root, `/fermion` under a prefix (to put in front of an absolute path). */
export function appPrefix() {
  return trimSlashes(appBase())
}

function isPath(s) {
  return typeof s === 'string' && s.startsWith('/')
}

/** `s` without its trailing slashes (a loop: no backtracking regular expression). */
function trimSlashes(s) {
  let end = s.length
  while (end > 0 && s[end - 1] === '/') end--
  return s.slice(0, end)
}
