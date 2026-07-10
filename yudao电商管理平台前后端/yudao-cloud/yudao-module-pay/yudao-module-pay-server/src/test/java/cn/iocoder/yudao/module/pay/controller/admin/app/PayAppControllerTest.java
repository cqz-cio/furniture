package cn.iocoder.yudao.module.pay.controller.admin.app;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PayAppController} unit tests.
 */
public class PayAppControllerTest {

    @Test
    public void testGetAppList_usesAppQueryPermission() throws NoSuchMethodException {
        PreAuthorize preAuthorize = PayAppController.class
                .getMethod("getAppList")
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('pay:app:query')", preAuthorize.value());
    }

}
