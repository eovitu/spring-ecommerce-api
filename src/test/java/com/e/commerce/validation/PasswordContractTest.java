package com.e.commerce.validation;

import com.e.commerce.dto.request.LoginRequest;
import com.e.commerce.dto.request.UserRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class PasswordContractTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test void bcryptRejectsMoreThan72Utf8Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new BCryptPasswordEncoder().encode("A1!" + "é".repeat(35)));
    }
    @Test void registrationRejects73AsciiBytes() { assertFalse(validator.validate(user("A1!" + "a".repeat(70))).isEmpty()); }
    @Test void registrationRejects73Utf8Bytes() { assertFalse(validator.validate(user("A1!" + "é".repeat(35))).isEmpty()); }
    @Test void loginRejects73Utf8Bytes() { assertFalse(validator.validate(new LoginRequest("user@example.com", "A1!" + "é".repeat(35))).isEmpty()); }
    @Test void bothAccept72ByteBoundaryAndBcryptRoundTrips() {
        for (String password : new String[]{"A1!" + "a".repeat(69), "A1!a" + "é".repeat(34), "A1!a" + "😀".repeat(17)}) {
            assertTrue(validator.validate(user(password)).isEmpty());
            assertTrue(validator.validate(new LoginRequest("user@example.com", password)).isEmpty());
            var encoder = new BCryptPasswordEncoder();
            assertTrue(encoder.matches(password, encoder.encode(password)));
        }
    }
    @Test void minimumsAndRequiredPasswordRemain() {
        assertFalse(validator.validate(user("A1!aaaa")).isEmpty());
        assertFalse(validator.validate(new LoginRequest("user@example.com", "12345")).isEmpty());
        assertTrue(validator.validate(new LoginRequest("user@example.com", "123456")).isEmpty());
        assertFalse(validator.validate(user(null)).isEmpty());
        assertFalse(validator.validate(new LoginRequest("user@example.com", " ")).isEmpty());
    }
    private UserRequest user(String password) { return new UserRequest("Maria Silva", "user@example.com", password, null); }
}
