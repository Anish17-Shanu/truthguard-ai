package com.truthguard.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.truthguard.backend.config.AppProperties;
import io.jsonwebtoken.Claims;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private JwtService jwtService;

    @BeforeEach
    void setup() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret(Base64.getEncoder().encodeToString(new byte[32]));
        properties.getJwt().setExpirationSeconds(3600);
        jwtService = new JwtService(properties);
    }

    @Test
    void issuedTokenCarriesSubjectRoleAndTokenId() {
        JwtService.IssuedToken issued = jwtService.generateToken("user-1", "a@b.com", "USER");

        Claims claims = jwtService.parse(issued.token());

        assertEquals("user-1", claims.getSubject());
        assertEquals("USER", claims.get("role", String.class));
        assertEquals("a@b.com", claims.get("email", String.class));
        assertEquals(issued.tokenId(), claims.getId());
    }

    @Test
    void tokenIdsAreUnique() {
        JwtService.IssuedToken first = jwtService.generateToken("user-1", "a@b.com", "USER");
        JwtService.IssuedToken second = jwtService.generateToken("user-1", "a@b.com", "USER");

        assertNotEquals(first.tokenId(), second.tokenId());
        assertNotEquals(first.token(), second.token());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken("user-1", "a@b.com", "USER").token();
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(Exception.class, () -> jwtService.parse(tampered));
    }
}
