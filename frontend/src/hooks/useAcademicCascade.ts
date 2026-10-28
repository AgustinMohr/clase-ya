import { useState } from 'react';
import {
  api,
  type AcademicUnit,
  type Career,
  type CareerSubject,
} from '../api';

/**
 * University → academic unit → career → career-subject picker for the subject form. Each selection
 * clears every dependent level, exactly like the original page did.
 */
export function useAcademicCascade() {
  const [units, setUnits] = useState<AcademicUnit[]>([]);
  const [careers, setCareers] = useState<Career[]>([]);
  const [careerSubjects, setCareerSubjects] = useState<CareerSubject[]>([]);
  const [selUniversity, setSelUniversity] = useState('');
  const [selUnit, setSelUnit] = useState('');
  const [selCareer, setSelCareer] = useState('');
  const [selCareerSubject, setSelCareerSubject] = useState('');

  function pickUnit(universityId: string) {
    setSelUniversity(universityId);
    setSelUnit('');
    setSelCareer('');
    setSelCareerSubject('');
    setCareers([]);
    setCareerSubjects([]);
    api.academicUnits(universityId).then(setUnits).catch(() => setUnits([]));
  }

  function pickCareer(unitId: string) {
    setSelUnit(unitId);
    setSelCareer('');
    setSelCareerSubject('');
    setCareerSubjects([]);
    api.careers(unitId).then(setCareers).catch(() => setCareers([]));
  }

  function pickCareerSubject(careerId: string) {
    setSelCareer(careerId);
    setSelCareerSubject('');
    api.careerSubjects(careerId).then(setCareerSubjects).catch(() => setCareerSubjects([]));
  }

  return {
    units,
    careers,
    careerSubjects,
    selUniversity,
    selUnit,
    selCareer,
    selCareerSubject,
    setSelCareerSubject,
    pickUnit,
    pickCareer,
    pickCareerSubject,
  };
}
