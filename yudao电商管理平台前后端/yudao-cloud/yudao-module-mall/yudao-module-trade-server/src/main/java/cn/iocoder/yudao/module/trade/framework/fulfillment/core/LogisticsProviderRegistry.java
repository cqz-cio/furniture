package cn.iocoder.yudao.module.trade.framework.fulfillment.core;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_PROVIDER_NOT_AVAILABLE;

@Component
public class LogisticsProviderRegistry {

    private final Map<String, LogisticsProviderClient> clients;

    public LogisticsProviderRegistry(List<LogisticsProviderClient> clients) {
        this.clients = clients.stream().collect(Collectors.toUnmodifiableMap(
                client -> client.getProviderCode().toLowerCase(Locale.ROOT),
                Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException("Duplicate logistics provider: " + left.getProviderCode());
                }
        ));
    }

    public LogisticsProviderClient getClient(String providerCode) {
        LogisticsProviderClient client = providerCode == null ? null : clients.get(providerCode.toLowerCase(Locale.ROOT));
        if (client == null) {
            throw exception(FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        }
        return client;
    }

}
