export const ROUTES = {
  home: '/',
  search: '/buscar',
  teacher: (teacherId: string) => `/profesores/${encodeURIComponent(teacherId)}`,
  favorites: '/favoritos',
  messages: '/mensajes',
  conversation: (conversationId: string) => `/mensajes/${encodeURIComponent(conversationId)}`,
  myListing: '/mi-anuncio',
  register: '/registro',
  activate: '/activar',
};

/** Search URL with only the free-text term, used by the landing and category cards. */
export function searchPath(term: string): string {
  const trimmed = term.trim();
  return trimmed ? `${ROUTES.search}?materia=${encodeURIComponent(trimmed)}` : ROUTES.search;
}

const PRIVATE_PREFIXES = [ROUTES.favorites, ROUTES.messages, ROUTES.myListing];

export function isPrivatePath(pathname: string): boolean {
  return PRIVATE_PREFIXES.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}
