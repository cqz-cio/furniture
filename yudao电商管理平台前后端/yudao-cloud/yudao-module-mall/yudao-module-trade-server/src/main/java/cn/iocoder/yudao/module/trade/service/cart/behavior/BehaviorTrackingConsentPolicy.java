package cn.iocoder.yudao.module.trade.service.cart.behavior;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import lombok.Value;
import org.springframework.stereotype.Component;
import javax.servlet.http.HttpServletRequest;
@Component
public class BehaviorTrackingConsentPolicy {
 @org.springframework.beans.factory.annotation.Value("${yudao.trade.cart-tracking.enabled:false}") private boolean enabled;
 @org.springframework.beans.factory.annotation.Value("${yudao.trade.cart-tracking.consent-required:true}") private boolean consentRequired;
 public Decision currentDecision(Long userId){
  if(!enabled)return Decision.denied(); HttpServletRequest r=ServletUtils.getRequest();
  if(!consentRequired)return new Decision(true,null,null);
  if(r==null||!"granted".equals(r.getHeader("x-analytics-consent")))return Decision.denied();
  String v=r.getHeader("x-analytics-visitor-id"),s=r.getHeader("x-analytics-session-id");
  if(v==null||v.trim().isEmpty()||s==null||s.trim().isEmpty())return Decision.denied();
  return new Decision(true,v,s);
 }
 @Value public static class Decision { boolean allowed; String visitorId; String sessionId; static Decision denied(){return new Decision(false,null,null);} }
}
