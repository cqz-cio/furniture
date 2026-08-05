package cn.iocoder.yudao.module.seo.enums.navigation;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum WebsiteNavigationTargetEnum {

    ROUTE_HOME("ROUTE_HOME", "ROUTE", "Home", "/"),
    ROUTE_PRODUCTS("ROUTE_PRODUCTS", "ROUTE", "All products", "/products"),
    ROUTE_CATALOG("ROUTE_CATALOG", "ROUTE", "OAKVED catalog", "/catalog"),
    ROUTE_SALE("ROUTE_SALE", "ROUTE", "Sale", "/sale"),

    FILTER_NEW("FILTER_NEW", "FILTER", "New arrivals", "/products?tag=new"),
    FILTER_COLLECTIONS_ALL("FILTER_COLLECTIONS_ALL", "FILTER", "All collections", "/products?collection=all"),
    FILTER_ROOM_BEDROOM("FILTER_ROOM_BEDROOM", "FILTER", "Bedroom", "/products?room=bedroom"),
    FILTER_ROOM_LIVING("FILTER_ROOM_LIVING", "FILTER", "Living", "/products?room=living"),
    FILTER_ROOM_DINING("FILTER_ROOM_DINING", "FILTER", "Dining", "/products?room=dining"),
    FILTER_COLLECTION_BESPOKE("FILTER_COLLECTION_BESPOKE", "FILTER", "Bespoke", "/products?collection=bespoke"),
    FILTER_CATEGORY_DECOR("FILTER_CATEGORY_DECOR", "FILTER", "Decor", "/products?category=decor"),
    FILTER_COLLECTION_SOLSTICE("FILTER_COLLECTION_SOLSTICE", "FILTER", "The Solstice", "/products?collection=solstice"),
    FILTER_COLLECTION_HALCYON("FILTER_COLLECTION_HALCYON", "FILTER", "Halcyon", "/products?collection=halcyon"),
    FILTER_COLLECTION_KINDRED("FILTER_COLLECTION_KINDRED", "FILTER", "Kindred", "/products?collection=kindred"),
    FILTER_CATEGORY_BED("FILTER_CATEGORY_BED", "FILTER", "Beds", "/products?category=bed"),
    FILTER_CATEGORY_HEADBOARD("FILTER_CATEGORY_HEADBOARD", "FILTER", "Headboard", "/products?category=headboard"),
    FILTER_CATEGORY_NIGHTSTAND("FILTER_CATEGORY_NIGHTSTAND", "FILTER", "Nightstands", "/products?category=nightstand"),
    FILTER_CATEGORY_BENCH("FILTER_CATEGORY_BENCH", "FILTER", "Benches", "/products?category=bench"),
    FILTER_CATEGORY_DRESSER("FILTER_CATEGORY_DRESSER", "FILTER", "Dressers", "/products?category=dresser"),
    FILTER_CATEGORY_CHAIR("FILTER_CATEGORY_CHAIR", "FILTER", "Chairs", "/products?category=chair"),
    FILTER_CATEGORY_SIDE_TABLE("FILTER_CATEGORY_SIDE_TABLE", "FILTER", "Side tables", "/products?category=side-table"),
    FILTER_GROUP_FABRIC_CARE("FILTER_GROUP_FABRIC_CARE", "FILTER", "Fabric care", "/products?group=fabric-care"),
    FILTER_GROUP_MATERIALS_CRAFTSMANSHIP("FILTER_GROUP_MATERIALS_CRAFTSMANSHIP", "FILTER", "Materials & craftsmanship", "/products?group=materials-craftsmanship"),
    FILTER_CATEGORY_SOFA("FILTER_CATEGORY_SOFA", "FILTER", "Sofas", "/products?category=sofa"),
    FILTER_CATEGORY_TABLE("FILTER_CATEGORY_TABLE", "FILTER", "Tables", "/products?category=table"),
    FILTER_CATEGORY_CONSOLE("FILTER_CATEGORY_CONSOLE", "FILTER", "Consoles", "/products?category=console"),
    FILTER_CATEGORY_SIDEBOARD("FILTER_CATEGORY_SIDEBOARD", "FILTER", "Sideboards", "/products?category=sideboard"),
    FILTER_CATEGORY_CABINET("FILTER_CATEGORY_CABINET", "FILTER", "Cabinets", "/products?category=cabinet"),
    FILTER_CATEGORY_STOOL("FILTER_CATEGORY_STOOL", "FILTER", "Stools", "/products?category=stool"),
    FILTER_CATEGORY_RECTANGULAR_TABLE("FILTER_CATEGORY_RECTANGULAR_TABLE", "FILTER", "Rectangular tables", "/products?category=rectangular-table"),
    FILTER_CATEGORY_ROUND_OVAL_TABLE("FILTER_CATEGORY_ROUND_OVAL_TABLE", "FILTER", "Round & oval tables", "/products?category=round-oval-table"),
    FILTER_CATEGORY_BISTRO_TABLE("FILTER_CATEGORY_BISTRO_TABLE", "FILTER", "Bistro tables", "/products?category=bistro-table"),
    FILTER_CATEGORY_FABRIC_CHAIR("FILTER_CATEGORY_FABRIC_CHAIR", "FILTER", "Fabric chairs", "/products?category=fabric-chair"),
    FILTER_CATEGORY_WOOD_WOVEN_CHAIR("FILTER_CATEGORY_WOOD_WOVEN_CHAIR", "FILTER", "Wood & woven chairs", "/products?category=wood-woven-chair"),
    FILTER_CATEGORY_BAR_COUNTER_STOOL("FILTER_CATEGORY_BAR_COUNTER_STOOL", "FILTER", "Bar & counter stools", "/products?category=bar-counter-stool"),
    FILTER_GROUP_UPHOLSTERY_SWATCHES("FILTER_GROUP_UPHOLSTERY_SWATCHES", "FILTER", "Upholstery swatches", "/products?group=upholstery-swatches");

    private final String code;
    private final String itemType;
    private final String label;
    private final String href;

    public static WebsiteNavigationTargetEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElse(null);
    }

}
