import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import VerificationPanel from './VerificationPanel';
import type { TeacherVerification } from '../../api';

const { uploadDocument } = vi.hoisted(() => ({
  uploadDocument: vi.fn(() => Promise.resolve()),
}));

vi.mock('../../api', () => ({
  api: {
    uploadDocument,
    myDocuments: vi.fn(() => Promise.resolve([])),
  },
}));

vi.mock('../ui/Toast', () => ({
  useToast: () => ({ notify: vi.fn() }),
}));

const verification: TeacherVerification = {
  profileStatus: 'PENDING',
  credentials: [
    {
      educationId: 'education-1',
      institution: 'UNL',
      degree: 'Licenciatura',
      status: 'PENDING',
      documentCount: 0,
    },
  ],
  history: [],
};

describe('VerificationPanel', () => {
  beforeEach(() => {
    uploadDocument.mockClear();
  });

  it('uploads the selected PDF without an extra submit button', async () => {
    render(<VerificationPanel verification={verification} onChanged={vi.fn()} />);

    const file = new File(['%PDF-1.4'], 'diploma.pdf', { type: 'application/pdf' });
    fireEvent.change(screen.getByLabelText('Archivo (PDF)'), { target: { files: [file] } });

    await waitFor(() => expect(uploadDocument).toHaveBeenCalledTimes(1));
    expect(uploadDocument).toHaveBeenCalledWith('education-1', 'DIPLOMA', file);
    expect(screen.queryByRole('button', { name: 'Subir' })).toBeNull();
  });
});
