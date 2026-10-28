import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Field, Input } from './Field';

describe('Field', () => {
  it('wires the label and exposes the error to assistive tech', () => {
    render(
      <Field label="Email" error="Requerido">
        {(props) => <Input {...props} />}
      </Field>,
    );

    const input = screen.getByLabelText('Email');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Requerido');
    expect(screen.getByRole('alert')).toHaveTextContent('Requerido');
  });

  it('shows the hint when there is no error', () => {
    render(
      <Field label="Email" hint="Usá tu email principal">
        {(props) => <Input {...props} />}
      </Field>,
    );

    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Usá tu email principal');
  });
});
