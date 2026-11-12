import { GraduationCap } from 'lucide-react';
import { Link } from 'react-router';
import { ROUTES } from '../../routes';

const COLUMNS = [
  {
    title: 'Materias',
    links: ['Matemática', 'Física', 'Química', 'Programación', 'Inglés', 'Lengua'],
  },
  {
    title: 'Estudiantes',
    links: ['Cómo funciona', 'Consejos de estudio', 'Preguntas frecuentes'],
  },
  {
    title: 'Profesores',
    links: ['Publicar mi anuncio', 'Requisitos', 'Ser profesor verificado'],
  },
];

export function Footer({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <footer className="border-t border-border bg-surface">
      <div className="container-page grid gap-10 py-12 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="grid h-9 w-9 place-items-center rounded-xl bg-primary-600 text-white">
              <GraduationCap className="h-5 w-5" aria-hidden="true" />
            </span>
            <span className="font-display text-2xl text-primary-700 dark:text-primary-200">ClaseYa</span>
          </div>
          <p className="mt-3 max-w-xs text-sm text-content-muted">
            Encontrá profesores particulares por materia, modalidad y zona. Contactalos gratis.
          </p>
        </div>

        {COLUMNS.map((column) => (
          <div key={column.title}>
            <h3 className="text-base font-bold">{column.title}</h3>
            <ul className="mt-3 space-y-2 text-sm text-content-muted">
              {column.links.map((link) => (
                <li key={link}>
                  <button type="button" onClick={onNavigate} className="transition-colors hover:text-primary-600">
                    {link}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-border">
        <div className="container-page flex flex-col items-center justify-between gap-2 py-5 text-xs text-content-muted sm:flex-row">
          <p>Ac {new Date().getFullYear()} ClaseYa A� Santa Fe, Argentina</p>
          <p className="flex flex-wrap items-center gap-2">
            <Link to={ROUTES.terms} className="transition-colors hover:text-primary-600">
              TAcrminos
            </Link>
            <span aria-hidden="true">A�</span>
            <Link to={ROUTES.privacy} className="transition-colors hover:text-primary-600">
              Privacidad
            </Link>
            <span aria-hidden="true">A�</span>
            <Link to={ROUTES.help} className="transition-colors hover:text-primary-600">
              Ayuda
            </Link>
          </p>
        </div>
      </div>
    </footer>
  );
}
