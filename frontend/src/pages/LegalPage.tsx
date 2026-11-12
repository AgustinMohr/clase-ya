import type { ReactElement } from 'react';
import { usePageMeta } from '../hooks/usePageMeta';

type LegalKind = 'terms' | 'privacy' | 'help';

interface Props {
  kind: LegalKind;
}

const META: Record<LegalKind, { title: string; description: string; heading: string }> = {
  terms: {
    title: 'Términos y condiciones | ClaseYa',
    description: 'Condiciones de uso de ClaseYa, el directorio de profesores particulares.',
    heading: 'Términos y condiciones',
  },
  privacy: {
    title: 'Política de privacidad | ClaseYa',
    description: 'Cómo tratamos tus datos en ClaseYa y qué información no se publica.',
    heading: 'Política de privacidad',
  },
  help: {
    title: 'Ayuda | ClaseYa',
    description: 'Preguntas frecuentes para estudiantes, familias y profesores de ClaseYa.',
    heading: 'Ayuda',
  },
};

function Terms() {
  return (
    <>
      <p>
        ClaseYa es un directorio que conecta a estudiantes, familias y profesores particulares. La
        plataforma permite buscar, comparar y contactar profesores; no reserva clases, no cobra y no
        agenda horarios.
      </p>
      <h2>Cuentas y responsabilidad</h2>
      <p>
        Para contactar a un profesor necesitás una cuenta activa. Los menores de edad no crean ni
        operan su propia cuenta: lo hace su madre, padre o tutor legal. Al registrarte declarás ser
        mayor de 18 años o contar con la autorización de tu adulto responsable.
      </p>
      <h2>Conducta</h2>
      <p>
        No está permitido acosar, suplantar identidades, publicar datos personales de terceros,
        ofrecer servicios ilegales ni usar la plataforma para enviar spam. Podés reportar y bloquear
        a otro usuario desde una conversación.
      </p>
      <h2>Coordinación de clases</h2>
      <p>
        La fecha, la modalidad, el precio y el pago de cada clase los acuerdan el estudiante (o su
        adulto responsable) y el profesor fuera de ClaseYa. ClaseYa no participa en esa relación ni
        garantiza el resultado de una clase.
      </p>
      <h2>Contenido de los anuncios</h2>
      <p>
        Cada profesor es responsable de la información que publica. ClaseYa puede moderar, ocultar o
        dar de baja anuncios y cuentas que incumplan estas condiciones.
      </p>
    </>
  );
}

function Privacy() {
  return (
    <>
      <p>
        Tratamos los datos mínimos necesarios para que puedas buscar profesores y contactarlos de
        forma segura. La identidad de quien actúa en la plataforma siempre se resuelve desde la
        sesión, nunca desde un identificador enviado por el navegador.
      </p>
      <h2>Qué datos usamos</h2>
      <ul>
        <li>Cuenta: nombre, email, rol y estado de activación.</li>
        <li>Perfil de estudiante: nombre visible, nivel y ciudad opcional.</li>
        <li>Perfil de profesor: anuncio, materias, modalidades, zona y disponibilidad declarada.</li>
        <li>Mensajería interna y reportes de seguridad.</li>
      </ul>
      <h2>Qué no se publica</h2>
      <p>
        Nunca mostramos emails, teléfonos, direcciones exactas, coordenadas precisas, contraseñas,
        tokens ni hashes. Los datos de contacto reales no se comparten entre las partes: la
        comunicación ocurre dentro de ClaseYa.
      </p>
      <h2>Menores</h2>
      <p>
        La cuenta de un menor la crea y opera su adulto responsable. No recopilamos datos de menores
        con fines publicitarios ni creamos perfiles públicos de menores.
      </p>
      <h2>Reportes y bloqueo</h2>
      <p>
        Cuando reportás a otro usuario, el reporte queda disponible para administración. La persona
        reportada no ve quién la reportó. Un bloqueo impide nuevos contactos y mensajes en ambas
        direcciones, pero conserva el historial existente.
      </p>
      <h2>Tus derechos</h2>
      <p>
        Podés solicitar acceso, corrección o baja de tus datos escribiendo al canal de ayuda de
        ClaseYa. Responderemos dentro de un plazo razonable.
      </p>
    </>
  );
}

function Help() {
  return (
    <>
      <h2>¿Cómo contacto a un profesor?</h2>
      <p>
        Creá una cuenta, completá tu nombre visible y entrá al perfil del profesor. Usá “Contactar”
        para abrir una conversación dentro de ClaseYa. La fecha y el pago los acordás directamente
        con el profesor.
      </p>
      <h2>¿ClaseYa cobra o reserva?</h2>
      <p>
        No. ClaseYa es un directorio y un canal de contacto. No procesa pagos ni administra agenda.
      </p>
      <h2>¿Qué hago si alguien me molesta?</h2>
      <p>
        Desde la conversación podés reportar y bloquear al usuario. El bloqueo corta nuevos
        mensajes. Administración revisa los reportes y puede suspender cuentas.
      </p>
      <h2>¿Cómo publico mi anuncio?</h2>
      <p>
        Registrate como profesor, completá tu perfil, agregá al menos una materia, una modalidad y
        tu disponibilidad declarada. Cuando el anuncio esté publicado, aparecerás en las búsquedas.
      </p>
      <h2>¿Necesito verificación?</h2>
      <p>
        La verificación es opcional y suma confianza: no es un requisito para publicar. Los perfiles
        verificados muestran una insignia.
      </p>
      <h2>Olvidé mi contraseña</h2>
      <p>
        Usá “Recuperar contraseña” en el ingreso. Te enviamos un enlace de un solo uso que vence en
        30 minutos.
      </p>
    </>
  );
}

const CONTENT: Record<LegalKind, () => ReactElement> = {
  terms: Terms,
  privacy: Privacy,
  help: Help,
};

export default function LegalPage({ kind }: Props) {
  const meta = META[kind];
  usePageMeta({ title: meta.title, description: meta.description });
  const Content = CONTENT[kind];

  return (
    <div className="container-page max-w-3xl py-10">
      <article className="space-y-4 rounded-2xl border border-border bg-surface p-6 sm:p-8">
        <h1 className="font-display text-3xl text-primary-700 dark:text-primary-200">
          {meta.heading}
        </h1>
        <p className="text-xs text-content-muted">Última actualización: noviembre de 2026.</p>
        <div className="space-y-3 text-sm leading-relaxed text-content-muted [&_h2]:pt-2 [&_h2]:text-base [&_h2]:font-bold [&_h2]:text-content [&_li]:ml-5 [&_li]:list-disc">
          <Content />
        </div>
      </article>
    </div>
  );
}
