package cn.iocoder.yudao.module.crm.service.clue;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateReqDTO;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.yudao.module.crm.service.permission.CrmPermissionService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 仅使用一次性 H2 库；真实 Mapper、租户拦截器及服务参与验证，不加载应用连接配置。 */
class WebsiteInquiryTenantDbTest {

    private CrmClueMapper mapper;
    private CrmClueServiceImpl service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        TenantContextHolder.clear();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:inquiry_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("sql/website-inquiry-schema.sql"))
                .execute(dataSource);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CrmClueMapper.class);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(
                new TenantDatabaseInterceptor(new TenantProperties())));
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO)));
        factory.setPlugins(interceptor);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(CrmClueMapper.class);
        service = new CrmClueServiceImpl();
        ReflectionTestUtils.setField(service, "clueMapper", mapper);
        ReflectionTestUtils.setField(service, "adminUserApi", mock(AdminUserApi.class));
        ReflectionTestUtils.setField(service, "crmPermissionService", mock(CrmPermissionService.class));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (jdbc != null) {
            jdbc.execute("SHUTDOWN");
        }
    }

    @Test
    void sameExternalIdIsIndependentAcrossTenantsAndIdempotentWithinTenant() {
        TenantContextHolder.setTenantId(90001L);
        var first = service.createWebsiteInquiry(request());
        var retry = service.createWebsiteInquiry(request());
        assertTrue(first.getCreated());
        assertFalse(retry.getCreated());
        assertEquals(first.getInquiryId(), retry.getInquiryId());

        TenantContextHolder.setTenantId(90002L);
        assertNull(mapper.selectByExternalInquiryId("shared-test-id"));
        assertNull(mapper.selectById(first.getInquiryId()));
        var second = service.createWebsiteInquiry(request());
        assertTrue(second.getCreated());
        assertNotEquals(first.getInquiryId(), second.getInquiryId());
        assertEquals(second.getInquiryId(), mapper.selectByExternalInquiryId("shared-test-id").getId());

        TenantContextHolder.setTenantId(90001L);
        assertEquals(first.getInquiryId(), mapper.selectByExternalInquiryId("shared-test-id").getId());
        assertNull(mapper.selectById(second.getInquiryId()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM crm_clue", Integer.class));
    }

    @Test
    void databaseRejectsDuplicateKeyWithinSameTenant() {
        TenantContextHolder.setTenantId(90001L);
        service.createWebsiteInquiry(request());
        assertThrows(DuplicateKeyException.class,
                () -> mapper.insert(new CrmClueDO().setExternalInquiryId("shared-test-id")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_clue", Integer.class));
    }

    @Test
    void categoryTypeAndMultilineMessageRoundTripThroughRealColumns() {
        TenantContextHolder.setTenantId(90001L);
        var result = service.createWebsiteInquiry(request());
        CrmClueDO saved = mapper.selectById(result.getInquiryId());
        assertEquals(90001L, saved.getTenantId());
        assertEquals("Cups & Lids", saved.getCategory());
        assertEquals("samples", saved.getInquiryType());
        assertEquals("First line\nSecond line", saved.getInquiryMessage());
        assertEquals("buyer@example.com", saved.getEmail());
        assertEquals("Test buyer", saved.getContactName());
        assertEquals("Test company", saved.getCompanyName());
    }

    @Test
    void otherTenantCannotUpdateOrDeleteByKnownId() {
        TenantContextHolder.setTenantId(90001L);
        Long id = service.createWebsiteInquiry(request()).getInquiryId();
        TenantContextHolder.setTenantId(90002L);
        assertEquals(0, mapper.updateById(new CrmClueDO().setId(id).setCategory("Other tenant")));
        assertEquals(0, mapper.deleteById(id));
        TenantContextHolder.setTenantId(90001L);
        assertEquals("Cups & Lids", mapper.selectById(id).getCategory());
    }

    @Test
    void missingTenantContextFailsClosed() {
        assertThrows(RuntimeException.class, () -> mapper.selectByExternalInquiryId("shared-test-id"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM crm_clue", Integer.class));
    }

    private static CrmWebsiteInquiryCreateReqDTO request() {
        return new CrmWebsiteInquiryCreateReqDTO()
                .setExternalInquiryId("shared-test-id").setOwnerUserId(91001L)
                .setContactName("Test buyer").setCompanyName("Test company")
                .setEmail("Buyer@Example.com").setSubject("Packaging samples")
                .setMessage("First line\nSecond line").setCategory("Cups & Lids").setInquiryType("samples");
    }
}
