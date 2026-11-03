import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Pagination } from './Pagination';

describe('Pagination', () => {
  it('renders nothing with a single page', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onChange={vi.fn()} />);

    expect(container).toBeEmptyDOMElement();
  });

  it('shows the 1-based position and moves between pages', () => {
    const onChange = vi.fn();
    render(<Pagination page={1} totalPages={3} onChange={onChange} />);

    expect(screen.getByText('Página 2 de 3')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Siguiente/ }));
    expect(onChange).toHaveBeenCalledWith(2);

    fireEvent.click(screen.getByRole('button', { name: /Anterior/ }));
    expect(onChange).toHaveBeenCalledWith(0);
  });

  it('disables the edges and while loading', () => {
    render(<Pagination page={0} totalPages={2} onChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: /Anterior/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Siguiente/ })).toBeEnabled();
  });

  it('disables both controls while a request is in flight', () => {
    render(<Pagination page={1} totalPages={3} loading onChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: /Anterior/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Siguiente/ })).toBeDisabled();
  });
});
