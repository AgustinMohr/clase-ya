import { describe, expect, it } from 'vitest';
import { isPrivatePath, searchPath } from './routes';

describe('searchPath', () => {
  it('puts the trimmed term in the query string', () => {
    expect(searchPath('  fisica  ')).toBe('/buscar?materia=fisica');
  });

  it('encodes reserved characters', () => {
    expect(searchPath('a&b')).toBe('/buscar?materia=a%26b');
  });

  it('returns the bare search route without a term', () => {
    expect(searchPath('   ')).toBe('/buscar');
  });
});

describe('isPrivatePath', () => {
  it('matches the private routes and their children', () => {
    expect(isPrivatePath('/favoritos')).toBe(true);
    expect(isPrivatePath('/favoritos/abc')).toBe(true);
    expect(isPrivatePath('/mensajes/abc')).toBe(true);
    expect(isPrivatePath('/mi-anuncio')).toBe(true);
  });

  it('does not match public routes or lookalike prefixes', () => {
    expect(isPrivatePath('/')).toBe(false);
    expect(isPrivatePath('/registro')).toBe(false);
    expect(isPrivatePath('/favoritosx')).toBe(false);
  });
});
