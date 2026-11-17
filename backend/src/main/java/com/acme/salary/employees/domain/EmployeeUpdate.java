package com.acme.salary.employees.domain;

import com.acme.salary.shared.domain.FieldError;
import com.acme.salary.shared.domain.ValidationFailedException;
import java.util.ArrayList;
import java.util.List;

/** A valid edit: the new profile and the version the client based it on. */
public record EmployeeUpdate(EmployeeDraft profile, long version) {

    public static EmployeeUpdate parse(ProfileInput input, Long version) {
        List<FieldError> errors = new ArrayList<>();
        EmployeeDraft draft = EmployeeDraft.parse(input, errors);
        if (version == null || version < 0) {
            errors.add(new FieldError("version", "is required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new EmployeeUpdate(draft, version);
    }
}
