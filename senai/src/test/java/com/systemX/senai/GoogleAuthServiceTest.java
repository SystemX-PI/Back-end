package com.systemX.senai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.systemX.senai.config.JwtService;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.repository.RefreshTokenRepository;
import com.systemX.senai.repository.UsuarioRepository;
import com.systemX.senai.service.AuthService;
import com.systemX.senai.service.GoogleIdentityService;
import com.systemX.senai.service.PlatformService;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {
    @Mock UsuarioRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordEncoder passwordEncoder;
    @Mock GoogleIdentityService googleIdentity;
    @Mock PlatformService platform;

    private AuthService auth;
    private GoogleIdentityService.Identity identity;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, refreshTokens, passwordEncoder,
                new JwtService("test-secret-with-at-least-32-characters", 900), 604800,
                googleIdentity, platform);
        identity = new GoogleIdentityService.Identity(
                "google-sub-123", "person@example.com", "Person Example", "https://photo.example/avatar.jpg");
        when(googleIdentity.verify("valid-google-token")).thenReturn(identity);
    }

    @Test
    void loginUsesGoogleSubjectAndIssuesTheExistingApplicationSession() {
        Usuario existing = user("person@example.com");
        when(users.findByGoogleSubject(identity.subject())).thenReturn(Optional.of(existing));

        var response = auth.loginWithGoogle("valid-google-token");

        assertNotNull(response.token());
        assertNotNull(response.refreshToken());
        assertEquals("CLIENTE", response.tipo());
        verify(platform, never()).signupWithGoogle(any());
        verify(refreshTokens).save(any());
    }

    @Test
    void loginRejectsAGoogleIdentityThatHasNotBeenRegistered() {
        when(users.findByGoogleSubject(identity.subject())).thenReturn(Optional.empty());
        when(users.lockByEmail(identity.email())).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.loginWithGoogle("valid-google-token"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(platform, never()).signupWithGoogle(any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void loginDoesNotSilentlyLinkAnExistingLocalAccountByEmail() {
        when(users.findByGoogleSubject(identity.subject())).thenReturn(Optional.empty());
        when(users.lockByEmail(identity.email())).thenReturn(Optional.of(user(identity.email())));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.loginWithGoogle("valid-google-token"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(platform, never()).signupWithGoogle(any());
    }

    @Test
    void registrationCreatesTheGoogleAccountWithTheCompanyNameAndStartsASession() {
        Usuario created = user(identity.email());
        when(users.findByGoogleSubject(identity.subject())).thenReturn(Optional.empty());
        when(users.lockByEmail(identity.email())).thenReturn(Optional.empty());
        when(platform.signupWithGoogle(identity, "Empresa Aurora")).thenReturn(created);

        var response = auth.registerWithGoogle("valid-google-token", "Empresa Aurora");

        assertEquals("CLIENTE", response.tipo());
        verify(platform).signupWithGoogle(identity, "Empresa Aurora");
        verify(refreshTokens).save(any());
    }

    @Test
    void registrationRejectsAnAlreadyRegisteredGoogleAccount() {
        when(users.findByGoogleSubject(identity.subject())).thenReturn(Optional.of(user(identity.email())));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.registerWithGoogle("valid-google-token", "Empresa Aurora"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(platform, never()).signupWithGoogle(any(), any());
    }

    @Test
    void linkRequiresTheGoogleSubjectToBeFreeOrAlreadyOwnedByTheCurrentUser() {
        Usuario current = user("person@example.com");
        ReflectionTestUtils.setField(current, "id", 1L);
        when(users.lockByEmail(current.getEmail())).thenReturn(Optional.of(current));
        when(users.findByGoogleSubject(identity.subject())).thenReturn(Optional.empty());

        auth.linkGoogle("valid-google-token", current.getEmail());

        assertEquals(identity.subject(), current.getGoogleSubject());
        verify(users).save(current);
    }

    @Test
    void linkRejectsAGoogleIdentityOwnedByAnotherUser() {
        Usuario current = user("person@example.com");
        Usuario owner = user("other@example.com");
        ReflectionTestUtils.setField(current, "id", 1L);
        ReflectionTestUtils.setField(owner, "id", 2L);
        when(users.lockByEmail(current.getEmail())).thenReturn(Optional.of(current));
        when(users.findByGoogleSubject(identity.subject())).thenReturn(Optional.of(owner));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.linkGoogle("valid-google-token", current.getEmail()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    private Usuario user(String email) {
        Usuario user = new Usuario();
        user.setNome("Person Example");
        user.setEmail(email);
        user.setSenha("unused");
        user.setTipo(TipoUsuario.CLIENTE);
        return user;
    }
}
