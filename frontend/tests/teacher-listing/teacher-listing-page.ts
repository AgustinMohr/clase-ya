import { expect, type Locator, type Page } from '@playwright/test';
import { BasePage } from '../base-page';

/**
 * "Mi anuncio" del profesor (TEACHER-001, slice A): completitud, perfil, materias, modalidades,
 * formación, disponibilidad y el panel de verificación (slice B).
 */
export class TeacherListingPage extends BasePage {
  readonly heading: Locator;
  readonly publishedBadge: Locator;
  readonly bio: Locator;
  readonly saveProfile: Locator;
  readonly institution: Locator;
  readonly degree: Locator;
  readonly startYear: Locator;
  readonly addEducation: Locator;
  readonly credentials: Locator;
  readonly subjectTags: Locator;

  constructor(page: Page) {
    super(page);
    this.heading = page.getByRole('heading', { name: 'Mi anuncio' });
    this.publishedBadge = page.getByText('Publicado', { exact: true });
    this.bio = page.getByLabel('Bio');
    this.saveProfile = page.getByRole('button', { name: /Guardar cambios|Crear perfil/ });
    this.institution = page.getByLabel('Institución');
    this.degree = page.getByLabel('Título');
    this.startYear = page.getByLabel('Año de inicio');
    this.addEducation = page.getByRole('button', { name: 'Agregar formación' });
    this.credentials = page.getByTestId('credential-row');
    this.subjectTags = page.getByTestId('subject-tag');
  }

  async open(): Promise<void> {
    await this.gotoHome();
    await this.page.getByRole('button', { name: 'Mi anuncio', exact: true }).click();
    await expect(this.heading).toBeVisible();
  }

  /** The verification row of the credential with this degree. */
  credentialRow(degree: string): Locator {
    return this.credentials.filter({ hasText: degree }).first();
  }
}
