import { MAX_TEACHER_BIO_LENGTH, type TeacherProfile, type TeacherProfileInput } from '../../../api';
import { Button } from '../../ui/Button';
import { Field, Input, Textarea } from '../../ui/Field';
import { Card } from '../../ui/primitives';

interface Props {
  profile: TeacherProfile | null;
  form: TeacherProfileInput;
  setForm: React.Dispatch<React.SetStateAction<TeacherProfileInput>>;
  profileError: string;
  savingProfile: boolean;
  onSave: () => void | Promise<void>;
}

/** Editable public profile. Coordinates are preserved but not edited here. */
export default function ProfileSection({ profile, form, setForm, profileError, savingProfile, onSave }: Props) {
  return (
    <Card className="p-6">
      <h2 className="mb-4 font-display text-xl">{profile ? 'Perfil' : 'Creá tu perfil de profesor'}</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Nombre">
          {({ id }) => <Input id={id} value={form.name ?? ''} onChange={(e) => setForm({ ...form, name: e.target.value })} />}
        </Field>
        <Field label="Ciudad">
          {({ id }) => <Input id={id} value={form.city ?? ''} onChange={(e) => setForm({ ...form, city: e.target.value })} />}
        </Field>
        <Field label="Precio por hora (ARS)" hint="Dejalo vacío para 'a convenir'.">
          {({ id }) => (
            <Input
              id={id}
              type="number"
              min="0"
              value={form.pricePerHour ?? ''}
              onChange={(e) => setForm({ ...form, pricePerHour: e.target.value === '' ? undefined : Number(e.target.value) })}
            />
          )}
        </Field>
        <Field label="Foto (URL)">
          {({ id }) => <Input id={id} value={form.photoUrl ?? ''} onChange={(e) => setForm({ ...form, photoUrl: e.target.value })} />}
        </Field>
        <Field label="Dirección (zona)">
          {({ id }) => <Input id={id} value={form.address ?? ''} onChange={(e) => setForm({ ...form, address: e.target.value })} />}
        </Field>
        <Field label="Nota de disponibilidad">
          {({ id }) => <Input id={id} value={form.availabilityNote ?? ''} onChange={(e) => setForm({ ...form, availabilityNote: e.target.value })} />}
        </Field>
      </div>
      <div className="mt-4">
        <Field label="Bio">
          {({ id }) => (
            <Textarea id={id} rows={3} maxLength={MAX_TEACHER_BIO_LENGTH} value={form.bio ?? ''} onChange={(e) => setForm({ ...form, bio: e.target.value })} />
          )}
        </Field>
      </div>
      {profileError && (
        <p
          role="alert"
          className="mt-4 rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500"
        >
          {profileError}
        </p>
      )}
      <div className="mt-4">
        <Button onClick={onSave} loading={savingProfile}>
          {profile ? 'Guardar cambios' : 'Crear perfil'}
        </Button>
      </div>
    </Card>
  );
}
