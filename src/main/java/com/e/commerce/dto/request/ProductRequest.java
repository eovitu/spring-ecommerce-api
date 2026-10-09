package com.e.commerce.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Nome e obrigatorio")
    @Size(min = 2, max = 120, message = "Nome deve ter entre 2 e 120 caracteres")
    private String name;

    @NotBlank(message = "Descricao e obrigatoria")
    @Size(min = 5, max = 500, message = "Descricao deve ter entre 5 e 500 caracteres")
    private String description;

    @NotNull(message = "Preco e obrigatorio")
    @DecimalMin(value = "0.01", message = "Preco deve ser maior que zero")
    @Digits(integer = 36, fraction = 2, message = "Preco deve possuir no maximo 36 inteiros e 2 decimais")
    private BigDecimal price;

    @NotBlank(message = "Imagem e obrigatoria")
    @Size(max = 500, message = "URL da imagem deve ter no maximo 500 caracteres")
    private String imageUrl;

    @NotEmpty(message = "Informe ao menos uma categoria")
    private String[] categories;

    @jakarta.validation.constraints.AssertTrue(message = "Categorias devem ser preenchidas")
    public boolean isCategoryElementsValid() {
        return categories == null || java.util.Arrays.stream(categories)
                .allMatch(category -> category != null && !category.isBlank());
    }
}
