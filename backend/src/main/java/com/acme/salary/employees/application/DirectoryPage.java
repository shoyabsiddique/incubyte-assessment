package com.acme.salary.employees.application;

import java.util.List;

public record DirectoryPage(List<EmployeeSummary> items, int page, int size, long totalElements) {

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }
}
