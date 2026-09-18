import { Atom, BookText, Calculator, Code2, FlaskConical, GraduationCap, Languages, PieChart, Sigma } from 'lucide-react';
import { Card } from '../ui/primitives';

const ICONS: Array<{ match: RegExp; Icon: typeof Atom }> = [
  { match: /matem|álgebra|algebra|análisis|analisis/i, Icon: Sigma },
  { match: /física|fisica/i, Icon: Atom },
  { match: /química|quimica/i, Icon: FlaskConical },
  { match: /program|comput|sistem|software/i, Icon: Code2 },
  { match: /inglés|ingles|idioma|lengua extranjera/i, Icon: Languages },
  { match: /lengua|literatura|castellano/i, Icon: BookText },
  { match: /contab|econom|admin/i, Icon: PieChart },
  { match: /.*/, Icon: Calculator },
];

function iconFor(name: string) {
  const found = ICONS.find((entry) => entry.match.test(name));
  const Icon = found ? found.Icon : GraduationCap;
  return <Icon className="h-6 w-6" aria-hidden="true" />;
}

export function CategoryCard({ name, onClick }: { name: string; onClick: (name: string) => void }) {
  return (
    <Card
      interactive
      className="flex items-center gap-3 p-4"
      role="button"
      tabIndex={0}
      onClick={() => onClick(name)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onClick(name);
        }
      }}
    >
      <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-primary-50 text-primary-700 dark:bg-primary-900/40 dark:text-primary-200">
        {iconFor(name)}
      </span>
      <span className="min-w-0">
        <span className="block truncate font-semibold">{name}</span>
        <span className="block text-xs text-content-muted">Ver profesores</span>
      </span>
    </Card>
  );
}
