package com.dmu.dshorts.domain.admin.controller;

import com.dmu.dshorts.domain.admin.dto.AdminPersonaDtos.AdminPersonaDetail;
import com.dmu.dshorts.domain.admin.dto.AdminPersonaDtos.AdminPersonaSummary;
import com.dmu.dshorts.domain.admin.dto.AdminPersonaDtos.PersonaRequest;
import com.dmu.dshorts.domain.admin.service.AdminPersonaService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/personas")
public class AdminPersonaController {

    private final AdminPersonaService personaService;

    public AdminPersonaController(AdminPersonaService personaService) {
        this.personaService = personaService;
    }

    /** All personas (inactive/deleted included), id order, single page. */
    @GetMapping
    public ApiResponse<PageResponse<AdminPersonaSummary>> list() {
        return ApiResponse.of(PageResponse.of(personaService.list(), null));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminPersonaDetail> get(@PathVariable Long id) {
        return ApiResponse.of(personaService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminPersonaDetail> create(@RequestBody PersonaRequest request) {
        return ApiResponse.of(personaService.create(request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AdminPersonaDetail> update(@PathVariable Long id, @RequestBody PersonaRequest request) {
        return ApiResponse.of(personaService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        personaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
