import { Link } from 'react-router';
import { SearchX } from 'lucide-react';
import { usePageMeta } from '../hooks/usePageMeta';
import { buttonStyles } from '../components/ui/Button';
import { EmptyState } from '../components/ui/primitives';
import { ROUTES } from '../routes';

export default function NotFoundPage() {
  usePageMeta({
    title: 'Página no encontrada | ClaseYa',
    description: 'La página que buscás no existe o cambió de dirección.',
  });

  return (
    <div className="container-page py-16">
      <EmptyState
        icon={<SearchX className="h-6 w-6" aria-hidden="true" />}
        title="Página no encontrada"
        description="La dirección que abriste no existe o cambió. Volvé al inicio para seguir buscando."
        action={
          <Link to={ROUTES.home} className={buttonStyles({ size: 'md' })}>
            Volver al inicio
          </Link>
        }
      />
    </div>
  );
}
