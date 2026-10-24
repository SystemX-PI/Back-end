package com.systemX.senai;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.systemX.senai.service.EvolutionService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
class EvolutionLifecycleTest {
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
