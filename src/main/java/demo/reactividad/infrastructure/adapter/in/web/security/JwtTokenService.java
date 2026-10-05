package demo.reactividad.infrastructure.adapter.in.web.security;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

// Reemplaza el AUTH_CATEGORY_MAP hardcodeado (ver docs/security/threat-model.md,
// hallazgos de Spoofing/Elevation of Privilege): los tokens ahora son JWT firmados
// (HS256), de corta duración, en vez de strings fijos escritos en el código fuente.
@Component
public class JwtTokenService {

    private static final String CATEGORY_CLAIM = "category";

    private final byte[] secretKeyBytes;
    private final Duration tokenDuration;

    public JwtTokenService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration}") Duration tokenDuration) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.tokenDuration = tokenDuration;
    }

    public String generate(AuthenticationCategory category) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim(CATEGORY_CLAIM, category.name())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(this.tokenDuration)))
                    .build();
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJWT.sign(new MACSigner(this.secretKeyBytes));
            return signedJWT.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to sign JWT", exception);
        }
    }

    public AuthenticationCategory parseCategory(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(verifier())) {
                throw new InvalidAuthTokenException("Token signature is invalid");
            }
            Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                throw new InvalidAuthTokenException("Token has expired");
            }
            String categoryValue = signedJWT.getJWTClaimsSet().getStringClaim(CATEGORY_CLAIM);
            return AuthenticationCategory.valueOf(categoryValue);
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new InvalidAuthTokenException("Token is malformed or tampered", exception);
        }
    }

    private JWSVerifier verifier() throws JOSEException {
        return new MACVerifier(this.secretKeyBytes);
    }
}
