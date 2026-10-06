import { expect, type Locator, type Page } from '@playwright/test';
import { BasePage } from '../base-page';

export interface StudentProfileValues {
  currentUniversity: string;
  currentFaculty: string;
  currentCareer: string;
  currentYear: string;
}

/**
 * "Mi perfil" dialog for the student profile (CONTACT-001). It is a prerequisite of the
 * product's core flow: without a profile the API rejects contacting and favorites.
 */
export class StudentProfileDialog extends BasePage {
  readonly dialog: Locator;
  readonly university: Locator;
  readonly faculty: Locator;
  readonly career: Locator;
  readonly year: Locator;
  readonly bio: Locator;
  readonly saveButton: Locator;
  readonly savedLine: Locator;

  constructor(page: Page) {
    super(page);
    this.dialog = page.getByRole('dialog');
    this.university = this.dialog.getByLabel('Universidad');
    this.faculty = this.dialog.getByLabel('Facultad');
    this.career = this.dialog.getByLabel('Carrera');
    this.year = this.dialog.getByLabel('Año que cursás');
    this.bio = this.dialog.getByLabel('Sobre vos');
    this.saveButton = this.dialog.getByRole('button', { name: 'Guardar perfil' });
    this.savedLine = this.dialog.getByText('Guardado:');
  }

  async openFromNavbar(): Promise<void> {
    await this.page.getByRole('button', { name: 'Mi perfil' }).click();
    await expect(this.dialog).toBeVisible();
  }

  /** Values currently selected in the form, after the cascade has resolved. */
  async currentValues(): Promise<StudentProfileValues> {
    await expect(this.faculty).not.toHaveValue('');
    await expect(this.career).not.toHaveValue('');
    return {
      currentUniversity: await this.university.inputValue(),
      currentFaculty: await this.faculty.inputValue(),
      currentCareer: await this.career.inputValue(),
      currentYear: await this.year.inputValue(),
    };
  }

  private async optionValues(select: Locator): Promise<string[]> {
    return select
      .locator('option')
      .evaluateAll((options) => options.map((option) => (option as HTMLOptionElement).value).filter(Boolean));
  }

  /**
   * Moves the profile to another university and picks its first faculty and career. A single
   * faculty may have a single career (UTN in the demo catalog), so the change spans the whole
   * cascade: that is also the case that used to lose the selection after saving.
   */
  async selectAnotherUniversityFacultyAndCareer(
    currentUniversity: string,
  ): Promise<{ university: string; faculty: string; career: string }> {
    const university = (await this.optionValues(this.university)).find((value) => value !== currentUniversity);
    if (!university) {
      throw new Error('The catalog exposes a single university: cannot change it');
    }
    await this.university.selectOption(university);
    await expect
      .poll(() => this.optionValues(this.faculty), { message: 'las facultades deben cargar' })
      .not.toHaveLength(0);
    const [faculty] = await this.optionValues(this.faculty);
    await this.faculty.selectOption(faculty);
    await expect
      .poll(() => this.optionValues(this.career), { message: 'las carreras deben cargar' })
      .not.toHaveLength(0);
    const [career] = await this.optionValues(this.career);
    await this.career.selectOption(career);
    return { university, faculty, career };
  }

  async save(): Promise<void> {
    await this.saveButton.click();
    await expect(this.dialog).toBeHidden();
  }
}
