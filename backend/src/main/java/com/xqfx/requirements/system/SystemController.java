package com.xqfx.requirements.system;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/systems")
class SystemController {

    private final SystemService service;

    SystemController(SystemService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SystemResponse create(@Valid @RequestBody CreateSystemRequest request) {
        return service.create(request.name(), request.ownerName(), request.collaborators());
    }

    @GetMapping
    List<SystemResponse> list() {
        return service.list();
    }

    record CreateSystemRequest(
            @NotBlank String name,
            @NotBlank String ownerName,
            List<String> collaborators
    ) {
        CreateSystemRequest {
            collaborators = collaborators == null ? List.of() : List.copyOf(collaborators);
        }
    }
}
