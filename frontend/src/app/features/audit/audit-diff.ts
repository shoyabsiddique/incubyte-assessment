import { AuditEntry } from './audit.service';

export interface FieldChange {
  field: string;
  before: string | null;
  after: string | null;
}

/** Writes a camelCase field name as plain words: workEmail becomes "Work email". */
export function fieldLabel(field: string): string {
  const words = field
    .replace(/([A-Z])/g, ' $1')
    .toLowerCase()
    .trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

const ENUM_VALUES = new Set([
  'ACTIVE',
  'ON_LEAVE',
  'TERMINATED',
  'FULL_TIME',
  'PART_TIME',
  'CONTRACT',
  'INDIA',
  'GERMANY',
  'SINGAPORE',
  'CANADA',
  'AUSTRALIA',
]);

function isMoney(value: unknown): value is { amount: string; currency: string } {
  return typeof value === 'object' && value !== null && 'amount' in value && 'currency' in value;
}

function format(value: unknown): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (isMoney(value)) {
    const amount = Number(value.amount).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    return `${amount} ${value.currency}`;
  }
  const text = String(value);
  // Enum values (ON_LEAVE) read as words; short codes such as UK or USA stay as they are.
  if (ENUM_VALUES.has(text)) {
    const words = text.replace(/_/g, ' ').toLowerCase();
    return words.charAt(0).toUpperCase() + words.slice(1);
  }
  return text;
}

/** The fields an audit entry changed, each with what it was and what it became. */
export function describeChanges(entry: AuditEntry): FieldChange[] {
  const after = entry.after ?? entry.before ?? {};
  return Object.keys(after)
    .filter(
      (field) =>
        !entry.before ||
        JSON.stringify(entry.before[field]) !== JSON.stringify(entry.after?.[field]),
    )
    .map((field) => ({
      field: fieldLabel(field),
      before: format(entry.before?.[field]),
      after: format(entry.after?.[field]),
    }));
}
