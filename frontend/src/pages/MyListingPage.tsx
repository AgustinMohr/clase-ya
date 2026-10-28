import { ArrowLeft, Eye, Megaphone } from 'lucide-react';
import { Button } from '../components/ui/Button';
import { Skeleton } from '../components/ui/primitives';
import VerificationPanel from '../components/teacher/VerificationPanel';
import AnnouncementStatusCard from '../components/teacher/listing/AnnouncementStatusCard';
import AvailabilitySection from '../components/teacher/listing/AvailabilitySection';
import EducationSection from '../components/teacher/listing/EducationSection';
import ModalitiesSection from '../components/teacher/listing/ModalitiesSection';
import ProfileSection from '../components/teacher/listing/ProfileSection';
import SubjectsSection from '../components/teacher/listing/SubjectsSection';
import { useAcademicCascade } from '../hooks/useAcademicCascade';
import { usePageMeta } from '../hooks/usePageMeta';
import { useTeacherListing } from '../hooks/useTeacherListing';

/**
 * "Mi anuncio" orchestrator. Loading and mutations live in useTeacherListing; the academic cascade
 * in useAcademicCascade; each section is a presentational component. Behavior is unchanged.
 */
export default function MyListingPage({
  onBack,
  onPreview,
}: {
  onBack: () => void;
  onPreview: (id: string) => void;
}) {
  usePageMeta({
    title: 'Mi anuncio | ClaseYa',
    description: 'Editá tu perfil público de profesor, materias, disponibilidad y verificación.',
  });

  const listing = useTeacherListing();
  const cascade = useAcademicCascade();

  if (listing.loading) {
    return (
      <div className="container-page space-y-5 py-8">
        <Skeleton className="h-24" />
        <Skeleton className="h-64" />
      </div>
    );
  }

  return (
    <div className="container-page py-8">
      <button
        type="button"
        onClick={onBack}
        className="mb-5 inline-flex items-center gap-1.5 text-sm font-semibold text-content-muted transition-colors hover:text-primary-600 dark:hover:text-primary-200"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver
      </button>

      <header className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 font-display text-3xl">
            <Megaphone className="h-7 w-7 text-primary-600 dark:text-primary-200" aria-hidden="true" /> Mi anuncio
          </h1>
          <p className="mt-1 text-content-muted">
            Mantené tu perfil, materias, modalidades, formación y disponibilidad. La verificación no
            bloquea tu anuncio: lo que falta es solo informativo.
          </p>
        </div>
        {listing.profile && (
          <Button variant="outline" onClick={() => onPreview(listing.profile!.id)}>
            <Eye className="h-4 w-4" aria-hidden="true" /> Previsualizar
          </Button>
        )}
      </header>

      <div className="space-y-8">
        <AnnouncementStatusCard announcement={listing.announcement} />

        <div>
          <VerificationPanel verification={listing.verification} onChanged={listing.reloadVerification} />
        </div>

        <ProfileSection
          profile={listing.profile}
          form={listing.form}
          setForm={listing.setForm}
          profileError={listing.profileError}
          savingProfile={listing.savingProfile}
          onSave={listing.saveProfile}
        />

        <SubjectsSection
          subjects={listing.subjects}
          universities={listing.universities}
          units={cascade.units}
          careers={cascade.careers}
          careerSubjects={cascade.careerSubjects}
          selUniversity={cascade.selUniversity}
          selUnit={cascade.selUnit}
          selCareer={cascade.selCareer}
          selCareerSubject={cascade.selCareerSubject}
          setSelCareerSubject={cascade.setSelCareerSubject}
          onPickUnit={cascade.pickUnit}
          onPickCareer={cascade.pickCareer}
          onPickCareerSubject={cascade.pickCareerSubject}
          addingSubject={listing.addingSubject}
          onAddSubject={listing.addSubject}
          onRemoveSubject={listing.removeSubject}
        />

        <ModalitiesSection modalities={listing.modalities} onToggle={listing.toggleModality} />

        <EducationSection
          education={listing.education}
          form={listing.eduForm}
          setForm={listing.setEduForm}
          addingEducation={listing.addingEducation}
          onAdd={listing.addEducation}
          onRemove={listing.removeEducation}
        />

        <AvailabilitySection
          availability={listing.availability}
          form={listing.availForm}
          setForm={listing.setAvailForm}
          addingAvailability={listing.addingAvailability}
          onAdd={listing.addAvailability}
          onDisable={listing.disableAvailability}
        />
      </div>
    </div>
  );
}
