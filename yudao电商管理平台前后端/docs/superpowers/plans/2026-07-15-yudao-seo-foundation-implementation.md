# Yudao SEO Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Yudao ERP 中交付可独立运行的 SEO 基础模块，使管理员可以配置站点 SEO 默认值，维护并发布商品、分类、文章和页面的 SEO 元数据，并由前台通过稳定的公开接口取得已发布数据。

**Architecture:** 后端新增独立的 `yudao-module-seo`（API + Server）并接入 `yudao-server`，数据按租户、站点、实体类型、实体 ID 和语言隔离。管理端使用现有 Vue 3 + Element Plus 规范实现站点设置和元数据工作台。公开查询通过单独的 App Controller 暴露，只返回已发布版本；分析评分、文件解析、Sitemap、robots、重定向和前台 SSR Head 分别由后续计划基于本计划的稳定接口继续实现。

**Tech Stack:** Java 17、Spring Boot、MyBatis Plus、Jakarta Validation、JUnit 5、H2/MySQL、Vue 3、TypeScript、Element Plus、Vite、Node.js contract tests。

## Global Constraints

- 工作分支固定为 `codex/agent-rag`；不得修改、合并或推送 `main`。
- 每个实现任务都遵循红灯—绿灯—重构：先新增失败测试，再写最小实现，再运行相关测试。
- 只暂存并提交当前任务的文件；保留现有 `pnpm-lock.yaml` 修改和所有无关未跟踪文件。
- 不复制 Yoast GPL 源码。本阶段只建立 ERP 自有的数据模型和元数据管理闭环。
- 所有表都继承 Yudao 的审计、逻辑删除和租户字段约定；所有管理端接口都要求权限校验。
- `siteId` 必须显式传入，`tenantId` 必须从租户上下文取得，禁止接受客户端提交的 `tenantId`。
- 第一阶段实体类型固定为 `PRODUCT`、`CATEGORY`、`ARTICLE`、`PAGE`，语言使用 BCP 47 风格字符串，默认 `zh-CN`。
- 元数据唯一键为 `(tenant_id, site_id, entity_type, entity_id, locale, deleted)`；公开接口只返回 `PUBLISHED` 状态。
- 标题、描述等用户输入按纯文本保存；Canonical URL 必须是绝对 HTTP(S) URL；不允许将任意 HTML 直接回显到管理页面。
- 后续计划的稳定扩展点是 `SeoMetadataService#getPublishedMetadata(Long, String, Long, String)` 和 `SeoPublicMetadataRespVO`，本计划完成后不得随意破坏其字段语义。

---

## File Map

### Backend build and module wiring

- Modify: `yudao-cloud/pom.xml`
- Modify: `yudao-cloud/yudao-server/pom.xml`
- Create: `yudao-cloud/yudao-module-seo/pom.xml`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-api/pom.xml`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/pom.xml`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/SeoServerApplication.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/resources/application.yaml`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/resources/application-unit-test.yaml`

### Shared API and persistence

- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-api/src/main/java/cn/iocoder/yudao/module/seo/enums/SeoEntityTypeEnum.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-api/src/main/java/cn/iocoder/yudao/module/seo/enums/SeoPublishStatusEnum.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-api/src/main/java/cn/iocoder/yudao/module/seo/enums/ErrorCodeConstants.java`
- Create: `yudao-cloud/sql/mysql/migrations/V015__seo_foundation.sql`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/resources/sql/create_tables.sql`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/resources/sql/clean.sql`

### Site configuration feature

- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/dataobject/config/SeoSiteConfigDO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/mysql/config/SeoSiteConfigMapper.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/config/vo/SeoSiteConfigSaveReqVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/config/vo/SeoSiteConfigRespVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/service/config/SeoSiteConfigService.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/service/config/SeoSiteConfigServiceImpl.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/config/SeoSiteConfigController.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/java/cn/iocoder/yudao/module/seo/service/config/SeoSiteConfigServiceImplTest.java`

### Metadata feature

- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/dataobject/metadata/SeoMetadataDO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/mysql/metadata/SeoMetadataMapper.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/metadata/vo/SeoMetadataPageReqVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/metadata/vo/SeoMetadataSaveReqVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/metadata/vo/SeoMetadataRespVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/app/metadata/vo/SeoPublicMetadataRespVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/service/metadata/SeoMetadataService.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/service/metadata/SeoMetadataServiceImpl.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/metadata/SeoMetadataController.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/app/metadata/AppSeoMetadataController.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/java/cn/iocoder/yudao/module/seo/service/metadata/SeoMetadataServiceImplTest.java`

### Admin UI

- Modify: `yudao-ui-admin-vue3/package.json`
- Create: `yudao-ui-admin-vue3/scripts/check-seo-foundation-contract.mjs`
- Create: `yudao-ui-admin-vue3/src/api/seo/siteConfig/index.ts`
- Create: `yudao-ui-admin-vue3/src/api/seo/metadata/index.ts`
- Create: `yudao-ui-admin-vue3/src/views/seo/siteConfig/index.vue`
- Create: `yudao-ui-admin-vue3/src/views/seo/metadata/index.vue`
- Create: `yudao-ui-admin-vue3/src/views/seo/metadata/MetadataForm.vue`

---

## Task 1: Create and wire the SEO Maven module

**Files:**

- Modify: `yudao-cloud/pom.xml`
- Modify: `yudao-cloud/yudao-server/pom.xml`
- Create: `yudao-cloud/yudao-module-seo/pom.xml`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-api/pom.xml`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/pom.xml`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/SeoServerApplication.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/resources/application.yaml`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/java/cn/iocoder/yudao/module/seo/SeoModuleSmokeTest.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/resources/application-unit-test.yaml`

- [ ] **Step 1: Add a smoke test before registering the module**

Create `SeoModuleSmokeTest.java`:

```java
package cn.iocoder.yudao.module.seo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeoModuleSmokeTest {

    @Test
    void seoApplicationClassShouldExist() {
        assertThat(SeoServerApplication.class).isNotNull();
    }
}
```

- [ ] **Step 2: Run the focused test and verify the red state**

Run from `yudao-cloud`:

```powershell
mvn -pl yudao-module-seo/yudao-module-seo-server -am -Dtest=SeoModuleSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: Maven reports that the requested module is not in the reactor, or compilation fails because `SeoServerApplication` does not exist.

- [ ] **Step 3: Add the minimum module structure**

Mirror the dependency and plugin structure of `yudao-module-wms`, using artifact IDs:

```xml
<artifactId>yudao-module-seo</artifactId>
<modules>
    <module>yudao-module-seo-api</module>
    <module>yudao-module-seo-server</module>
</modules>
```

The API module depends on `yudao-common`. The Server module depends on its own API plus the same web, security, tenant, MyBatis and test starters used by WMS. Do not add AI, Elasticsearch or document parsing dependencies in this foundation plan.

Register `<module>yudao-module-seo</module>` in `yudao-cloud/pom.xml`, and add the following dependency to `yudao-server/pom.xml`:

```xml
<dependency>
    <groupId>cn.iocoder.boot</groupId>
    <artifactId>yudao-module-seo-server</artifactId>
    <version>${revision}</version>
</dependency>
```

Create the application marker:

```java
@SpringBootApplication
public class SeoServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeoServerApplication.class, args);
    }
}
```

Use the WMS unit-test YAML as the baseline, changing only module-specific SQL resource paths and application name.

- [ ] **Step 4: Re-run the focused test**

Run the same Maven command.

Expected: `SeoModuleSmokeTest` passes and the reactor includes both SEO submodules.

- [ ] **Step 5: Verify server aggregation**

```powershell
mvn -pl yudao-server -am -DskipTests package
```

Expected: `BUILD SUCCESS`, including `yudao-module-seo-api`, `yudao-module-seo-server`, and `yudao-server`.

- [ ] **Step 6: Commit only module wiring files**

```powershell
git add -- yudao电商管理平台前后端/yudao-cloud/pom.xml yudao电商管理平台前后端/yudao-cloud/yudao-server/pom.xml yudao电商管理平台前后端/yudao-cloud/yudao-module-seo
git commit -m "feat(seo): scaffold seo module"
```

---

## Task 2: Define enums, error codes and the foundation schema

**Files:**

- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-api/src/main/java/cn/iocoder/yudao/module/seo/enums/SeoEntityTypeEnum.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-api/src/main/java/cn/iocoder/yudao/module/seo/enums/SeoPublishStatusEnum.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-api/src/main/java/cn/iocoder/yudao/module/seo/enums/ErrorCodeConstants.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-api/src/test/java/cn/iocoder/yudao/module/seo/enums/SeoEnumTest.java`
- Create: `yudao-cloud/sql/mysql/migrations/V015__seo_foundation.sql`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/resources/sql/create_tables.sql`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/resources/sql/clean.sql`

- [ ] **Step 1: Write enum contract tests**

Test exact external values so later API consumers cannot silently drift:

```java
assertThat(SeoEntityTypeEnum.values())
        .extracting(SeoEntityTypeEnum::getCode)
        .containsExactly("PRODUCT", "CATEGORY", "ARTICLE", "PAGE");
assertThat(SeoPublishStatusEnum.values())
        .extracting(SeoPublishStatusEnum::getCode)
        .containsExactly("DRAFT", "PUBLISHED");
assertThat(SeoEntityTypeEnum.isValid("PRODUCT")).isTrue();
assertThat(SeoEntityTypeEnum.isValid("product")).isFalse();
```

- [ ] **Step 2: Run the enum test and verify compilation fails**

```powershell
mvn -pl yudao-module-seo/yudao-module-seo-api -am -Dtest=SeoEnumTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing enum classes.

- [ ] **Step 3: Implement the enum and error-code contracts**

Allocate SEO errors from the unused `1-070` segment:

```java
ErrorCode SITE_CONFIG_NOT_EXISTS = new ErrorCode(1_070_001_000, "SEO 站点配置不存在");
ErrorCode METADATA_NOT_EXISTS = new ErrorCode(1_070_002_000, "SEO 元数据不存在");
ErrorCode METADATA_DUPLICATE = new ErrorCode(1_070_002_001, "该实体和语言的 SEO 元数据已存在");
ErrorCode ENTITY_TYPE_INVALID = new ErrorCode(1_070_002_002, "SEO 实体类型不支持");
ErrorCode METADATA_VERSION_CONFLICT = new ErrorCode(1_070_002_003, "SEO 元数据已被其他用户修改，请刷新后重试");
```

Enums expose `getCode()` and null-safe `isValid(String)`.

- [ ] **Step 4: Create MySQL and H2 schemas**

`V015__seo_foundation.sql` creates:

```sql
CREATE TABLE seo_site_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    site_id BIGINT NOT NULL,
    site_name VARCHAR(128) NOT NULL,
    site_url VARCHAR(512) NOT NULL,
    default_title_suffix VARCHAR(128) NOT NULL DEFAULT '',
    default_description VARCHAR(500) NOT NULL DEFAULT '',
    default_robots VARCHAR(64) NOT NULL DEFAULT 'index,follow',
    default_og_image VARCHAR(1024) NOT NULL DEFAULT '',
    default_locale VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_site_deleted (tenant_id, site_id, deleted)
);

CREATE TABLE seo_metadata (
    id BIGINT NOT NULL AUTO_INCREMENT,
    site_id BIGINT NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    locale VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    seo_title VARCHAR(255) NOT NULL DEFAULT '',
    meta_description VARCHAR(500) NOT NULL DEFAULT '',
    focus_keyphrase VARCHAR(255) NOT NULL DEFAULT '',
    related_keyphrases JSON NULL,
    canonical_url VARCHAR(1024) NOT NULL DEFAULT '',
    robots_index BIT(1) NOT NULL DEFAULT b'1',
    robots_follow BIT(1) NOT NULL DEFAULT b'1',
    og_title VARCHAR(255) NOT NULL DEFAULT '',
    og_description VARCHAR(500) NOT NULL DEFAULT '',
    og_image VARCHAR(1024) NOT NULL DEFAULT '',
    schema_type VARCHAR(64) NOT NULL DEFAULT '',
    publish_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 1,
    published_time DATETIME NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_entity_locale_deleted
        (tenant_id, site_id, entity_type, entity_id, locale, deleted),
    KEY idx_public_resolve
        (tenant_id, site_id, entity_type, entity_id, locale, publish_status)
);
```

Add menu rows under a top-level `SEO 管理` menu for `内容优化` and `站点设置`, plus button permissions:

- `seo:metadata:query`
- `seo:metadata:create`
- `seo:metadata:update`
- `seo:metadata:delete`
- `seo:metadata:publish`
- `seo:site-config:query`
- `seo:site-config:update`

Follow current migration conventions for deterministic menu IDs and `system_menu` columns. Use H2-compatible equivalents in `create_tables.sql`, including JSON stored as `VARCHAR`, and truncate both tables in `clean.sql`.

- [ ] **Step 5: Re-run the enum test and parse the migration**

```powershell
mvn -pl yudao-module-seo/yudao-module-seo-api -am -Dtest=SeoEnumTest -Dsurefire.failIfNoSpecifiedTests=false test
Select-String -Path sql/mysql/migrations/V015__seo_foundation.sql -Pattern "seo_site_config","seo_metadata","seo:metadata:publish"
```

Expected: enum test passes; each required schema/menu token appears.

- [ ] **Step 6: Commit schema contracts**

```powershell
git add -- yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-api yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/resources/sql yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V015__seo_foundation.sql
git commit -m "feat(seo): define foundation schema contracts"
```

---

## Task 3: Implement site configuration with upsert semantics

**Files:**

- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/dataobject/config/SeoSiteConfigDO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/mysql/config/SeoSiteConfigMapper.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/config/vo/SeoSiteConfigSaveReqVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/config/vo/SeoSiteConfigRespVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/service/config/SeoSiteConfigService.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/service/config/SeoSiteConfigServiceImpl.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/config/SeoSiteConfigController.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/java/cn/iocoder/yudao/module/seo/service/config/SeoSiteConfigServiceImplTest.java`

- [ ] **Step 1: Write database-backed service tests**

Extend `BaseDbUnitTest`, import the implementation, and inject the real mapper. Cover:

```java
@Test
void saveSiteConfig_shouldInsertDefaultsAndNormalizeUrl();

@Test
void saveSiteConfig_shouldUpdateExistingRowWithoutChangingId();

@Test
void getSiteConfig_shouldReturnNullForAnotherTenant();

@Test
void saveSiteConfig_shouldRejectNonHttpUrl();
```

The first assertion set must verify:

```java
assertThat(saved.getSiteUrl()).isEqualTo("https://shop.example.com");
assertThat(saved.getDefaultLocale()).isEqualTo("zh-CN");
assertThat(saved.getDefaultRobots()).isEqualTo("index,follow");
```

- [ ] **Step 2: Run the tests and verify missing implementation failures**

```powershell
mvn -pl yudao-module-seo/yudao-module-seo-server -am -Dtest=SeoSiteConfigServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails on missing site config types.

- [ ] **Step 3: Implement the persistence model and mapper**

`SeoSiteConfigDO` extends `TenantBaseDO` and maps every column in `seo_site_config`. The mapper extends `BaseMapperX<SeoSiteConfigDO>` and exposes:

```java
default SeoSiteConfigDO selectBySiteId(Long siteId) {
    return selectOne(SeoSiteConfigDO::getSiteId, siteId);
}
```

Do not accept `tenantId` in any request VO.

- [ ] **Step 4: Implement validation and upsert behavior**

Request contract:

```java
@Data
public class SeoSiteConfigSaveReqVO {
    @NotNull private Long siteId;
    @NotBlank @Size(max = 128) private String siteName;
    @NotBlank @Size(max = 512) private String siteUrl;
    @Size(max = 128) private String defaultTitleSuffix;
    @Size(max = 500) private String defaultDescription;
    @Size(max = 64) private String defaultRobots;
    @Size(max = 1024) private String defaultOgImage;
    @Size(max = 32) private String defaultLocale;
}
```

Service contract:

```java
void saveSiteConfig(SeoSiteConfigSaveReqVO reqVO);
SeoSiteConfigDO getSiteConfig(Long siteId);
SeoSiteConfigDO getRequiredSiteConfig(Long siteId);
```

Normalization rules:

- Parse `siteUrl` with `URI`; require `http` or `https`, require host, remove trailing `/` from the origin path.
- Convert blank optional strings to documented defaults, not `null`.
- On existing `(tenant, site)` row, update that row; otherwise insert.

- [ ] **Step 5: Add the admin controller**

Expose:

```text
GET  /admin-api/seo/site-config/get?siteId={siteId}
PUT  /admin-api/seo/site-config/save
```

Use permissions `seo:site-config:query` and `seo:site-config:update`, `CommonResult`, `BeanUtils`, `@Valid`, and `@Operation` consistent with nearby modules.

- [ ] **Step 6: Run tests and module compilation**

```powershell
mvn -pl yudao-module-seo/yudao-module-seo-server -am -Dtest=SeoSiteConfigServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl yudao-module-seo/yudao-module-seo-server -am -DskipTests package
```

Expected: all site config tests pass; module packages successfully.

- [ ] **Step 7: Commit the site configuration slice**

```powershell
git add -- yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/config yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/dataobject/config yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/mysql/config yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/service/config yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/java/cn/iocoder/yudao/module/seo/service/config
git commit -m "feat(seo): add site configuration"
```

---

## Task 4: Implement metadata draft, publish and public resolution

**Files:**

- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/dataobject/metadata/SeoMetadataDO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/mysql/metadata/SeoMetadataMapper.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/metadata/vo/SeoMetadataPageReqVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/metadata/vo/SeoMetadataSaveReqVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/metadata/vo/SeoMetadataRespVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/app/metadata/vo/SeoPublicMetadataRespVO.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/service/metadata/SeoMetadataService.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/service/metadata/SeoMetadataServiceImpl.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/metadata/SeoMetadataController.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/app/metadata/AppSeoMetadataController.java`
- Create: `yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/java/cn/iocoder/yudao/module/seo/service/metadata/SeoMetadataServiceImplTest.java`

- [ ] **Step 1: Write service tests for the state machine and tenant boundary**

Cover at least:

```java
@Test
void createMetadata_shouldPersistDraftAndRelatedKeyphrases();

@Test
void createMetadata_shouldRejectDuplicateEntityLocale();

@Test
void updateMetadata_shouldRejectStaleVersion();

@Test
void publishMetadata_shouldSetStatusVersionAndPublishedTime();

@Test
void getPublishedMetadata_shouldIgnoreDraft();

@Test
void getPublishedMetadata_shouldNotCrossTenantBoundary();

@Test
void saveMetadata_shouldRejectUnsupportedEntityType();

@Test
void saveMetadata_shouldRejectRelativeCanonicalUrl();
```

Use `TenantContextHolder.setTenantId(1L)` in setup and `TenantContextHolder.clear()` in teardown, following an existing tenant-aware module test.

- [ ] **Step 2: Run the focused test and confirm red**

```powershell
mvn -pl yudao-module-seo/yudao-module-seo-server -am -Dtest=SeoMetadataServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails on missing metadata types.

- [ ] **Step 3: Implement the DO and mapper**

`SeoMetadataDO` extends `TenantBaseDO`; map `relatedKeyphrases` with the project JSON type handler and mark `version` with the project-supported optimistic locking annotation/configuration. Required mapper methods:

```java
SeoMetadataDO selectByEntity(Long siteId, String entityType, Long entityId, String locale);
SeoMetadataDO selectPublished(Long siteId, String entityType, Long entityId, String locale);
PageResult<SeoMetadataDO> selectPage(SeoMetadataPageReqVO reqVO);
```

All query wrappers include `siteId` and applicable entity filters. Tenant interception supplies the tenant predicate.

- [ ] **Step 4: Define request and response contracts**

Save request fields:

```java
Long id;                // null for create
@NotNull Long siteId;
@NotBlank String entityType;
@NotNull Long entityId;
@NotBlank String locale;
@Size(max = 255) String seoTitle;
@Size(max = 500) String metaDescription;
@Size(max = 255) String focusKeyphrase;
List<@Size(max = 255) String> relatedKeyphrases;
@Size(max = 1024) String canonicalUrl;
Boolean robotsIndex;
Boolean robotsFollow;
@Size(max = 255) String ogTitle;
@Size(max = 500) String ogDescription;
@Size(max = 1024) String ogImage;
@Size(max = 64) String schemaType;
Integer version;        // required for update
```

Page request extends `PageParam` and supports `siteId`, `entityType`, `entityId`, `locale`, `publishStatus`, and free-text `keyword`. Response includes all editable fields plus `publishStatus`, `version`, `publishedTime`, `createTime`, and `updateTime`.

Public response is intentionally smaller and stable:

```java
String title;
String description;
String canonicalUrl;
Boolean robotsIndex;
Boolean robotsFollow;
String ogTitle;
String ogDescription;
String ogImage;
String schemaType;
String locale;
Integer version;
```

It must not expose focus keyphrases, audit users, database IDs, tenant IDs or draft state.

- [ ] **Step 5: Implement service behavior**

Service interface:

```java
Long createMetadata(SeoMetadataSaveReqVO reqVO);
void updateMetadata(SeoMetadataSaveReqVO reqVO);
void deleteMetadata(Long id);
void publishMetadata(Long id, Integer version);
SeoMetadataDO getMetadata(Long id);
PageResult<SeoMetadataDO> getMetadataPage(SeoMetadataPageReqVO reqVO);
SeoMetadataDO getPublishedMetadata(
        Long siteId, String entityType, Long entityId, String locale);
```

Behavior:

- Create always starts as `DRAFT`, version `1`, and validates entity type/canonical URL.
- Update cannot alter `siteId`, `entityType`, `entityId`, or `locale` through an ID switch; it updates editable fields and increments version.
- If the submitted version is stale, throw `METADATA_VERSION_CONFLICT`.
- Publish validates the submitted version, sets `PUBLISHED`, sets `publishedTime`, and increments version in the same database update.
- Updating a published row keeps it published in phase one. The UI clearly labels that saves affect the live version; immutable publication history belongs to a later revision feature.
- Delete uses logical deletion and requires the row to exist in the current tenant.
- Public resolution returns only `PUBLISHED` rows and exact locale matches. Locale fallback is intentionally delegated to the storefront integration plan.

- [ ] **Step 6: Add admin and App controllers**

Admin endpoints:

```text
GET    /admin-api/seo/metadata/page
GET    /admin-api/seo/metadata/get?id={id}
POST   /admin-api/seo/metadata/create
PUT    /admin-api/seo/metadata/update
DELETE /admin-api/seo/metadata/delete?id={id}
PUT    /admin-api/seo/metadata/publish?id={id}&version={version}
```

App endpoint:

```text
GET /app-api/seo/metadata/resolve
    ?siteId={siteId}&entityType={type}&entityId={id}&locale={locale}
```

The App endpoint returns `CommonResult<SeoPublicMetadataRespVO>` with `data: null` when no published exact match exists. Validate entity type before querying.

- [ ] **Step 7: Run all metadata and SEO module tests**

```powershell
mvn -pl yudao-module-seo/yudao-module-seo-server -am -Dtest=SeoMetadataServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl yudao-module-seo/yudao-module-seo-server -am test
```

Expected: state transition, validation, tenant and public-resolution tests all pass.

- [ ] **Step 8: Commit the metadata slice**

```powershell
git add -- yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/admin/metadata yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/app/metadata yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/dataobject/metadata yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/dal/mysql/metadata yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/service/metadata yudao电商管理平台前后端/yudao-cloud/yudao-module-seo/yudao-module-seo-server/src/test/java/cn/iocoder/yudao/module/seo/service/metadata
git commit -m "feat(seo): add metadata publishing workflow"
```

---

## Task 5: Build the admin UI with an executable contract test

**Files:**

- Modify: `yudao-ui-admin-vue3/package.json`
- Create: `yudao-ui-admin-vue3/scripts/check-seo-foundation-contract.mjs`
- Create: `yudao-ui-admin-vue3/src/api/seo/siteConfig/index.ts`
- Create: `yudao-ui-admin-vue3/src/api/seo/metadata/index.ts`
- Create: `yudao-ui-admin-vue3/src/views/seo/siteConfig/index.vue`
- Create: `yudao-ui-admin-vue3/src/views/seo/metadata/index.vue`
- Create: `yudao-ui-admin-vue3/src/views/seo/metadata/MetadataForm.vue`

- [ ] **Step 1: Write the failing source contract test**

Follow `scripts/check-dashboard-contract.mjs`. The new script reads the files above and asserts exact route tokens, exports and critical labels:

```javascript
assert.match(siteApi, /\/seo\/site-config\/get/)
assert.match(siteApi, /\/seo\/site-config\/save/)
assert.match(metadataApi, /\/seo\/metadata\/page/)
assert.match(metadataApi, /\/seo\/metadata\/publish/)
assert.match(metadataPage, /内容类型/)
assert.match(metadataPage, /发布状态/)
assert.match(metadataForm, /焦点关键词/)
assert.match(metadataForm, /Canonical URL/)
assert.match(metadataForm, /保存后将影响线上版本/)
```

Add without changing the lockfile:

```json
"check:seo-foundation": "node scripts/check-seo-foundation-contract.mjs"
```

- [ ] **Step 2: Run the contract test and verify red**

```powershell
node scripts/check-seo-foundation-contract.mjs
```

Expected: missing API/view file error or assertion failure.

- [ ] **Step 3: Implement typed API clients**

Site configuration API exports:

```typescript
export interface SeoSiteConfig {
  id?: number
  siteId: number
  siteName: string
  siteUrl: string
  defaultTitleSuffix: string
  defaultDescription: string
  defaultRobots: string
  defaultOgImage: string
  defaultLocale: string
}
export const getSeoSiteConfig = (siteId: number) =>
  request.get({ url: '/seo/site-config/get', params: { siteId } })
export const saveSeoSiteConfig = (data: SeoSiteConfig) =>
  request.put({ url: '/seo/site-config/save', data })
```

Metadata API exports:

```typescript
export type SeoEntityType = 'PRODUCT' | 'CATEGORY' | 'ARTICLE' | 'PAGE'
export type SeoPublishStatus = 'DRAFT' | 'PUBLISHED'
export interface SeoMetadata {
  id?: number
  siteId: number
  entityType: SeoEntityType
  entityId: number
  locale: string
  seoTitle: string
  metaDescription: string
  focusKeyphrase: string
  relatedKeyphrases: string[]
  canonicalUrl: string
  robotsIndex: boolean
  robotsFollow: boolean
  ogTitle: string
  ogDescription: string
  ogImage: string
  schemaType: string
  publishStatus?: SeoPublishStatus
  version?: number
  publishedTime?: string
  createTime?: string
  updateTime?: string
}
export interface SeoMetadataPageReq {
  pageNo: number
  pageSize: number
  siteId?: number
  entityType?: SeoEntityType
  entityId?: number
  locale?: string
  publishStatus?: SeoPublishStatus
  keyword?: string
}
export const getSeoMetadataPage = (params: SeoMetadataPageReq) =>
  request.get({ url: '/seo/metadata/page', params })
export const getSeoMetadata = (id: number) =>
  request.get({ url: '/seo/metadata/get', params: { id } })
export const createSeoMetadata = (data: SeoMetadata) =>
  request.post({ url: '/seo/metadata/create', data })
export const updateSeoMetadata = (data: SeoMetadata) =>
  request.put({ url: '/seo/metadata/update', data })
export const deleteSeoMetadata = (id: number) =>
  request.delete({ url: '/seo/metadata/delete', params: { id } })
export const publishSeoMetadata = (id: number, version: number) =>
  request.put({ url: '/seo/metadata/publish', params: { id, version } })
```

Match the repository's request wrapper conventions exactly, including params placement and response typing.

- [ ] **Step 4: Implement the site settings page**

The page includes a site selector/input and fields for site name, site URL, title suffix, default description, robots, default OG image and locale. It must:

- load on site ID change;
- validate required site name and absolute site URL;
- display the default robots choices clearly;
- disable Save while submitting;
- refresh from the API after a successful save;
- use `v-hasPermi="['seo:site-config:update']"` on the Save action.

- [ ] **Step 5: Implement the metadata page and edit drawer/dialog**

The list filters by site, content type, entity ID, locale and publication status. Columns include SEO title, content identity, locale, status, version and update time. Actions respect `query/create/update/delete/publish` permissions.

The form contains every save request field, uses select options for the four entity types, manages related keyphrases as string tags, and applies these interactions:

- entity identity fields are disabled on edit;
- `version` is retained from the load response and submitted on update/publish;
- canonical URL validates as blank or absolute HTTP(S);
- publish asks for confirmation;
- published rows show `保存后将影响线上版本` before update;
- version-conflict errors leave the form open and instruct the user to reload.

- [ ] **Step 6: Run contract, type and production-build checks**

Run from `yudao-ui-admin-vue3`:

```powershell
node scripts/check-seo-foundation-contract.mjs
pnpm.cmd ts:check
pnpm.cmd build:local
```

Expected: contract script prints a success line, TypeScript exits `0`, and Vite build completes. Do not run `pnpm install`; do not modify `pnpm-lock.yaml`.

- [ ] **Step 7: Commit UI files without the pre-existing lockfile change**

```powershell
git add -- yudao电商管理平台前后端/yudao-ui-admin-vue3/package.json yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-seo-foundation-contract.mjs yudao电商管理平台前后端/yudao-ui-admin-vue3/src/api/seo yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/seo
git commit -m "feat(seo): add metadata management UI"
```

Before committing, confirm `git diff --cached --name-only` does not contain `pnpm-lock.yaml` or any unrelated file.

---

## Task 6: Verify the complete foundation slice

**Files:**

- Verify all files introduced or modified by Tasks 1–5.
- Do not modify unrelated workspace files during verification.

- [ ] **Step 1: Run the complete SEO backend test suite**

From `yudao-cloud`:

```powershell
mvn -pl yudao-module-seo/yudao-module-seo-server -am test
```

Expected: `BUILD SUCCESS`, with all SEO tests executed rather than skipped.

- [ ] **Step 2: Build the aggregated backend**

```powershell
mvn -pl yudao-server -am -DskipTests package
```

Expected: `BUILD SUCCESS` and successful packaging of `yudao-server` with the SEO module dependency.

- [ ] **Step 3: Run all targeted admin checks**

From `yudao-ui-admin-vue3`:

```powershell
node scripts/check-seo-foundation-contract.mjs
pnpm.cmd ts:check
pnpm.cmd build:local
```

Expected: all three commands exit `0`.

- [ ] **Step 4: Audit migration and API boundaries**

Verify:

```powershell
Select-String -Path sql/mysql/migrations/V015__seo_foundation.sql -Pattern "uk_tenant_site_deleted","uk_entity_locale_deleted","idx_public_resolve"
rg "tenantId" yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller
rg "focusKeyphrase|relatedKeyphrases|tenantId" yudao-module-seo/yudao-module-seo-server/src/main/java/cn/iocoder/yudao/module/seo/controller/app/metadata/vo/SeoPublicMetadataRespVO.java
```

Expected:

- all three database indexes exist;
- request/response controller VOs do not expose a writable `tenantId`;
- the public response contains none of the private analysis or tenant fields.

- [ ] **Step 5: Inspect scope before the final foundation commit**

```powershell
git status --short
git diff --check
git log --oneline -6
```

Expected: no whitespace errors; unrelated user files remain untouched and uncommitted; recent commits correspond to the task slices above.

- [ ] **Step 6: Record the foundation delivery state**

If verification required a scoped correction, list the corrected SEO files with `git status --short`, stage each explicit path with `git add -- path/to/file`, verify the staged list with `git diff --cached --name-only`, and commit them with `git commit -m "fix(seo): complete foundation verification"`.

If no correction was required, do not create an empty commit.

---

## Acceptance Checklist

- [ ] `yudao-server` loads the independent SEO module.
- [ ] A tenant can create or update exactly one site configuration per site ID.
- [ ] Admin users can create, edit, list, delete and publish metadata for all four entity types.
- [ ] Duplicate entity/locale records and stale concurrent writes are rejected explicitly.
- [ ] Public resolution returns only exact-locale, published metadata in the current tenant.
- [ ] The public DTO does not expose keyphrases, audit details, tenant ID or draft fields.
- [ ] Admin navigation and buttons are protected by the seven defined permissions.
- [ ] Backend module tests, aggregate package, UI contract test, type check and local build all pass.
- [ ] Existing unrelated workspace changes, especially `pnpm-lock.yaml`, remain uncommitted.

## Follow-on Plan Boundaries

After this plan passes, continue in this order with separate implementation plans:

1. `2026-07-22-yudao-seo-keyword-relevance-analysis-implementation.md`: per-keyword percentage scoring, deterministic Yoast-level rules, evidence, recommendations, analysis history and the unified-color result UI. Generative AI polishing is explicitly excluded.
2. `2026-07-15-yudao-seo-document-analysis-implementation.md`: DOCX/PDF/XLSX extraction and conversion into the keyword plan's shared immutable content snapshot; it must reuse the same analysis engine instead of creating a second scoring path.
3. `2026-07-15-yudao-seo-technical-controls-implementation.md`: Sitemap generation, robots editor, redirect rules, validation and caches.
4. `2026-07-15-yudao-seo-storefront-rendering-implementation.md`: product/category entity adapters, locale fallback, canonical policy, Schema.org JSON-LD, and initial-HTML Head rendering in `furniture web`.

Those plans must consume the contracts delivered here rather than reintroducing SEO configuration or metadata tables.
