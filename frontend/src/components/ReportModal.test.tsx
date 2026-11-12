import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ReportModal from './ReportModal';

const mocks = vi.hoisted(() => ({
  reportUser: vi.fn(),
}));

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    api: {
      ...actual.api,
      reportUser: mocks.reportUser,
    },
  };
});

const REPORT = {
  id: 'report-1',
  status: 'OPEN',
  reason: 'HARASSMENT',
  createdAt: '2026-11-11T12:00:00Z',
};

describe('ReportModal', () => {
  beforeEach(() => {
    mocks.reportUser.mockReset();
  });

  it('sends the selected reason and description', async () => {
    mocks.reportUser.mockResolvedValue(REPORT);
    const onReported = vi.fn();

    render(
      <ReportModal
        open
        reportedUserId="user-1"
        reportedName="Bruno"
        onClose={vi.fn()}
        onReported={onReported}
      />,
    );

    fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: 'SPAM' } });
    fireEvent.change(screen.getByLabelText('Detalle'), { target: { value: 'Publicidad repetida' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar reporte' }));

    await waitFor(() =>
      expect(mocks.reportUser).toHaveBeenCalledWith('user-1', 'SPAM', 'Publicidad repetida'),
    );
    expect(onReported).toHaveBeenCalledWith(REPORT);
  });

  it('explains a duplicate open report', async () => {
    const { ApiError } = await import('../api');
    mocks.reportUser.mockRejectedValue(new ApiError(409, 'Conflict'));

    render(
      <ReportModal
        open
        reportedUserId="user-1"
        reportedName="Bruno"
        onClose={vi.fn()}
        onReported={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Enviar reporte' }));

    expect(
      await screen.findByText(
        'Ya reportaste a este usuario. Administración está revisando el caso.',
      ),
    ).toBeInTheDocument();
  });
});
