package com.systemX.senai;
import com.systemX.senai.config.JwtService;
import com.systemX.senai.dto.LoginRequest;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.repository.*;
import com.systemX.senai.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
@SpringBootTest @AutoConfigureMockMvc
class ProfileRecoveryIntegrationTest {
 @Autowired MockMvc mvc;@Autowired PlatformService platform;@Autowired UsuarioRepository users;@Autowired JwtService jwt;
 @Autowired PasswordResetTokenRepository resets;@Autowired AuthService auth;@Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
 @MockitoBean EmailService mail;
 @MockitoBean(name="passwordResetMailExecutor") TaskExecutor executor;
 private final AtomicReference<String> delivered=new AtomicReference<>();
 private static final AtomicInteger address=new AtomicInteger();
 private final com.fasterxml.jackson.databind.ObjectMapper mapper=new com.fasterxml.jackson.databind.ObjectMapper();
 @BeforeEach void delivery(){when(mail.isEnabled()).thenReturn(true);doAnswer(call->{delivered.set(call.getArgument(2));return null;}).when(mail).send(anyString(),anyString(),anyString());doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(executor).execute(any(Runnable.class));}
 private Usuario owner(){var r=platform.signup("Teste perfil","profile-"+UUID.randomUUID()+"@test.com","senha123");return users.findById(r.id()).orElseThrow();}
 private String bearer(Usuario u){return "Bearer "+jwt.generate(u);}
 private String request(Usuario u)throws Exception{
  String body=mvc.perform(post("/api/auth/forgot-password").with(r->{r.setRemoteAddr("192.0.2."+address.incrementAndGet());return r;}).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("email",u.getEmail())))).andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(300)).andReturn().getResponse().getContentAsString();
  String code=delivered.get().split(": ")[1].substring(0,8);assertFalse(body.contains(code));return code;
 }
 private org.springframework.test.web.servlet.ResultActions reset(Usuario u,String code)throws Exception{return mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("email",u.getEmail(),"token",code,"newPassword","nova-senha-123"))));}
 @Test void fotoDoProprioPerfilESenhaAtualComSessaoRenovada()throws Exception{
  var user=owner();String old=bearer(user);
  var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"png",bytes);String photo="data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
  mvc.perform(put("/api/platform/profile").header("Authorization",old).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("name","Meu perfil","photo",photo)))).andExpect(status().isNoContent());
  mvc.perform(get("/api/platform/context").header("Authorization",old)).andExpect(jsonPath("$.name").value("Meu perfil")).andExpect(jsonPath("$.photo").value(photo));
  mvc.perform(put("/api/platform/profile/password").header("Authorization",old).contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"incorreta\",\"newPassword\":\"nova-senha-123\"}")).andExpect(status().isBadRequest());
  String response=mvc.perform(put("/api/platform/profile/password").header("Authorization",old).contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"senha123\",\"newPassword\":\"nova-senha-123\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String fresh=mapper.readTree(response).path("token").asText();
  mvc.perform(get("/api/platform/context").header("Authorization",old)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/platform/context").header("Authorization","Bearer "+fresh)).andExpect(status().isOk());
 }
 @Test void recuperacaoExpiraEmCincoMinutosEUsaCodigoUmaVez()throws Exception{
  var user=owner();String old=bearer(user);var session=auth.login(new LoginRequest(user.getEmail(),"senha123"));String code=request(user);
  var stored=resets.findByUsuarioId(user.getId()).orElseThrow();assertNotEquals(code,stored.getTokenHash());assertTrue(stored.getExpiresAt().isAfter(Instant.now().plusSeconds(285)));assertTrue(stored.getExpiresAt().isBefore(Instant.now().plusSeconds(301)));
  reset(user,code).andExpect(status().isNoContent());reset(user,code).andExpect(status().isBadRequest());
  mvc.perform(get("/api/platform/context").header("Authorization",old)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("refreshToken",session.refreshToken())))).andExpect(status().isUnauthorized());
  assertTrue(encoder.matches("nova-senha-123",users.findById(user.getId()).orElseThrow().getSenha()));
 }
 @Test void codigoExpiradoOuCincoTentativasNaoPermitemAlterarSenha()throws Exception{
  var expired=owner();String code=request(expired);var stored=resets.findByUsuarioId(expired.getId()).orElseThrow();stored.setExpiresAt(Instant.now().minusSeconds(1));resets.saveAndFlush(stored);reset(expired,code).andExpect(status().isBadRequest());
  var attempts=owner();String valid=request(attempts);String wrong=valid.equals("00000000")?"11111111":"00000000";
  for(int i=0;i<5;i++)reset(attempts,wrong).andExpect(status().isBadRequest());assertEquals(5,resets.findByUsuarioId(attempts.getId()).orElseThrow().getAttempts());reset(attempts,valid).andExpect(status().isBadRequest());assertTrue(encoder.matches("senha123",users.findById(attempts.getId()).orElseThrow().getSenha()));
 }
 @Test void remetenteAusenteNaoSimulaEnvioEEnderecoDesconhecidoNaoRevelaCadastro()throws Exception{
  when(mail.isEnabled()).thenReturn(false);
  mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"unknown@test.com\"}")).andExpect(status().isServiceUnavailable());
  when(mail.isEnabled()).thenReturn(true);
  mvc.perform(post("/api/auth/forgot-password").with(r->{r.setRemoteAddr("192.0.2."+address.incrementAndGet());return r;}).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"unknown@test.com\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(300));verify(mail,never()).send(anyString(),anyString(),anyString());
 }
}
