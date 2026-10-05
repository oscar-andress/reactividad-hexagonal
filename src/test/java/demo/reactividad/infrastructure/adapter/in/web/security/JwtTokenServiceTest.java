package demo.reactividad.infrastructure.adapter.in.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long!!";

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, Duration.ofMinutes(15));

    @Test
    void generateThenParseCategory_RoundTripsToTheSameCategory() {
        String token = this.jwtTokenService.generate(AuthenticationCategory.PRIME);

        AuthenticationCategory category = this.jwtTokenService.parseCategory(token);

        assertEquals(AuthenticationCategory.PRIME, category);
    }

    @Test
    void parseCategory_WhenTokenIsTampered_ThrowsInvalidAuthTokenException() {
        String token = this.jwtTokenService.generate(AuthenticationCategory.STANDARD);
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThrows(InvalidAuthTokenException.class, () -> this.jwtTokenService.parseCategory(tampered));
    }

    @Test
    void parseCategory_WhenSignedWithADifferentSecret_ThrowsInvalidAuthTokenException() {
        JwtTokenService otherService = new JwtTokenService(
                "a-completely-different-secret-also-32-bytes!!", Duration.ofMinutes(15));
        String token = otherService.generate(AuthenticationCategory.PRIME);

        assertThrows(InvalidAuthTokenException.class, () -> this.jwtTokenService.parseCategory(token));
    }

    @Test
    void parseCategory_WhenTokenIsExpired_ThrowsInvalidAuthTokenException() throws InterruptedException {
        JwtTokenService shortLivedService = new JwtTokenService(SECRET, Duration.ofMillis(1));
        String token = shortLivedService.generate(AuthenticationCategory.STANDARD);
        Thread.sleep(20);

        assertThrows(InvalidAuthTokenException.class, () -> shortLivedService.parseCategory(token));
    }

    @Test
    void parseCategory_WhenTokenIsNotAJwtAtAll_ThrowsInvalidAuthTokenException() {
        assertThrows(InvalidAuthTokenException.class, () -> this.jwtTokenService.parseCategory("secret123"));
    }
}
