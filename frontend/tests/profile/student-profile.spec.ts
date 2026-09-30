import { expect, test } from '@playwright/test';
import { loginAs } from '../helpers';
import { StudentProfileDialog } from './student-profile-dialog';

test.describe('Perfil de estudiante', () => {
  test(
    'guarda la carrera y la conserva al reabrir el formulario',
    { tag: ['@critical', '@e2e', '@profile', '@PROFILE-E2E-001'] },
    async ({ page }) => {
      await loginAs(page);

      const dialog = new StudentProfileDialog(page);
      await dialog.gotoHome();
      await dialog.openFromNavbar();
      await expect(dialog.savedLine).toBeVisible();

      // Prefill of the first open: the whole cascade comes from the saved profile.
      const before = await dialog.currentValues();

      const target = await dialog.selectAnotherUniversityFacultyAndCareer(before.currentUniversity);
      await dialog.save();

      // Reopen: what was saved must be the selected values. This is the regression that made
      // the faculty look "lost" (the loader never ran again for the same university).
      await dialog.openFromNavbar();
      await expect(dialog.university).toHaveValue(target.university);
      await expect(dialog.faculty).toHaveValue(target.faculty);
      await expect(dialog.career).toHaveValue(target.career);

      // Leave the demo database as it was found.
      await dialog.university.selectOption(before.currentUniversity);
      await dialog.faculty.selectOption(before.currentFaculty);
      await dialog.career.selectOption(before.currentCareer);
      await dialog.save();
      await dialog.openFromNavbar();
      await expect(dialog.university).toHaveValue(before.currentUniversity);
      await expect(dialog.faculty).toHaveValue(before.currentFaculty);
      await expect(dialog.career).toHaveValue(before.currentCareer);
    },
  );
});
