package com.e.commerce.controller;

import com.e.commerce.dto.request.OrderRequest;
import com.e.commerce.dto.response.OrderResponse;
import com.e.commerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.e.commerce.enums.Role;
import com.e.commerce.security.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;



    @GetMapping
    public ResponseEntity<List<OrderResponse>> findAll(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user.role() == Role.ADMIN) {
            return ResponseEntity.ok(orderService.findAll());
        }
        return ResponseEntity.ok(orderService.findByUserId(user.id()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @authorizationService.isOrderOwner(#p0)")
    public ResponseEntity<OrderResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody OrderRequest request
    ) {
        OrderResponse response = orderService.create(request, user.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
