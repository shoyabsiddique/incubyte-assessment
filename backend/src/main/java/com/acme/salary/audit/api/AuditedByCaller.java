package com.acme.salary.audit.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service that writes on behalf of a larger operation which records the single audit entry
 * for the whole operation, so the architecture rule does not ask it to audit again.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditedByCaller {}
