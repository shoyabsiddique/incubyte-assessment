package com.acme.salary.audit.api;

/**
 * Records what changed, in the transaction of the change itself. It never opens a transaction of
 * its own: called outside one it fails, so a change can not be saved without its audit entry.
 */
public interface AuditTrail {

    void record(AuditEvent event);
}
