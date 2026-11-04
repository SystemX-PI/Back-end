package com.systemX.senai.service;
import com.systemX.senai.dto.*;
import com.systemX.senai.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
@Service
public class ProfileService {
 private final PlatformService platform;
 private final UsuarioRepository users;
 private final PasswordEncoder encoder;
 private final AuthService auth;
 public ProfileService(PlatformService platform,UsuarioRepository users,PasswordEncoder encoder,AuthService auth) {this.platform=platform;this.users=users;this.encoder=encoder;this.auth=auth;}
 @Transactional public void update(String name,String photo) {
  validatePhoto(photo);var user=platform.currentUser();user.setNome(name.trim());user.setFoto(photo==null||photo.isBlank()?null:photo);users.save(user);
 }
 @Transactional public AuthResponse changePassword(String previous,String password) {
   var me=platform.currentUser();
   var user=users.lockByEmail(me.getEmail()).orElseThrow();
  if(!encoder.matches(previous,user.getSenha())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A senha atual está incorreta.");
  validatePassword(password);
  if(encoder.matches(password,user.getSenha()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Escolha uma senha diferente da atual.");
  user.setSenha(encoder.encode(password));user.invalidarSessoes();users.saveAndFlush(user);
  return auth.login(new LoginRequest(user.getEmail(),password));
 }
 public static void validatePassword(String password) {
  if(password==null||password.length()<10||password.getBytes(StandardCharsets.UTF_8).length>72)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A nova senha deve ter pelo menos 10 caracteres e no máximo 72 bytes.");
 }
 private void validatePhoto(String photo) {
  if(photo==null||photo.isBlank()) return;
  try {
   if(photo.length()>400000||!photo.matches("^data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+$")) throw new IllegalArgumentException();
   byte[] bytes=Base64.getDecoder().decode(photo.substring(photo.indexOf(',')+1));
   if(bytes.length>280000) throw new IllegalArgumentException();
    try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
     var readers=ImageIO.getImageReaders(input);
     if (!readers.hasNext()) {
      throw new IllegalArgumentException();
     }
     var reader=readers.next();
     try {
      reader.setInput(input);
      if(reader.getWidth(0)>2048||reader.getHeight(0)>2048||reader.getWidth(0)<1||reader.getHeight(0)<1)throw new IllegalArgumentException();
      if(reader.read(0)==null)throw new IllegalArgumentException();
     } finally {
      reader.dispose();
     }
   }
  }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Envie uma foto PNG ou JPG válida, de até 280 KB e 2048 pixels.");}
 }
}
