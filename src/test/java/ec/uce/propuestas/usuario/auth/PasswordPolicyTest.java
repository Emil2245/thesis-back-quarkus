package ec.uce.propuestas.usuario.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void validPassword() {
        assertTrue(PasswordPolicy.isValid("Pass1234"));
    }

    @Test
    void emptyPassword() {
        assertFalse(PasswordPolicy.isValid(""));
    }

    @Test
    void sevenCharsTooShort() {
        assertFalse(PasswordPolicy.isValid("Pass123"));
    }

    @Test
    void noDigit() {
        assertFalse(PasswordPolicy.isValid("Password"));
    }

    @Test
    void noLetter() {
        assertFalse(PasswordPolicy.isValid("12345678"));
    }

    @Test
    void nullPassword() {
        assertFalse(PasswordPolicy.isValid(null));
    }
}
