package cn.iocoder.yudao.module.trade.service.fulfillment.support;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Component
public class FulfillmentNoGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();

    private final Clock clock;
    private final SecureRandom secureRandom;

    public FulfillmentNoGenerator() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    public FulfillmentNoGenerator(Clock clock) {
        this(clock, new SecureRandom());
    }

    FulfillmentNoGenerator(Clock clock, SecureRandom secureRandom) {
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public String generate() {
        byte[] random = new byte[8];
        secureRandom.nextBytes(random);
        return "SHP-" + LocalDate.now(clock.withZone(ZoneOffset.UTC)).format(DATE_FORMAT)
                + "-" + UPPER_HEX.formatHex(random);
    }

}
