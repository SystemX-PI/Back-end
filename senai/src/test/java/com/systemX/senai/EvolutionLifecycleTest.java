package com.systemX.senai;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.systemX.senai.service.EvolutionService;
import com.systemX.senai.service.WhatsAppService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
class EvolutionLifecycleTest {
 @Test void canalDesativadoBloqueiaMesmoQuandoHaChaveEInstancia() {
  var service = new WhatsAppService(false, "http://127.0.0.1:1", "test-only", "padrao");
  assertFalse(service.isEnabled());
  assertFalse(service.canSendFromInstance());
  var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
      () -> service.sendFrom("account-1-suporte", "invalido", "Mensagem de teste"));
  assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
 }
 @Test void envioUsaRemetenteSelecionadoEAdicionaDdiAoNumeroLocal() throws Exception {
  var path=new java.util.concurrent.atomic.AtomicReference<String>();
  var payload=new java.util.concurrent.atomic.AtomicReference<String>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/", exchange->{
   path.set(exchange.getRequestURI().getPath());
   payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
   exchange.sendResponseHeaders(201,-1);
   exchange.close();
  });
  server.start();
  try {
   var service=new WhatsAppService(true,"http://127.0.0.1:"+server.getAddress().getPort(),"test-only","padrao");
   service.sendFrom("account-1-suporte","(11) 99999-0000","Mensagem de teste");
   assertEquals("/message/sendText/account-1-suporte",path.get());
   var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload.get());
   assertEquals("5511999990000",json.path("number").asText());
   assertEquals("Mensagem de teste",json.path("text").asText());
  } finally { server.stop(0); }
 }
 @Test void desconectaEReconectaSemApagarOutraInstancia() throws Exception {
  var calls=new ArrayList<String>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/", exchange->{
   calls.add(exchange.getRequestMethod()+" "+exchange.getRequestURI().getPath());
   String body=exchange.getRequestURI().getPath().contains("connectionState") ? "{\"instance\":{\"state\":\"close\"}}" : "{\"base64\":\"dGVzdA==\"}";
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
  });
  server.start();
  try {
   var service=new EvolutionService("http://127.0.0.1:"+server.getAddress().getPort(),"test-only");
   service.disconnect("account-1-vendas");
   assertNotNull(service.generateQr("account-1-vendas").qrCode());
   assertTrue(calls.contains("DELETE /instance/logout/account-1-vendas"));
   assertTrue(calls.contains("GET /instance/connect/account-1-vendas"));
   assertTrue(calls.stream().noneMatch(x->x.contains("account-2")));
  } finally { server.stop(0); }
 }
}
