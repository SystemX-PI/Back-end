package com.systemX.senai.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.systemX.senai.repository.*;
import com.systemX.senai.entity.*;
import com.systemX.senai.enums.TipoUsuario;
import java.time.Instant;
import java.util.*;
@Service
public class PlatformCheckoutService {
 private final PlatformService platform;
 private final PlatformCheckoutRepository orders;
 private final PlatformAccountRepository accounts;
 private final PlatformPlans plans;
 private final boolean simulation;
 public PlatformCheckoutService(PlatformService platform,PlatformCheckoutRepository orders,PlatformAccountRepository accounts,PlatformPlans plans,@Value("${app.billing.simulation-enabled:false}") boolean simulation) {
  this.platform=platform;this.orders=orders;this.accounts=accounts;this.plans=plans;this.simulation=simulation;
 }
 private void allowed() {
  if(!simulation) throw error(403,"Compra simulada indisponível neste ambiente.");
  var role=platform.currentUser().getTipo();
  if(role!=TipoUsuario.CLIENTE && role!=TipoUsuario.ADMINISTRADOR) throw error(403,"Somente o titular pode contratar um plano.");
 }
 @Transactional public Map<String,Object> create(Long accountId,String method) {
  allowed();var a=platform.lockAccount(accountId);
  if(a.isSuspended()) throw error(403,"Conta suspensa.");
  if(plans.pro(a)) throw error(409,"O plano Profissional já está ativo.");
  if(!Set.of("PIX","CARD").contains(method)) throw error(400,"Escolha Pix ou cartão de crédito.");
  var order=orders.findFirstByContaIdAndPaymentMethodAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(a.getId(),method,"PENDING",Instant.now()).orElseGet(()->{
   var o=new PlatformCheckout();o.setId(UUID.randomUUID().toString());o.setContaId(a.getId());o.setPaymentMethod(method);o.setAmountCents(plans.priceCents());return orders.save(o);
  });return view(order);
 }
 @Transactional public Map<String,Object> confirm(Long accountId,String orderId) {
  allowed();var a=platform.lockAccount(accountId);
  var o=orders.lockOwned(orderId,a.getId()).orElseThrow(()->error(404,"Pedido não encontrado."));
  if("PAID".equals(o.getStatus())) return view(o);
  if(a.isSuspended()) throw error(403,"Conta suspensa.");
  if(!o.getExpiresAt().isAfter(Instant.now())) throw error(409,"A simulação expirou. Inicie uma nova compra.");
  if(plans.pro(a)) throw error(409,"O plano já foi ativado por outra compra.");
  o.setStatus("PAID");o.setPaidAt(Instant.now());a.setProfessional(true);a.setPaidUntil(Instant.now().plusSeconds(2592000));orders.save(o);accounts.save(a);return view(o);
 }
 private Map<String,Object> view(PlatformCheckout o) {
  var result=new LinkedHashMap<String,Object>();result.put("id",o.getId());result.put("paymentMethod",o.getPaymentMethod());result.put("status",o.getStatus());result.put("amountCents",o.getAmountCents());result.put("currency","BRL");result.put("expiresAt",o.getExpiresAt());result.put("paidAt",o.getPaidAt());result.put("simulation",true);
  result.put("pixCode","PIX".equals(o.getPaymentMethod())?"SIMULACAO-SEM-VALOR-PIX-"+o.getId():null);return result;
 }
 private ResponseStatusException error(int code,String message) { return new ResponseStatusException(HttpStatus.valueOf(code),message); }
}
