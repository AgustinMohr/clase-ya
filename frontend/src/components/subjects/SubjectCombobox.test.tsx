import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import SubjectCombobox from './SubjectCombobox';

describe('SubjectCombobox', () => {
  it('searches with debounce and selects a subject', async () => {
    const loadSubjects = vi.fn(() => Promise.resolve([{ id: 'subject-1', name: 'Matemática' }]));
    const onSelect = vi.fn();

    render(<SubjectCombobox id="subject" loadSubjects={loadSubjects} onSelect={onSelect} />);

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ma' } });

    await waitFor(() => expect(loadSubjects).toHaveBeenCalledWith('ma'));
    const option = await screen.findByRole('option', { name: /Matemática/ });
    fireEvent.click(option.querySelector('button')!);

    await waitFor(() => expect(onSelect).toHaveBeenCalledWith({ id: 'subject-1', name: 'Matemática' }));
    expect(screen.getByRole('combobox')).toHaveValue('');
  });

  it('clears the term without selecting anything', () => {
    render(
      <SubjectCombobox
        id="subject"
        loadSubjects={vi.fn(() => Promise.resolve([]))}
        onSelect={vi.fn()}
      />,
    );

    const input = screen.getByRole('combobox');
    fireEvent.change(input, { target: { value: 'mate' } });
    fireEvent.click(screen.getByRole('button', { name: 'Limpiar búsqueda' }));

    expect(input).toHaveValue('');
  });
});
