# Mi anuncio (TEACHER-001, slice A + B)

Área del profesor para mantener su anuncio (perfil, materias, modalidades, formación, disponibilidad)
y presentar credenciales a verificación.

## Page object

`teacher-listing-page.ts` → `TeacherListingPage` (extiende `BasePage`):

- `open()` — entra a "Mi anuncio" desde el navbar (rol TEACHER).
- `credentialRow(degree)` — la fila del panel de verificación de esa credencial.

## Flujos cubiertos

- **A**: completitud (`Publicado`), edición de bio (`Guardar cambios`).
- **B**: alta de formación → subir documento (PDF) → presentar credencial (`En revisión`).

## Selectores

- Navbar: `getByRole('button', { name: 'Mi anuncio' })`.
- Fila de credencial: `getByTestId('credential-row')` filtrado por el texto del título.
- Subir: `getByLabel('Archivo (PDF, JPG o PNG)')` + `getByRole('button', { name: 'Subir' })`.
- Presentar: `getByRole('button', { name: 'Presentar a revisión' })`.
