package com.systemX.senai.service;
import com.systemX.senai.entity.*;
import com.systemX.senai.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
@Service
public class PasswordResetService {
 private final UsuarioRepository users;
 private final PasswordResetTokenRepository tokens;
 private final org.springframework.security.crypto.password.PasswordEncoder encoder;
 private final EmailService mail;
 private final TaskExecutor executor;
 private final byte[] secret;
 private final SecureRandom random=new SecureRandom();
 private final Map<String,Long> requests=new LinkedHashMap<>();
 public PasswordResetService(UsuarioRepository users,PasswordResetTokenRepository tokens,org.springframework.security.crypto.password.PasswordEncoder encoder,EmailService mail,@Qualifier("passwordResetMailExecutor") TaskExecutor executor,@Value("${app.jwt.secret}") String secret) {
  this.users=users;this.tokens=tokens;this.encoder=encoder;this.mail=mail;this.executor=executor;this.secret=secret.getBytes(StandardCharsets.UTF_8);
 }
 private synchronized void throttle(String client) {
  long now=System.currentTimeMillis();requests.entrySet().removeIf(e->now-e.getValue()>60000);
  if(requests.containsKey(client))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Aguarde um minuto antes de solicitar outro código.");
  if(requests.size()>=5000)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Muitas solicitações. Tente novamente em um minuto.");
  requests.put(client,now);
 }
 @Transactional public void request(String email,String client) {
  if(!mail.isEnabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"A recuperação por e-mail ainda não está configurada. Contate o administrador da plataforma.");
  throttle(client);
  var user=users.lockByEmail(email.trim().toLowerCase(Locale.ROOT)).filter(Usuario::isAtivo).orElse(null);
  if(user==null)return;
  var token=tokens.lockByUsuarioId(user.getId()).orElseGet(PasswordResetToken::new);
  if(token.getRequestedAt()!=null&&token.getRequestedAt().isAfter(Instant.now().minusSeconds(60)))return;
  String code=String.format(Locale.ROOT,"%08d",random.nextInt(100000000));
  token.setUsuarioId(user.getId());token.setTokenHash(hash(user.getId(),code));token.setExpiresAt(Instant.now().plusSeconds(300));token.setRequestedAt(Instant.now());token.setUsed(false);token.setAttempts(0);token.setAuthVersion(user.getVersaoAutenticacao());tokens.save(token);
  String recipient=user.getEmail();
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){
   try{executor.execute(()->{try{mail.send(recipient,"Código para redefinir sua senha","Seu código de recuperação é: "+code+"\n\nEle expira em 5 minutos e pode ser usado uma única vez.\nSe você não solicitou esta alteração, ignore este e-mail.");}catch(Exception e){org.slf4j.LoggerFactory.getLogger(PasswordResetService.class).warn("Não foi possível entregar um e-mail de recuperação. Verifique o remetente SMTP.");}});}catch(Exception e){org.slf4j.LoggerFactory.getLogger(PasswordResetService.class).warn("Fila de recuperação por e-mail indisponível.");}
  }});
 }
 @Transactional(noRollbackFor=ResponseStatusException.class) public void reset(String email,String code,String password) {
  ProfileService.validatePassword(password);
  var user=users.lockByEmail(email.trim().toLowerCase(Locale.ROOT)).filter(Usuario::isAtivo).orElseThrow(this::invalid);
  var token=tokens.lockByUsuarioId(user.getId()).orElseThrow(this::invalid);
  if(token.isUsed()||token.getAttempts()>=5||!token.getExpiresAt().isAfter(Instant.now())||token.getAuthVersion()!=user.getVersaoAutenticacao())throw invalid();
  if(!MessageDigest.isEqual(token.getTokenHash().getBytes(StandardCharsets.US_ASCII),hash(user.getId(),code).getBytes(StandardCharsets.US_ASCII))){token.setAttempts(token.getAttempts()+1);tokens.saveAndFlush(token);throw invalid();}
  user.setSenha(encoder.encode(password));user.invalidarSessoes();users.saveAndFlush(user);token.setUsed(true);tokens.saveAndFlush(token);
 }
 private String hash(Long id,String code){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((id+":"+code).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Código inválido, utilizado ou expirado. Solicite um novo código.");}
}
