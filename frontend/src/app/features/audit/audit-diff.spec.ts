import { AuditEntry } from './audit.service';
import { describeChanges, fieldLabel } from './audit-diff';

function entry(partial: Partial<AuditEntry>): AuditEntry {
  return {
    id: 'a1',
    userId: 'u1',
    userEmail: 'priya@acme.example',
    entityType: 'EMPLOYEE',
    entityId: 'e1',
    subjectEmployeeId: 'e1',
    operation: 'UPDATE',
    before: null,
    after: null,
    createdAt: '2026-03-10T10:00:00Z',
    ...partial,
  };
}

describe('describeChanges', () => {
  it('lists each changed field with its previous and new value', () => {
    const changes = describeChanges(
      entry({
        before: { jobTitle: 'Engineer', status: 'ACTIVE' },
        after: { jobTitle: 'Senior Engineer', status: 'ON_LEAVE' },
      }),
    );

    expect(changes).toEqual([
      { field: 'Job title', before: 'Engineer', after: 'Senior Engineer' },
      { field: 'Status', before: 'Active', after: 'On leave' },
    ]);
  });

  it('shows a created record as new values only', () => {
    const changes = describeChanges(
      entry({ operation: 'CREATE', before: null, after: { city: 'Sydney' } }),
    );

    expect(changes).toEqual([{ field: 'City', before: null, after: 'Sydney' }]);
  });

  it('writes money as an amount and a currency', () => {
    const changes = describeChanges(
      entry({
        entityType: 'COMPENSATION_RECORD',
        operation: 'REVISE',
        before: { amount: { amount: '100000.00', currency: 'AUD' } },
        after: { amount: { amount: '120000.00', currency: 'AUD' } },
      }),
    );

    expect(changes).toEqual([
      { field: 'Amount', before: '100,000.00 AUD', after: '120,000.00 AUD' },
    ]);
  });

  it('shows a missing value as null so the screen can draw a dash', () => {
    const changes = describeChanges(
      entry({
        entityType: 'COMPENSATION_RECORD',
        operation: 'REVISE',
        before: { effectiveEnd: null },
        after: { effectiveEnd: '2026-05-31' },
      }),
    );

    expect(changes[0]).toEqual({ field: 'Effective end', before: null, after: '2026-05-31' });
  });

  it('leaves out fields that did not change', () => {
    const changes = describeChanges(
      entry({
        before: { city: 'Sydney', status: 'ACTIVE' },
        after: { city: 'Sydney', status: 'ON_LEAVE' },
      }),
    );

    expect(changes.map((c) => c.field)).toEqual(['Status']);
  });

  it('does not rewrite free text that happens to be upper case', () => {
    const changes = describeChanges(entry({ before: null, after: { department: 'DEVOPS' } }));

    expect(changes[0].after).toBe('DEVOPS');
  });

  it('names the currency in an exchange rate change, which is otherwise not shown as changed', () => {
    const changes = describeChanges(
      entry({
        entityType: 'EXCHANGE_RATE',
        subjectEmployeeId: null,
        before: { currency: 'INR', rate: '0.012' },
        after: { currency: 'INR', rate: '0.0125' },
      }),
    );

    expect(changes).toEqual([{ field: 'INR rate', before: '0.012', after: '0.0125' }]);
  });

  it('has nothing to show when there is no payload', () => {
    expect(describeChanges(entry({}))).toEqual([]);
  });

  it("writes money the way the caller asks, so the viewer's format applies", () => {
    const changes = describeChanges(
      entry({
        entityType: 'COMPENSATION_RECORD',
        operation: 'REVISE',
        before: { amount: { amount: '100000.00', currency: 'AUD' } },
        after: { amount: { amount: '120000.00', currency: 'AUD' } },
      }),
      (money) => `${money.currency} ${money.amount}`,
    );

    expect(changes).toEqual([{ field: 'Amount', before: 'AUD 100000.00', after: 'AUD 120000.00' }]);
  });
});

describe('fieldLabel', () => {
  it('writes a camelCase field as plain words', () => {
    expect(fieldLabel('workEmail')).toBe('Work email');
    expect(fieldLabel('salaryEffectiveStart')).toBe('Salary effective start');
  });
});
