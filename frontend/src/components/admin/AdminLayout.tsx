import { Flag, LayoutDashboard, MessageSquare, ShieldCheck, Users, type LucideIcon } from 'lucide-react';
import { NavLink } from 'react-router';
import { buttonStyles } from '../ui/Button';
import { cn } from '../../lib/cn';
import { ROUTES } from '../../routes';

/**
 * Admin navigation (ADMIN-001). Sections are declared here once; a section is only rendered as a
 * link when it is `enabled`, so the next modules never expose dead links.
 */
interface AdminSection {
  id: string;
  label: string;
  icon: LucideIcon;
  path: string;
  enabled: boolean;
}

export const ADMIN_SECTIONS: AdminSection[] = [
  { id: 'overview', label: 'Resumen', icon: LayoutDashboard, path: ROUTES.admin, enabled: true },
  { id: 'users', label: 'Usuarios', icon: Users, path: `${ROUTES.admin}/usuarios`, enabled: true },
  {
    id: 'verification',
    label: 'Verificación',
    icon: ShieldCheck,
    path: `${ROUTES.admin}/verificacion`,
    enabled: true,
  },
  {
    id: 'reviews',
    label: 'Opiniones',
    icon: MessageSquare,
    path: ROUTES.adminReviews,
    enabled: true,
  },
  {
    id: 'reports',
    label: 'Reportes',
    icon: Flag,
    path: ROUTES.adminReports,
    enabled: true,
  },
];

const VISIBLE_SECTIONS = ADMIN_SECTIONS.filter((section) => section.enabled);

/** Sidebar on `lg+`, horizontal tabs on mobile. The page itself never scrolls sideways. */
export function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="container-page py-8">
      <div className="lg:grid lg:grid-cols-[240px_1fr] lg:gap-8">
        <nav aria-label="Administración" className="mb-6 lg:mb-0">
          <ul className="flex gap-2 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible lg:pb-0">
            {VISIBLE_SECTIONS.map(({ id, label, icon: Icon, path }) => (
              <li key={id}>
                <NavLink
                  to={path}
                  end
                  className={({ isActive }) =>
                    cn(
                      buttonStyles({ variant: isActive ? 'secondary' : 'ghost', size: 'md' }),
                      'w-full justify-start whitespace-nowrap',
                    )
                  }
                >
                  <Icon className="h-4 w-4" aria-hidden="true" />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="min-w-0">{children}</div>
      </div>
    </div>
  );
}
