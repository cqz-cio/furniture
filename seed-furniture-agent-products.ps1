$ErrorActionPreference = "Stop"

$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$javac = (Get-Command javac.exe -ErrorAction Stop).Source
$java = (Get-Command java.exe -ErrorAction Stop).Source
$driver = Join-Path $env:USERPROFILE ".m2\repository\com\mysql\mysql-connector-j\8.0.33\mysql-connector-j-8.0.33.jar"
if (-not (Test-Path -LiteralPath $driver)) {
    throw "MySQL JDBC driver not found at $driver"
}
$tmpDir = Join-Path $workspace ".tmp"
$javaFile = Join-Path $tmpDir "SeedFurnitureAgentProducts.java"

if (-not (Test-Path -LiteralPath $tmpDir)) {
    New-Item -ItemType Directory -Path $tmpDir | Out-Null
}

$source = @'
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class SeedFurnitureAgentProducts {

    private static final long TENANT_ID = 121L;
    private static final String IMAGE = "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=1200&q=80";
    private static final String CREAM_FABRIC_SOFA_IMAGE = "https://images.unsplash.com/photo-1768144092684-c1a5dd6c7aad?auto=format&fit=crop&w=1200&q=80";
    private static final String IVORY_PERFORMANCE_SOFA_IMAGE = "https://images.unsplash.com/photo-1493663284031-b7e3aefcae8e?auto=format&fit=crop&w=1200&q=80";
    private static final String CLOUD_MODULAR_SOFA_IMAGE = "https://images.unsplash.com/photo-1618221195710-dd6b41faaea6?auto=format&fit=crop&w=1200&q=80";
    private static final String LEATHER_LOUNGE_SOFA_IMAGE = "https://images.unsplash.com/photo-1678225179685-b8ad3b6d7249?auto=format&fit=crop&w=1200&q=80";
    private static final String COMPACT_SOFA_IMAGE = "https://images.unsplash.com/photo-1631679706909-1844bbd07221?auto=format&fit=crop&w=1200&q=80";
    private static final String BROWN_LEATHER_SOFA_IMAGE = "https://images.unsplash.com/photo-1616593871468-2a9452218369?auto=format&fit=crop&w=1200&q=80";
    private static final String DINING_TABLE_IMAGE = "https://images.unsplash.com/photo-1730630906214-1256b57d65b7?auto=format&fit=crop&w=1200&q=80";
    private static final String UPHOLSTERED_BED_IMAGE = "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80";
    private static final String PENDANT_LIGHT_IMAGE = "https://images.unsplash.com/photo-1721523262897-49620a979091?auto=format&fit=crop&w=1200&q=80";
    private static final String MEDIA_CABINET_IMAGE = "https://images.unsplash.com/photo-1646861039459-fd9e3aabf3fb?auto=format&fit=crop&w=1200&q=80";
    private static final String WOOD_DINING_SET_IMAGE = "https://images.unsplash.com/photo-1758977403438-1b8546560d31?auto=format&fit=crop&w=1200&q=80";
    private static final String BLACK_DINING_SET_IMAGE = "https://images.unsplash.com/photo-1729606312336-63d202c090b1?auto=format&fit=crop&w=1200&q=80";
    private static final String DINING_CHAIR_IMAGE = "https://images.unsplash.com/photo-1503602642458-232111445657?auto=format&fit=crop&w=1200&q=80";
    private static final String BLACK_DINING_CHAIR_IMAGE = "https://images.unsplash.com/photo-1592078615290-033ee584e267?auto=format&fit=crop&w=1200&q=80";
    private static final String COFFEE_TABLE_IMAGE = "https://images.unsplash.com/photo-1609766857120-0183863c7971?auto=format&fit=crop&w=1200&q=80";
    private static final String WOOD_COFFEE_TABLE_IMAGE = "https://images.unsplash.com/photo-1605774337664-7a846e9cdf17?auto=format&fit=crop&w=1200&q=80";
    private static final String SIDE_TABLE_IMAGE = "https://images.unsplash.com/photo-1565374369705-acde12f3caa2?auto=format&fit=crop&w=1200&q=80";
    private static final String WALNUT_DESK_IMAGE = "https://images.unsplash.com/photo-1575318633968-0383e7d07ca0?auto=format&fit=crop&w=1200&q=80";
    private static final String TABLE_LAMP_NIGHTSTAND_IMAGE = "https://images.unsplash.com/photo-1565374235393-6fe32a07cc86?auto=format&fit=crop&w=1200&q=80";
    private static final String WHITE_TABLE_LAMP_IMAGE = "https://images.unsplash.com/photo-1753932847231-7949af383b98?auto=format&fit=crop&w=1200&q=80";
    private static final String BLACK_FLOOR_LAMP_IMAGE = "https://images.unsplash.com/photo-1494438639946-1ebd1d20bf85?auto=format&fit=crop&w=1200&q=80";
    private static final String BEIGE_RUG_TEXTURE_IMAGE = "https://images.unsplash.com/photo-1520762042279-ae3264026bd2?auto=format&fit=crop&w=1200&q=80";
    private static final String GRAY_RUG_TEXTURE_IMAGE = "https://images.unsplash.com/photo-1618220252344-8ec99ec624b1?auto=format&fit=crop&w=1200&q=80";
    private static final String WALNUT_DRESSER_IMAGE = "https://images.unsplash.com/photo-1579283111509-855c7eea1c49?auto=format&fit=crop&w=1200&q=80";
    private static final String WARDROBE_IMAGE = "https://images.unsplash.com/photo-1778731660303-1fa5ede75477?auto=format&fit=crop&w=1200&q=80";
    private static final String SIDEBOARD_IMAGE = "https://images.unsplash.com/photo-1713810958247-01dbd76b4a61?auto=format&fit=crop&w=1200&q=80";

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        try (Connection connection = DriverManager.getConnection(url, "root", "123456")) {
            connection.setAutoCommit(false);
            try {
                extendTenantExpiry(connection);
                long brandId = ensureBrand(connection);
                long rootCategoryId = ensureCategory(connection, 0L, "Furniture Agent Demo", 10);
                long sofaCategoryId = ensureCategory(connection, rootCategoryId, "Sofas", 20);
                long diningCategoryId = ensureCategory(connection, rootCategoryId, "Dining Tables", 30);
                long diningChairCategoryId = ensureCategory(connection, rootCategoryId, "Dining Chairs", 35);
                long coffeeTableCategoryId = ensureCategory(connection, rootCategoryId, "Coffee Tables", 36);
                long bedroomCategoryId = ensureCategory(connection, rootCategoryId, "Beds", 40);
                long officeCategoryId = ensureCategory(connection, rootCategoryId, "Desks", 45);
                long rugCategoryId = ensureCategory(connection, rootCategoryId, "Rugs", 46);
                long bedroomStorageCategoryId = ensureCategory(connection, rootCategoryId, "Bedroom Storage", 47);
                long wardrobeCategoryId = ensureCategory(connection, rootCategoryId, "Wardrobes", 48);
                long sideTableCategoryId = ensureCategory(connection, rootCategoryId, "Side Tables", 49);
                long lightingCategoryId = ensureCategory(connection, rootCategoryId, "Lighting", 50);
                long storageCategoryId = ensureCategory(connection, rootCategoryId, "Media Storage", 60);

                ensureProduct(connection, 0, brandId, sofaCategoryId, "Cream Fabric Sofa", "sofa cream performance fabric living room", "Deep cream performance-fabric sofa with relaxed cushions for everyday living rooms.", CREAM_FABRIC_SOFA_IMAGE, 699900, 899900, 420000, 18);
                ensureProduct(connection, 1, brandId, sofaCategoryId, "Cloud Modular Sofa", "sofa cloud modular linen living room", "Low, deep modular sofa with down-blend cushions for flexible living-room layouts.", CLOUD_MODULAR_SOFA_IMAGE, 329900, 459900, 220000, 18);
                ensureProduct(connection, 2, brandId, sofaCategoryId, "Leather Lounge Sofa", "sofa leather lounge brown premium", "Full-grain brown leather lounge sofa with a supportive frame and generous seat depth.", LEATHER_LOUNGE_SOFA_IMAGE, 1299900, 1599900, 780000, 8);
                ensureProduct(connection, 3, brandId, sofaCategoryId, "Ivory Performance Sofa", "sofa ivory performance fabric apartment", "Tailored ivory sofa in easy-care performance fabric for bright apartments and family rooms.", IVORY_PERFORMANCE_SOFA_IMAGE, 749900, 929900, 450000, 13);
                ensureProduct(connection, 4, brandId, sofaCategoryId, "Compact Linen Sofa", "sofa compact linen small apartment", "Compact linen-blend sofa sized for apartments, studios, and smaller sitting rooms.", COMPACT_SOFA_IMAGE, 329900, 459900, 220000, 18);
                ensureProduct(connection, 5, brandId, sofaCategoryId, "Brown Leather Club Sofa", "sofa brown leather club study", "Warm brown leather club sofa suited to studies, libraries, and refined lounge spaces.", BROWN_LEATHER_SOFA_IMAGE, 1299900, 1599900, 780000, 8);
                ensureProduct(connection, 6, brandId, diningCategoryId, "Natural Oak Dining Table", "dining table natural oak six seat", "Six-seat natural oak dining table with a clean profile and visible wood grain.", DINING_TABLE_IMAGE, 459900, 599900, 260000, 12);
                ensureProduct(connection, 7, brandId, bedroomCategoryId, "Upholstered Shelter Bed", "bed upholstered shelter queen bedroom", "Queen shelter bed with a softly upholstered headboard and calm neutral finish.", UPHOLSTERED_BED_IMAGE, 529900, 699900, 310000, 10);
                ensureProduct(connection, 8, brandId, lightingCategoryId, "Brass Drum Pendant", "lighting brass drum pendant dining", "Warm brass pendant with a linen drum shade for dining rooms and kitchen islands.", PENDANT_LIGHT_IMAGE, 189900, 259900, 90000, 25);
                ensureProduct(connection, 9, brandId, storageCategoryId, "Fluted Oak Media Console", "media console fluted oak living room", "Low fluted-oak media console with concealed storage and cable management.", MEDIA_CABINET_IMAGE, 359900, 499900, 190000, 14);
                ensureProduct(connection, 10, brandId, diningCategoryId, "Black Round Dining Table", "dining table black round four seat", "Sculptural black round dining table sized for four-seat dining areas.", BLACK_DINING_SET_IMAGE, 569900, 699900, 320000, 9);
                ensureProduct(connection, 11, brandId, diningCategoryId, "Reclaimed Wood Dining Table", "dining table reclaimed wood eight seat", "Long reclaimed-wood dining table for six to eight guests with a warm natural character.", WOOD_DINING_SET_IMAGE, 599900, 759900, 350000, 7);
                ensureProduct(connection, 12, brandId, diningChairCategoryId, "Grey Upholstered Dining Chair", "dining chair grey upholstered wood legs", "Grey upholstered dining chair with supportive padding and solid wood legs.", DINING_CHAIR_IMAGE, 89900, 129900, 42000, 32);
                ensureProduct(connection, 13, brandId, diningChairCategoryId, "Black Spindle Dining Chair", "dining chair black spindle modern", "Black spindle-back dining chair for modern tables and compact breakfast spaces.", BLACK_DINING_CHAIR_IMAGE, 129900, 179900, 58000, 24);
                ensureProduct(connection, 14, brandId, coffeeTableCategoryId, "Smoked Glass Coffee Table", "coffee table smoked glass modern", "Smoked-glass coffee table with a dark architectural base for contemporary living rooms.", COFFEE_TABLE_IMAGE, 249900, 329900, 125000, 15);
                ensureProduct(connection, 15, brandId, coffeeTableCategoryId, "Natural Oak Coffee Table", "coffee table natural oak living room", "Natural oak coffee table with a clean silhouette and expressive wood grain.", WOOD_COFFEE_TABLE_IMAGE, 219900, 299900, 110000, 18);
                ensureProduct(connection, 16, brandId, sideTableCategoryId, "Walnut Drum Side Table", "side table walnut drum sofa bedside", "Compact walnut drum table for sofa sides, bedsides, and reading corners.", SIDE_TABLE_IMAGE, 129900, 169900, 62000, 20);
                ensureProduct(connection, 17, brandId, officeCategoryId, "Walnut Writing Desk", "desk walnut writing home office", "Walnut writing desk with a generous work surface for home offices and studies.", WALNUT_DESK_IMAGE, 399900, 529900, 210000, 11);
                ensureProduct(connection, 18, brandId, rugCategoryId, "Handwoven Beige Wool Rug", "rug beige wool handwoven living room", "Handwoven beige wool rug that adds soft texture to neutral living rooms.", BEIGE_RUG_TEXTURE_IMAGE, 189900, 259900, 90000, 16);
                ensureProduct(connection, 19, brandId, rugCategoryId, "Textured Grey Area Rug", "rug grey textured area living room", "Low-pile grey area rug with subtle texture for living and dining spaces.", GRAY_RUG_TEXTURE_IMAGE, 159900, 219900, 78000, 19);
                ensureProduct(connection, 20, brandId, bedroomStorageCategoryId, "Oak Two-Drawer Nightstand", "nightstand oak two drawer bedroom", "Oak two-drawer nightstand with practical closed storage and a compact footprint.", TABLE_LAMP_NIGHTSTAND_IMAGE, 149900, 199900, 72000, 18);
                ensureProduct(connection, 21, brandId, bedroomStorageCategoryId, "Walnut Six-Drawer Dresser", "dresser walnut six drawer bedroom", "Walnut six-drawer dresser offering balanced bedroom storage and warm wood tone.", WALNUT_DRESSER_IMAGE, 299900, 399900, 150000, 10);
                ensureProduct(connection, 22, brandId, wardrobeCategoryId, "Natural Oak Wardrobe", "wardrobe natural oak bedroom storage", "Natural oak wardrobe with hanging space, shelves, and lower drawer storage.", WARDROBE_IMAGE, 699900, 899900, 380000, 6);
                ensureProduct(connection, 23, brandId, lightingCategoryId, "Opal Glass Table Lamp", "table lamp opal glass bedside", "Opal glass table lamp that provides soft ambient light on nightstands and consoles.", WHITE_TABLE_LAMP_IMAGE, 89900, 129900, 36000, 26);
                ensureProduct(connection, 24, brandId, lightingCategoryId, "Black Arc Floor Lamp", "floor lamp black arc reading", "Black arc floor lamp with focused illumination for sofa-side reading areas.", BLACK_FLOOR_LAMP_IMAGE, 169900, 229900, 82000, 21);
                ensureProduct(connection, 25, brandId, storageCategoryId, "Walnut Four-Door Sideboard", "sideboard walnut four door dining storage", "Walnut four-door sideboard for tableware storage and living-room display.", SIDEBOARD_IMAGE, 329900, 449900, 170000, 12);

                auditCatalog(connection);
                connection.commit();
                printCount(connection);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private static void extendTenantExpiry(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "update system_tenant set expire_time='2099-12-31 23:59:59', updater='furniture-agent-seed', update_time=current_timestamp where id=? and deleted=b'0'")) {
            ps.setLong(1, TENANT_ID);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("Tenant 121 was not found.");
            }
        }
    }

    private static long ensureBrand(Connection connection) throws Exception {
        Long existing = findId(connection, "select id from product_brand where tenant_id=? and name=? and deleted=b'0'",
                TENANT_ID, "Trendz Demo");
        if (existing != null) {
            return existing;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into product_brand(name, pic_url, sort, description, status, creator, updater, tenant_id) values(?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Trendz Demo");
            ps.setString(2, IMAGE);
            ps.setInt(3, 10);
            ps.setString(4, "Demo brand for furniture assistant testing");
            ps.setInt(5, 0);
            ps.setString(6, "furniture-agent-seed");
            ps.setString(7, "furniture-agent-seed");
            ps.setLong(8, TENANT_ID);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    private static long ensureCategory(Connection connection, long parentId, String name, int sort) throws Exception {
        Long existing = findId(connection, "select id from product_category where tenant_id=? and parent_id=? and name=? and deleted=b'0'",
                TENANT_ID, parentId, name);
        if (existing != null) {
            return existing;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into product_category(parent_id, name, pic_url, big_pic_url, sort, status, creator, updater, tenant_id) values(?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, parentId);
            ps.setString(2, name);
            ps.setString(3, IMAGE);
            ps.setString(4, IMAGE);
            ps.setInt(5, sort);
            ps.setInt(6, 0);
            ps.setString(7, "furniture-agent-seed");
            ps.setString(8, "furniture-agent-seed");
            ps.setLong(9, TENANT_ID);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    private static void ensureProduct(Connection connection, int position, long brandId, long categoryId, String name,
                                      String keyword, String introduction, String image, int price, int marketPrice,
                                      int costPrice, int stock) throws Exception {
        Long existing = findCatalogProductId(connection, position);
        if (existing != null) {
            updateProduct(connection, existing, brandId, categoryId, name, keyword, introduction, image, price, marketPrice, costPrice, stock);
            ensureSku(connection, existing, image, price, marketPrice, costPrice, stock);
            return;
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "insert into product_spu(name, keyword, introduction, description, category_id, brand_id, pic_url, slider_pic_urls, unit, sort, status, spec_type, price, market_price, cost_price, stock, delivery_types, delivery_template_id, recommend_hot, recommend_benefit, recommend_best, recommend_new, recommend_good, give_integral, sub_commission_type, sales_count, virtual_sales_count, browse_count, creator, updater, tenant_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            bindProduct(ps, brandId, categoryId, name, keyword, introduction, image, price, marketPrice, costPrice, stock);
            ps.executeUpdate();
            long spuId = generatedId(ps);
            ensureSku(connection, spuId, image, price, marketPrice, costPrice, stock);
        }
    }

    private static Long findCatalogProductId(Connection connection, int position) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "select id from product_spu where tenant_id=? and creator='furniture-agent-seed' and deleted=b'0' order by id limit ?,1")) {
            ps.setLong(1, TENANT_ID);
            ps.setInt(2, position);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static void updateProduct(Connection connection, long id, long brandId, long categoryId, String name,
                                      String keyword, String introduction, String image, int price, int marketPrice,
                                      int costPrice, int stock) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "update product_spu set name=?, keyword=?, introduction=?, description=?, category_id=?, brand_id=?, pic_url=?, slider_pic_urls=?, unit=?, sort=?, status=?, spec_type=?, price=?, market_price=?, cost_price=?, stock=?, delivery_types=?, delivery_template_id=?, recommend_hot=?, recommend_benefit=?, recommend_best=?, recommend_new=?, recommend_good=?, give_integral=?, sub_commission_type=?, sales_count=?, virtual_sales_count=?, browse_count=?, updater=?, update_time=current_timestamp where id=? and tenant_id=?")) {
            bindProduct(ps, brandId, categoryId, name, keyword, introduction, image, price, marketPrice, costPrice, stock);
            ps.setLong(30, id);
            ps.setLong(31, TENANT_ID);
            ps.executeUpdate();
        }
    }

    private static void bindProduct(PreparedStatement ps, long brandId, long categoryId, String name,
                                    String keyword, String introduction, String image, int price, int marketPrice,
                                    int costPrice, int stock) throws Exception {
        ps.setString(1, name);
        ps.setString(2, keyword);
        ps.setString(3, introduction);
        ps.setString(4, "<p>" + introduction + "</p>");
        ps.setLong(5, categoryId);
        ps.setLong(6, brandId);
        ps.setString(7, image);
        String galleryImage = image.replace("w=1200", "w=1600");
        ps.setString(8, "[\"" + image + "\",\"" + galleryImage + "\"]");
        ps.setInt(9, 1);
        ps.setInt(10, 100);
        ps.setInt(11, 1);
        ps.setBoolean(12, false);
        ps.setInt(13, price);
        ps.setInt(14, marketPrice);
        ps.setInt(15, costPrice);
        ps.setInt(16, stock);
        ps.setString(17, "1");
        ps.setLong(18, 0L);
        ps.setBoolean(19, true);
        ps.setBoolean(20, false);
        ps.setBoolean(21, true);
        ps.setBoolean(22, true);
        ps.setBoolean(23, true);
        ps.setInt(24, 0);
        ps.setBoolean(25, false);
        ps.setInt(26, 0);
        ps.setInt(27, 0);
        ps.setInt(28, 0);
        ps.setString(29, "furniture-agent-seed");
        if (ps.getParameterMetaData().getParameterCount() == 31) {
            ps.setString(30, "furniture-agent-seed");
            ps.setLong(31, TENANT_ID);
        }
    }

    private static void ensureSku(Connection connection, long spuId, String image, int price, int marketPrice,
                                  int costPrice, int stock) throws Exception {
        Long existing = findId(connection, "select id from product_sku where tenant_id=? and spu_id=? and deleted=b'0'",
                TENANT_ID, spuId);
        if (existing != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "update product_sku set price=?, market_price=?, cost_price=?, pic_url=?, stock=?, updater=?, update_time=current_timestamp where id=? and tenant_id=?")) {
                ps.setInt(1, price);
                ps.setInt(2, marketPrice);
                ps.setInt(3, costPrice);
                ps.setString(4, image);
                ps.setInt(5, stock);
                ps.setString(6, "furniture-agent-seed");
                ps.setLong(7, existing);
                ps.setLong(8, TENANT_ID);
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into product_sku(spu_id, properties, price, market_price, cost_price, bar_code, pic_url, stock, weight, volume, sales_count, creator, updater, tenant_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, spuId);
            ps.setString(2, "[]");
            ps.setInt(3, price);
            ps.setInt(4, marketPrice);
            ps.setInt(5, costPrice);
            ps.setString(6, "FA-" + spuId);
            ps.setString(7, image);
            ps.setInt(8, stock);
            ps.setDouble(9, 35.0D);
            ps.setDouble(10, 1.6D);
            ps.setInt(11, 0);
            ps.setString(12, "furniture-agent-seed");
            ps.setString(13, "furniture-agent-seed");
            ps.setLong(14, TENANT_ID);
            ps.executeUpdate();
        }
    }

    private static Long findId(Connection connection, String sql, Object... args) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof Long) {
                    ps.setLong(i + 1, (Long) arg);
                } else {
                    ps.setString(i + 1, String.valueOf(arg));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static long generatedId(PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (!rs.next()) {
                throw new IllegalStateException("No generated id returned.");
            }
            return rs.getLong(1);
        }
    }

    private static void auditCatalog(Connection connection) throws Exception {
        assertCount(connection, "active_products", 26,
                "select count(*) from product_spu where tenant_id=121 and creator='furniture-agent-seed' and status=1 and deleted=b'0'");
        assertCount(connection, "distinct_covers", 26,
                "select count(distinct pic_url) from product_spu where tenant_id=121 and creator='furniture-agent-seed' and status=1 and deleted=b'0'");
        assertZero(connection, "invalid_product_fields",
                "select count(*) from product_spu where tenant_id=121 and creator='furniture-agent-seed' and status=1 and deleted=b'0' and (name='' or name like '%?%' or pic_url not like 'https://%' or json_length(slider_pic_urls)<2 or cost_price>=price or price>=market_price or stock<0)");
        assertZero(connection, "tenant_mismatch",
                "select count(*) from product_spu p left join product_category c on c.id=p.category_id and c.deleted=b'0' left join product_brand b on b.id=p.brand_id and b.deleted=b'0' where p.tenant_id=121 and p.creator='furniture-agent-seed' and p.deleted=b'0' and (c.tenant_id<>121 or b.tenant_id<>121 or c.id is null or b.id is null)");
        assertZero(connection, "sku_mismatch",
                "select count(*) from product_spu p left join product_sku s on s.spu_id=p.id and s.tenant_id=p.tenant_id and s.deleted=b'0' where p.tenant_id=121 and p.creator='furniture-agent-seed' and p.deleted=b'0' and (s.id is null or s.pic_url<>p.pic_url or s.price<>p.price or s.market_price<>p.market_price or s.cost_price<>p.cost_price or s.stock<>p.stock)");
    }

    private static void assertZero(Connection connection, String label, String sql) throws Exception {
        assertCount(connection, label, 0, sql);
    }

    private static void assertCount(Connection connection, String label, int expected, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            int actual = rs.getInt(1);
            System.out.println("audit " + label + "=" + actual);
            if (actual != expected) {
                throw new IllegalStateException(label + " expected " + expected + " but was " + actual);
            }
        }
    }

    private static void printCount(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "select id, name, price, stock from product_spu where tenant_id=? and creator='furniture-agent-seed' and status=1 and deleted=b'0' order by price asc")) {
            ps.setLong(1, TENANT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("seeded spu id=" + rs.getLong("id")
                            + " name=" + rs.getString("name")
                            + " priceFen=" + rs.getInt("price")
                            + " stock=" + rs.getInt("stock"));
                }
            }
        }
    }
}
'@

[System.IO.File]::WriteAllText($javaFile, $source, (New-Object System.Text.UTF8Encoding($false)))
& $javac -encoding UTF-8 -cp $driver $javaFile
if ($LASTEXITCODE -ne 0) {
    throw "Failed to compile seed helper."
}
& $java -cp "$tmpDir;$driver" SeedFurnitureAgentProducts
if ($LASTEXITCODE -ne 0) {
    throw "Failed to run seed helper."
}
