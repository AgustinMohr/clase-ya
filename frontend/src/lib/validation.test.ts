import { describe, expect, it } from 'vitest';
import { validateRegistration } from './validation';

const valid = { email: 'alumno@example.com', password: 'Password123', confirmation: 'Password123' };

describe('validateRegistration', () => {
  it('accepts a valid form', () => {
    expect(validateRegistration(valid)).toEqual({});
  });

  it('requires an email', () => {
    expect(validateRegistration({ ...valid, email: '   ' })).toEqual({
      email: 'Ingresá tu email.',
    });
  });

  it('requires at least 10 characters', () => {
    expect(validateRegistration({ ...valid, password: 'Password1', confirmation: 'Password1' })).toEqual({
      password: 'Usá al menos 10 caracteres, con letras y números.',
    });
  });

  it('requires a letter and a digit', () => {
    expect(validateRegistration({ ...valid, password: 'passwordonly', confirmation: 'passwordonly' })).toEqual({
      password: 'Usá al menos 10 caracteres, con letras y números.',
    });
    expect(validateRegistration({ ...valid, password: '1234567890', confirmation: '1234567890' })).toEqual({
      password: 'Usá al menos 10 caracteres, con letras y números.',
    });
  });

  it('requires the confirmation to match', () => {
    expect(validateRegistration({ ...valid, confirmation: 'Password124' })).toEqual({
      confirmation: 'Las contraseñas no coinciden.',
    });
  });
});
