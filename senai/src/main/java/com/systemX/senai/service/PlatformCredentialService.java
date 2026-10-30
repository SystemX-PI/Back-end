package com.systemX.senai.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
@Service
public class PlatformCredentialService {
 private static final SecureRandom SECURE_RANDOM = new SecureRandom();
 private final SecretKeySpec key;
 public PlatformCredentialService(@Value("${app.credentials.key:${app.jwt.secret}}") String secret) {
  try { key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)), "AES"); }
  catch (Exception e) { throw new IllegalStateException(e); }
 }
 public String encrypt(String value) {
  try {
   byte[] iv = new byte[12]; SECURE_RANDOM.nextBytes(iv);
   Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
   return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
  } catch(Exception e) { throw new IllegalStateException("Falha ao proteger credenciais.", e); }
 }
 public void verifyGmail(String email, String password) {
  JavaMailSenderImpl sender = new JavaMailSenderImpl();
  sender.setHost("smtp.gmail.com"); sender.setPort(587); sender.setUsername(email); sender.setPassword(password);
  var props = sender.getJavaMailProperties();
  props.put("mail.smtp.auth", "true"); props.put("mail.smtp.starttls.enable", "true"); props.put("mail.smtp.starttls.required", "true");
  props.put("mail.smtp.connectiontimeout", "5000"); props.put("mail.smtp.timeout", "5000"); props.put("mail.smtp.writetimeout", "5000");
  try { sender.testConnection(); } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gmail recusou a conexão. Confira o e-mail e a senha de aplicativo."); }
 }
}
