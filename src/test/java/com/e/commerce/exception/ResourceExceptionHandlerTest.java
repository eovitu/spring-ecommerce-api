package com.e.commerce.exception;

import com.e.commerce.dto.request.LoginRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResourceExceptionHandlerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new ResourceExceptionHandler()).build();
    @Test void unexpectedErrorDoesNotExposeInternalMessage() throws Exception {
        mvc.perform(get("/probe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Erro interno do servidor"))
                .andExpect(jsonPath("$.error").value("Unexpected error"))
                .andExpect(jsonPath("$.path").value("/probe"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
    @Test void malformedJsonReturnsGeneric400() throws Exception {
        mvc.perform(post("/probe").contentType("application/json").content("{\"password\":\"private-input\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corpo da requisicao invalido"));
    }
    @Test void invalidPasswordKeepsValidationContract() throws Exception {
        mvc.perform(post("/probe").contentType("application/json").content("{\"email\":\"user@example.com\",\"password\":\"12345\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].fieldName").value("password"));
    }
    @Test void invalidQuantityReturns422() throws Exception {
        mvc.perform(get("/quantity"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Quantidade total do produto excede o limite permitido"));
    }
    @Test void unrelatedIllegalArgumentStillReturns500() throws Exception {
        mvc.perform(get("/argument")).andExpect(status().isInternalServerError());
    }
    @RestController static class ProbeController {
        @GetMapping("/quantity") void quantity() { throw new InvalidRequestException("Quantidade total do produto excede o limite permitido"); }
        @GetMapping("/argument") void argument() { throw new IllegalArgumentException("internal"); }
        @GetMapping("/probe") String fail() { throw new IllegalStateException("database-password=private-secret"); }
        @PostMapping("/probe") void accept(@Valid @RequestBody LoginRequest request) {}
    }
}
