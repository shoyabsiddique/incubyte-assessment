package com.acme.archfixture.good.employees.application;

import com.acme.archfixture.good.compensation.api.CompApi;
import com.acme.archfixture.good.audit.api.AuditApi;
import com.acme.archfixture.good.shared.SharedThing;
import com.acme.archfixture.good.employees.domain.Pure;

public class Svc {
    CompApi comp;
    AuditApi audit;
    SharedThing shared;
    Pure pure;
}
