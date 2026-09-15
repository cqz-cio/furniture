package cn.iocoder.yudao.module.system.framework.navigation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FurnitureCmsNavigationTest {
    @Test
    void mediaNavigationFollowsItsOwnGrantWithoutExpandingFurniturePackages() throws Exception {
        FurnitureNavigationCatalog catalog = new FurnitureNavigationConfiguration()
                .furnitureNavigationCatalog(new ObjectMapper());
        assertTrue(catalog.getMenuPaths().contains("/seo/media"));
        for (String mode : new String[]{"B2B", "B2C"}) {
            assertFalse(catalog.getMenuPaths(mode).contains("/seo/media"));
            assertFalse(catalog.getMenuPaths(mode, true, false).contains("/seo/media"));
            assertTrue(catalog.getMenuPaths(mode, false, true).contains("/seo/media"));
            assertTrue(catalog.getMenuPaths(mode, true, true).contains("/seo/media"));
            assertFalse(catalog.getMenuPaths(mode).contains("/seo/media"));
        }
    }

    @Test
    void tripeerBrandOperatorCanNavigateEveryGrantedCmsModule() throws Exception {
        FurnitureNavigationCatalog catalog = new FurnitureNavigationConfiguration()
                .furnitureNavigationCatalog(new ObjectMapper());
        for (String page : new String[]{"page-content", "site-config", "navigation", "blog",
                "metadata", "analysis", "website-code"}) {
            assertTrue(catalog.getMenuPaths("B2B", true).contains("/seo/" + page),
                    "TRIPEER CMS menu must survive the business-mode navigation filter: " + page);
        }
        assertFalse(catalog.getMenuPaths("B2B", false).contains("/seo/page-content"));
        assertFalse(catalog.getMenuPaths("B2B").contains("/seo/page-content"),
                "Do not grant TRIPEER page access through furniture package synchronization");
    }

    @Test
    void cmsEntryRequiresExistingRoleGrantAndDoesNotExpandFurniturePackages() throws Exception {
        FurnitureNavigationCatalog catalog = new FurnitureNavigationConfiguration()
                .furnitureNavigationCatalog(new ObjectMapper());
        String page = "/seo/page-content";
        assertTrue(catalog.getMenuPaths().contains(page), "Platform administrator must see the CMS entry");
        for (String mode : new String[]{"B2B", "B2C"}) {
            assertFalse(catalog.getMenuPaths(mode).contains(page), "Package synchronization must not grant CMS access");
            assertFalse(catalog.getMenuPaths(mode, false).contains(page), "An ungranted operator must not get the CMS entry");
            assertTrue(catalog.getMenuPaths(mode, true).contains(page), "Existing CMS role must retain its entry");
            assertFalse(catalog.getMenuPaths(mode).contains(page), "Resolving CMS navigation must not mutate package scope");
        }
    }
}
