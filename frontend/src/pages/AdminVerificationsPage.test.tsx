import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminVerificationsPage from './AdminVerificationsPage';

const apiMocks = vi.hoisted(() => ({
  adminVerificationQueue: vi.fn(),
  adminVerificationDetail: vi.fn(),
  reviewDocument: vi.fn(),
  decideCredential: vi.fn(),
}));
const notify = vi.hoisted(() => vi.fn());

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    api: {
      ...actual.api,
      adminVerificationQueue: apiMocks.adminVerificationQueue,
      adminVerificationDetail: apiMocks.adminVerificationDetail,
      reviewDocument: apiMocks.reviewDocument,
      decideCredential: apiMocks.decideCredential,
    },
  };
});
vi.mock('../components/ui/Toast', () => ({ useToast: () => ({ notify }) }));

const QUEUE = {
  content: [
    {
      educationId: 'e1',
      teacherId: 't1',
      teacherName: 'Ana Profe',
      institution: 'UTN',
      degree: 'Ingeniero',
      status: 'UNDER_REVIEW' as const,
      submittedAt: '2026-10-01T12:00:00Z',
      documentCount: 1,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

const DOCUMENT = {
  id: 'd1',
  educationId: 'e1',
  type: 'DIPLOMA' as const,
  originalFilename: 'diploma.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  uploadedAt: '2026-10-01T12:00:00Z',
  scanStatus: 'UNSCANNED' as const,
};

function detail(reviewed: boolean) {
  return {
    teacherId: 't1',
    teacherName: 'Ana Profe',
    profileStatus: 'UNDER_REVIEW' as const,
    credentials: [
      {
        educationId: 'e1',
        institution: 'UTN',
        degree: 'Ingeniero',
        status: 'UNDER_REVIEW' as const,
        submittedAt: '2026-10-01T12:00:00Z',
        documentCount: 1,
      },
    ],
    documents: [
      reviewed
        ? { ...DOCUMENT, reviewedAt: '2026-10-02T12:00:00Z', reviewedByName: 'Admin' }
        : DOCUMENT,
    ],
    history: [],
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/verificacion']}>
      <AdminVerificationsPage />
    </MemoryRouter>,
  );
}

async function openDetail() {
  apiMocks.adminVerificationDetail.mockResolvedValue(detail(false));
  renderPage();
  fireEvent.click(await screen.findByRole('button', { name: /Ana Profe/ }));
  await screen.findByText('diploma.pdf');
}

describe('AdminVerificationsPage', () => {
  beforeEach(() => {
    apiMocks.adminVerificationQueue.mockResolvedValue(QUEUE);
    apiMocks.reviewDocument.mockResolvedValue(undefined);
    apiMocks.decideCredential.mockResolvedValue({});
  });

  it('shows the queue and the detail with the scan status', async () => {
    await openDetail();

    expect(screen.getByText('Sin escanear')).toBeInTheDocument();
    expect(screen.getByText('0 de 1 documento(s) revisados')).toBeInTheDocument();
  });

  it('marks a document as reviewed', async () => {
    await openDetail();

    fireEvent.click(screen.getByLabelText('Marcar diploma.pdf como revisado'));

    await waitFor(() => expect(apiMocks.reviewDocument).toHaveBeenCalledWith('d1', true));
  });

  it('blocks VERIFIED while a document is unreviewed', async () => {
    await openDetail();

    fireEvent.click(screen.getByRole('button', { name: 'Decidir' }));

    expect(screen.getByText('1 documento(s) sin revisar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Registrar decisión' })).toBeDisabled();
  });

  it('submits VERIFIED once every document was reviewed', async () => {
    apiMocks.adminVerificationDetail.mockResolvedValue(detail(true));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /Ana Profe/ }));
    await screen.findByText('diploma.pdf');

    fireEvent.click(screen.getByRole('button', { name: 'Decidir' }));
    fireEvent.click(screen.getByRole('button', { name: 'Registrar decisión' }));

    await waitFor(() =>
      expect(apiMocks.decideCredential).toHaveBeenCalledWith('t1', 'e1', {
        decision: 'VERIFIED',
        method: 'DOCUMENT_ANALYSIS',
        reason: undefined,
      }),
    );
    expect(notify).toHaveBeenCalledWith('Decisión registrada', 'success');
  });
});
