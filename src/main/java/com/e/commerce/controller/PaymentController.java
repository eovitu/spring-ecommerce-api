package com.e.commerce.controller;

import com.e.commerce.dto.request.PaymentRequest;
import com.e.commerce.dto.response.PaymentResponse;
import com.e.commerce.service.PaymentService;
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
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> findAll(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user.role() == Role.ADMIN) {
            return ResponseEntity.ok(paymentService.findAll());
        }
        return ResponseEntity.ok(paymentService.findByUserId(user.id()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @authorizationService.isOrderOwner(#p0)")
    public ResponseEntity<PaymentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.findById(id));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or @authorizationService.isOrderOwner(#p0)")
    public ResponseEntity<PaymentResponse> findByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.findByOrderId(orderId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @authorizationService.isOrderOwner(#p0.orderId)")
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
