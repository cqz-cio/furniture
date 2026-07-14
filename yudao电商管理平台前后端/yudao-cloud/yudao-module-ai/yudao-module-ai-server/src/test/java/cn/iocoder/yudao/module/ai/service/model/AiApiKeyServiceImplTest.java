package cn.iocoder.yudao.module.ai.service.model;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.ai.dal.mysql.model.AiApiKeyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.ai.enums.ErrorCodeConstants.API_KEY_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiApiKeyServiceImplTest {

    @InjectMocks
    private AiApiKeyServiceImpl apiKeyService;

    @Mock
    private AiApiKeyMapper apiKeyMapper;

    @Test
    void shouldExposeEmptyProviderRepository() {
        when(apiKeyMapper.selectList()).thenReturn(Collections.emptyList());

        assertThat(apiKeyService.getApiKeyList()).isEmpty();
    }

    @Test
    void shouldReturnConfigurationErrorWhenDefaultKeyIsMissing() {
        String platform = "OPENAI";
        Integer status = CommonStatusEnum.ENABLE.getStatus();
        when(apiKeyMapper.selectFirstByPlatformAndStatus(platform, status)).thenReturn(null);

        assertServiceException(() -> apiKeyService.getRequiredDefaultApiKey(platform, status), API_KEY_NOT_EXISTS);
        verify(apiKeyMapper).selectFirstByPlatformAndStatus(platform, status);
    }

}
