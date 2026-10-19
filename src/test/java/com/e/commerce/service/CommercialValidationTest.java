package com.e.commerce.service;
import com.e.commerce.dto.request.*;
import com.e.commerce.entity.Product;
import jakarta.validation.Validation;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class CommercialValidationTest {
 @Test void nullOrderItemIsRejected() {
  try (var factory = Validation.buildDefaultValidatorFactory()) {
   assertFalse(factory.getValidator().validate(new OrderRequest(Arrays.asList((OrderItemRequest)null))).isEmpty());
  }
 }
 @Test void categoryElementsAndPriceRespectStorageContract() {
  try (var factory = Validation.buildDefaultValidatorFactory()) {
   var validator = factory.getValidator();
   for (String category : Arrays.asList(null, " ")) {
    assertFalse(validator.validate(new ProductRequest("Product", "Description", BigDecimal.TEN, "image", new String[]{category})).isEmpty());
   }
   for (String price : List.of("1.001", "1000000000000000000000000000000000000")) {
    assertFalse(validator.validate(new ProductRequest("Product", "Description", new BigDecimal(price), "image", new String[]{"Category"})).isEmpty());
   }
  }
 }
 @Test void productMappingPreserves500CharacterContract() throws Exception {
  assertNotNull(Product.class.getDeclaredField("description").getAnnotation(Column.class));
  assertEquals(500, Product.class.getDeclaredField("description").getAnnotation(Column.class).length());
  assertEquals(500, Product.class.getDeclaredField("imageUrl").getAnnotation(Column.class).length());
 }
}
