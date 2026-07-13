package cn.iocoder.yudao.module.trade.service.cart.behavior;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
class BehaviorTrackingConsentPolicyTest {
 @AfterEach void clean(){RequestContextHolder.resetRequestAttributes();}
 @Test void consentRequired_rejectsMissingEvidenceAndAcceptsVerifiedHeaders(){
  BehaviorTrackingConsentPolicy policy=policy(true,true); MockHttpServletRequest request=new MockHttpServletRequest(); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  assertFalse(policy.currentDecision(9L).isAllowed());
  request.addHeader("x-analytics-consent","granted"); request.addHeader("x-analytics-visitor-id","v"); request.addHeader("x-analytics-session-id","s");
  assertTrue(policy.currentDecision(9L).isAllowed());
 }
 @Test void killSwitchOff_rejectsEvenWithHeaders(){assertFalse(policy(false,false).currentDecision(9L).isAllowed());}
 private BehaviorTrackingConsentPolicy policy(boolean enabled,boolean required){BehaviorTrackingConsentPolicy p=new BehaviorTrackingConsentPolicy(); ReflectionTestUtils.setField(p,"enabled",enabled);ReflectionTestUtils.setField(p,"consentRequired",required);return p;}
}
