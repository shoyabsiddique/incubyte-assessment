package com.acme.salary.employees.web;

import com.acme.salary.employees.application.EmployeeCommandService;
import com.acme.salary.employees.application.EmployeeDirectoryService;
import com.acme.salary.shared.web.ActorResolver;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
class EmployeeCommandController {

    private final EmployeeCommandService commands;
    private final EmployeeDirectoryService directory;

    EmployeeCommandController(EmployeeCommandService commands, EmployeeDirectoryService directory) {
        this.commands = commands;
        this.directory = directory;
    }

    @PostMapping
    ResponseEntity<EmployeeResponse> create(@AuthenticationPrincipal Jwt token, @RequestBody CreateEmployeeRequest request) {
        UUID id = commands.create(ActorResolver.from(token), request.parse());
        EmployeeResponse created = directory.find(id).map(EmployeeResponse::from).orElseThrow();
        return ResponseEntity.created(URI.create("/api/v1/employees/" + id)).body(created);
    }
}
