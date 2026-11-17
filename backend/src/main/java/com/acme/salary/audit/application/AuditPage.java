package com.acme.salary.audit.application;

import java.util.List;

public record AuditPage(List<AuditEntryView> items, int page, int size, long totalElements) {}
