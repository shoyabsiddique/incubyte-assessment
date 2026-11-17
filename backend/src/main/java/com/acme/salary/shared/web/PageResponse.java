package com.acme.salary.shared.web;

import java.util.List;

/** The page envelope every list endpoint returns. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
