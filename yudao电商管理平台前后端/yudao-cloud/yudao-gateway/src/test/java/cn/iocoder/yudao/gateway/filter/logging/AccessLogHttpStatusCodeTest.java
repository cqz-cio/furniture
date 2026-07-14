package cn.iocoder.yudao.gateway.filter.logging;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;

import static org.junit.jupiter.api.Assertions.assertSame;

class AccessLogHttpStatusCodeTest {

    @Test
    void acceptsSpringSixHttpStatusCode() {
        HttpStatusCode statusCode = HttpStatusCode.valueOf(599);
        AccessLog accessLog = new AccessLog();

        accessLog.setHttpStatus(statusCode);

        assertSame(statusCode, accessLog.getHttpStatus());
    }

}
