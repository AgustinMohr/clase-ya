import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { cn } from '../../lib/cn';

const buttonStyles = cva(
  'inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition-[transform,background-color,color,box-shadow] duration-200 ease-smooth focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50 active:translate-y-[1px]',
  {
    variants: {
      variant: {
        primary: 'bg-primary-600 text-white shadow-card hover:bg-primary-700',
        secondary: 'bg-secondary-600 text-white hover:bg-secondary-700',
        accent: 'bg-accent-400 text-primary-900 hover:bg-accent-500',
        outline: 'border border-border bg-surface text-content hover:bg-surface-muted',
        ghost: 'text-content hover:bg-surface-muted',
        danger: 'bg-error-600 text-white hover:bg-error-700',
        link: 'text-primary-600 underline-offset-4 hover:underline px-0',
      },
      size: {
        sm: 'h-9 px-3 text-sm',
        md: 'h-11 px-5 text-[15px]',
        lg: 'h-12 px-6 text-base',
        icon: 'h-10 w-10',
      },
      block: { true: 'w-full', false: '' },
    },
    defaultVariants: { variant: 'primary', size: 'md', block: false },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonStyles> {
  loading?: boolean;
}

export function Button({ className, variant, size, block, loading, children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      className={cn(buttonStyles({ variant, size, block }), className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  );
}
