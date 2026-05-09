package com.api.finance.category.controller;

import com.api.finance.category.dto.CategoryResponse;
import com.api.finance.category.dto.CreateCategoryRequest;
import com.api.finance.category.model.CategoryType;
import com.api.finance.category.service.CategoryService;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Gerenciamento de categorias")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    private final CategoryService categoryService;
    private final AuthenticatedUserProvider userProvider;

    @GetMapping
    @Operation(summary = "Lista categorias do usuário + categorias de sistema")
    public ResponseEntity<List<CategoryResponse>> listar(
            @RequestParam(required = false) CategoryType tipo,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        List<CategoryResponse> result = tipo != null
                ? categoryService.listarPorTipo(tipo, caller)
                : categoryService.listar(caller);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Operation(summary = "Cria uma categoria personalizada")
    public ResponseEntity<CategoryResponse> criar(
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.criar(request, caller));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma categoria do usuário")
    public ResponseEntity<CategoryResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(categoryService.atualizar(id, request, caller));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativa uma categoria (soft-delete)")
    public ResponseEntity<Void> deletar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        categoryService.deletar(id, caller);
        return ResponseEntity.noContent().build();
    }
}
