package ec.uce.propuestas.usuario.auth;

import java.util.regex.Pattern;

public final class PasswordPolicy {

    private static final Pattern LETTER = Pattern.compile(".*[A-Za-zÁ-ú].*");
    private static final Pattern DIGIT  = Pattern.compile(".*\\d.*");

    private PasswordPolicy() {}

    public static boolean isValid(String password) {
        return password != null
            && password.length() >= 8
            && LETTER.matcher(password).matches()
            && DIGIT.matcher(password).matches();
    }
}
