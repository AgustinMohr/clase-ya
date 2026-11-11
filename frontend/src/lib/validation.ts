/** Pure form rules shared by the registration screen and its unit tests. */

export interface RegistrationForm {
  name: string;
  email: string;
  password: string;
  confirmation: string;
}

export type RegistrationErrors = Partial<Record<keyof RegistrationForm, string>>;

export function validateRegistration(form: RegistrationForm): RegistrationErrors {
  const errors: RegistrationErrors = {};
  if (!form.name.trim()) {
    errors.name = 'Ingresá tu nombre y apellido.';
  }
  if (!form.email.trim()) {
    errors.email = 'Ingresá tu email.';
  }
  if (form.password.length < 10 || !/\p{L}/u.test(form.password) || !/\d/.test(form.password)) {
    errors.password = 'Usá al menos 10 caracteres, con letras y números.';
  }
  if (form.confirmation !== form.password) {
    errors.confirmation = 'Las contraseñas no coinciden.';
  }
  return errors;
}
