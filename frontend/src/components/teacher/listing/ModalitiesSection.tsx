import { Check } from 'lucide-react';
import { modalityLabel } from '../../../lib/format';
import { Button } from '../../ui/Button';
import { Card } from '../../ui/primitives';

interface Props {
  modalities: string[];
  onToggle: (modality: string) => void | Promise<void>;
}

/** Teaching modalities (online / in person). Toggling is immediate and reversible. */
export default function ModalitiesSection({ modalities, onToggle }: Props) {
  return (
    <Card className="p-6">
      <h2 className="mb-4 font-display text-xl">Modalidades</h2>
      <div className="flex flex-wrap gap-2">
        {(['ONLINE', 'IN_PERSON'] as const).map((modality) => {
          const active = modalities.includes(modality);
          return (
            <Button key={modality} variant={active ? 'primary' : 'outline'} size="sm" onClick={() => onToggle(modality)} aria-pressed={active}>
              {active && <Check className="h-4 w-4" aria-hidden="true" />}
              {modalityLabel(modality)}
            </Button>
          );
        })}
      </div>
    </Card>
  );
}
