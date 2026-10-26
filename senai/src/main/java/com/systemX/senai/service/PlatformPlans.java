package com.systemX.senai.service;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import com.systemX.senai.entity.PlatformAccount;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
@Component
public class PlatformPlans {
 private final int priceCents;
 public PlatformPlans(@Value("${app.billing.demo-price-cents:9990}") int priceCents) { this.priceCents=priceCents; }
 public boolean pro(PlatformAccount a) { return a.isProfessional() && a.getPaidUntil()!=null && a.getPaidUntil().isAfter(Instant.now()); }
 public int whatsappLimit(PlatformAccount a) { return pro(a)?3:1; }
 public int gmailLimit(PlatformAccount a) { return pro(a)?2:1; }
 public int priceCents() { return priceCents; }
 public Map<String,Object> view(PlatformAccount a) {
  var view=new LinkedHashMap<String,Object>();
  view.put("tier",pro(a)?"PRO":"TRIAL"); view.put("whatsappLimit",whatsappLimit(a)); view.put("gmailLimit",gmailLimit(a)); view.put("upgradePriceCents",priceCents); view.put("currency","BRL");
  view.put("billingProvider",a.getStripeSubscriptionId()!=null?"STRIPE":null); view.put("billingStatus",a.getStripeSubscriptionStatus());
  return view;
 }
}
