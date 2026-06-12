$ErrorActionPreference = "Stop"

$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$javaHome = "D:\code\tools\jdk8\jdk1.8.0_492"
$driver = "C:\Users\admin\.m2\repository\com\mysql\mysql-connector-j\8.0.33\mysql-connector-j-8.0.33.jar"
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

                ensureProduct(connection, brandId, sofaCategoryId,
                        "Cream Fabric Sofa",
                        "sofa cream fabric living room under 8000",
                        "Cream performance fabric sofa with deep cushions for living rooms and budgets under 8000.",
                        CREAM_FABRIC_SOFA_IMAGE, 699900, 899900, 420000, 18);
                ensureProduct(connection, brandId, sofaCategoryId,
                        "Cloud Modular Sofa",
                        "sofa modular cloud fabric living room",
                        "Low, deep modular sofa with down-blend cushions for flexible living room layouts.",
                        CLOUD_MODULAR_SOFA_IMAGE, 329900, 459900, 220000, 18);
                ensureProduct(connection, brandId, sofaCategoryId,
                        "Leather Lounge Sofa",
                        "sofa leather lounge living room",
                        "Warm brown leather sofa for premium living rooms, studies, and lounge seating.",
                        LEATHER_LOUNGE_SOFA_IMAGE, 1299900, 1599900, 780000, 8);
                ensureProduct(connection, brandId, sofaCategoryId,
                        "\u7c73\u767d\u5e03\u827a\u6c99\u53d1 Sofa",
                        "sofa \u7c73\u767d\u5e03\u827a\u6c99\u53d1 cream fabric living room under 8000",
                        "\u7c73\u767d\u8272\u4eb2\u80a4\u5e03\u827a\uff0c\u9002\u5408\u5ba2\u5385\u548c\u5c0f\u6237\u578b\uff0c\u9884\u7b97 8000 \u5143\u5185\u53ef\u63a8\u8350\u3002",
                        CREAM_FABRIC_SOFA_IMAGE, 699900, 899900, 420000, 18);
                ensureProduct(connection, brandId, sofaCategoryId,
                        "\u5c0f\u6237\u578b\u6c99\u53d1 Sofa",
                        "sofa \u5c0f\u6237\u578b\u6c99\u53d1 compact apartment small living room under 5000",
                        "\u7d27\u51d1\u5c3a\u5bf8\uff0c\u9002\u5408\u516c\u5bd3\u3001\u5c0f\u5ba2\u5385\u548c\u9884\u7b97\u7b5b\u9009\u573a\u666f\u3002",
                        COMPACT_SOFA_IMAGE, 329900, 459900, 220000, 18);
                ensureProduct(connection, brandId, sofaCategoryId,
                        "\u76ae\u6c99\u53d1 Sofa",
                        "sofa \u76ae\u6c99\u53d1 leather lounge living room premium",
                        "\u6696\u68d5\u76ae\u8d28\u6c99\u53d1\uff0c\u9002\u5408\u5ba2\u5385\u3001\u4e66\u623f\u548c\u9ad8\u8d28\u611f\u63a8\u8350\u3002",
                        BROWN_LEATHER_SOFA_IMAGE, 1299900, 1599900, 780000, 8);
                ensureProduct(connection, brandId, diningCategoryId,
                        "\u9910\u684c Dining Table",
                        "table \u9910\u684c dining table family dinner under 6000",
                        "\u516d\u4eba\u4f4d\u9910\u684c\uff0c\u9002\u5408\u9910\u5385\u3001\u5bb6\u5ead\u805a\u9910\u548c\u4e2d\u7b49\u9884\u7b97\u63a8\u8350\u3002",
                        DINING_TABLE_IMAGE, 459900, 599900, 260000, 12);
                ensureProduct(connection, brandId, bedroomCategoryId,
                        "\u5e8a Bed",
                        "bed \u5e8a bedroom upholstered queen under 7000",
                        "\u8f6f\u5305\u53cc\u4eba\u5e8a\uff0c\u9002\u5408\u5367\u5ba4\u642d\u914d\u548c\u9884\u7b97\u7b5b\u9009\u3002",
                        UPHOLSTERED_BED_IMAGE, 529900, 699900, 310000, 10);
                ensureProduct(connection, brandId, lightingCategoryId,
                        "\u540a\u706f Lighting",
                        "lighting lamp \u540a\u706f pendant light dining room under 3000",
                        "\u91d1\u5c5e\u540a\u706f\uff0c\u9002\u5408\u9910\u5385\u3001\u5ba2\u5385\u548c\u6c1b\u56f4\u7167\u660e\u63a8\u8350\u3002",
                        PENDANT_LIGHT_IMAGE, 189900, 259900, 90000, 25);
                ensureProduct(connection, brandId, storageCategoryId,
                        "\u7535\u89c6\u67dc Cabinet",
                        "cabinet \u7535\u89c6\u67dc media console living room under 5000",
                        "\u4f4e\u77ee\u7535\u89c6\u67dc\uff0c\u9002\u5408\u5ba2\u5385\u6536\u7eb3\u548c\u7535\u89c6\u5899\u642d\u914d\u3002",
                        MEDIA_CABINET_IMAGE, 359900, 499900, 190000, 14);
                ensureProduct(connection, brandId, diningCategoryId,
                        "\u9ed1\u8272\u5706\u5f62\u9910\u684c Dining Table",
                        "dining table \u9ed1\u8272\u5706\u5f62\u9910\u684c black round dining room under 6000",
                        "\u9ed1\u8272\u5706\u5f62\u9910\u684c\uff0c\u9002\u5408\u73b0\u4ee3\u9910\u5385\u548c\u56db\u4eba\u4f4d\u5c0f\u5bb6\u5ead\u7528\u9910\u573a\u666f\u3002",
                        BLACK_DINING_SET_IMAGE, 569900, 699900, 320000, 9);
                ensureProduct(connection, brandId, diningCategoryId,
                        "\u539f\u6728\u957f\u9910\u684c Dining Table",
                        "dining table \u539f\u6728\u957f\u9910\u684c wood family dining room under 7000",
                        "\u539f\u6728\u8272\u957f\u9910\u684c\uff0c\u9002\u5408\u516d\u5230\u516b\u4eba\u4f4d\u5bb6\u5ead\u9910\u5385\u548c\u6e29\u6696\u6728\u8d28\u98ce\u683c\u3002",
                        WOOD_DINING_SET_IMAGE, 599900, 759900, 350000, 7);
                ensureProduct(connection, brandId, diningChairCategoryId,
                        "\u7070\u8272\u8f6f\u5305\u9910\u6905 Chair",
                        "chair \u7070\u8272\u8f6f\u5305\u9910\u6905 dining chair fabric wood legs under 1500",
                        "\u7070\u8272\u5e03\u827a\u8f6f\u5305\u9910\u6905\uff0c\u642d\u914d\u6728\u8d28\u684c\u811a\uff0c\u9002\u5408\u9910\u5385\u8865\u6905\u548c\u5c0f\u9884\u7b97\u573a\u666f\u3002",
                        DINING_CHAIR_IMAGE, 89900, 129900, 42000, 32);
                ensureProduct(connection, brandId, diningChairCategoryId,
                        "\u9ed1\u8272\u73b0\u4ee3\u9910\u6905 Chair",
                        "chair \u9ed1\u8272\u9910\u6905 dining chair modern black under 2000",
                        "\u9ed1\u8272\u73b0\u4ee3\u9910\u6905\uff0c\u9002\u5408\u9ed1\u8272\u9910\u684c\u3001\u6781\u7b80\u9910\u5385\u548c\u5957\u88c5\u642d\u914d\u3002",
                        BLACK_DINING_CHAIR_IMAGE, 129900, 179900, 58000, 24);
                ensureProduct(connection, brandId, coffeeTableCategoryId,
                        "\u9ed1\u8272\u73bb\u7483\u8336\u51e0 Coffee Table",
                        "coffee table \u9ed1\u8272\u73bb\u7483\u8336\u51e0 living room rug under 3000",
                        "\u9ed1\u8272\u73bb\u7483\u8336\u51e0\uff0c\u9002\u5408\u73b0\u4ee3\u5ba2\u5385\u3001\u6d45\u8272\u6c99\u53d1\u548c\u7c73\u8272\u5730\u6bef\u642d\u914d\u3002",
                        COFFEE_TABLE_IMAGE, 249900, 329900, 125000, 15);
                ensureProduct(connection, brandId, coffeeTableCategoryId,
                        "\u539f\u6728\u8336\u51e0 Coffee Table",
                        "coffee table \u539f\u6728\u8336\u51e0 wood living room under 2500",
                        "\u539f\u6728\u8272\u8336\u51e0\uff0c\u6728\u7eb9\u6e05\u6670\uff0c\u9002\u5408\u65e5\u5f0f\u3001\u5317\u6b27\u548c\u81ea\u7136\u98ce\u5ba2\u5385\u3002",
                        WOOD_COFFEE_TABLE_IMAGE, 219900, 299900, 110000, 18);
                ensureProduct(connection, brandId, sideTableCategoryId,
                        "\u68d5\u8272\u6728\u8d28\u8fb9\u51e0 Side Table",
                        "side table \u68d5\u8272\u6728\u8d28\u8fb9\u51e0 living room sofa side under 1500",
                        "\u68d5\u8272\u6728\u8d28\u8fb9\u51e0\uff0c\u9002\u5408\u6c99\u53d1\u65c1\u3001\u5e8a\u8fb9\u548c\u5c0f\u89d2\u843d\u6536\u7eb3\u573a\u666f\u3002",
                        SIDE_TABLE_IMAGE, 129900, 169900, 62000, 20);
                ensureProduct(connection, brandId, officeCategoryId,
                        "\u80e1\u6843\u6728\u4e66\u684c Desk",
                        "desk \u80e1\u6843\u6728\u4e66\u684c home office walnut under 5000",
                        "\u80e1\u6843\u6728\u4e66\u684c\uff0c\u9002\u5408\u5c45\u5bb6\u529e\u516c\u3001\u4e66\u623f\u548c\u663e\u793a\u5668\u529e\u516c\u684c\u642d\u914d\u3002",
                        WALNUT_DESK_IMAGE, 399900, 529900, 210000, 11);
                ensureProduct(connection, brandId, rugCategoryId,
                        "\u7c73\u8272\u7f8a\u6bdb\u5730\u6bef Rug",
                        "rug \u7c73\u8272\u7f8a\u6bdb\u5730\u6bef living room beige under 2500",
                        "\u7c73\u8272\u7f8a\u6bdb\u5730\u6bef\uff0c\u9002\u5408\u6d45\u8272\u6c99\u53d1\u3001\u8336\u51e0\u548c\u73b0\u4ee3\u5ba2\u5385\u8f6f\u88c5\u3002",
                        BEIGE_RUG_TEXTURE_IMAGE, 189900, 259900, 90000, 16);
                ensureProduct(connection, brandId, rugCategoryId,
                        "\u7070\u8272\u7eb9\u7406\u5730\u6bef Rug",
                        "rug \u7070\u8272\u7eb9\u7406\u5730\u6bef living room gray under 2000",
                        "\u7070\u8272\u7eb9\u7406\u5730\u6bef\uff0c\u9002\u5408\u9ed1\u8272\u9910\u684c\u3001\u5ba2\u5385\u4f11\u95f2\u533a\u548c\u4f4e\u9971\u548c\u914d\u8272\u3002",
                        GRAY_RUG_TEXTURE_IMAGE, 159900, 219900, 78000, 19);
                ensureProduct(connection, brandId, bedroomStorageCategoryId,
                        "\u6728\u8d28\u5e8a\u5934\u67dc Nightstand",
                        "nightstand \u6728\u8d28\u5e8a\u5934\u67dc bedroom lamp under 2000",
                        "\u6728\u8d28\u5e8a\u5934\u67dc\uff0c\u642d\u914d\u7403\u5f62\u53f0\u706f\u548c\u8f6f\u5305\u5e8a\uff0c\u9002\u5408\u5367\u5ba4\u6536\u7eb3\u3002",
                        TABLE_LAMP_NIGHTSTAND_IMAGE, 149900, 199900, 72000, 18);
                ensureProduct(connection, brandId, bedroomStorageCategoryId,
                        "\u80e1\u6843\u6728\u6597\u67dc Dresser",
                        "dresser \u80e1\u6843\u6728\u6597\u67dc bedroom storage under 4000",
                        "\u80e1\u6843\u6728\u6597\u67dc\uff0c\u9002\u5408\u5367\u5ba4\u8863\u7269\u6536\u7eb3\u3001\u8d70\u5eca\u548c\u590d\u53e4\u6728\u8d28\u98ce\u683c\u3002",
                        WALNUT_DRESSER_IMAGE, 299900, 399900, 150000, 10);
                ensureProduct(connection, brandId, wardrobeCategoryId,
                        "\u539f\u6728\u8863\u67dc Wardrobe",
                        "wardrobe \u539f\u6728\u8863\u67dc closet bedroom storage under 8000",
                        "\u539f\u6728\u8272\u8863\u67dc\uff0c\u5e26\u6302\u8863\u533a\u548c\u62bd\u5c49\u6536\u7eb3\uff0c\u9002\u5408\u5367\u5ba4\u6216\u8863\u5e3d\u95f4\u3002",
                        WARDROBE_IMAGE, 699900, 899900, 380000, 6);
                ensureProduct(connection, brandId, lightingCategoryId,
                        "\u767d\u8272\u7403\u5f62\u53f0\u706f Table Lamp",
                        "table lamp \u767d\u8272\u7403\u5f62\u53f0\u706f bedside nightstand under 1000",
                        "\u767d\u8272\u7403\u5f62\u53f0\u706f\uff0c\u9002\u5408\u5e8a\u5934\u67dc\u3001\u5367\u5ba4\u6c1b\u56f4\u548c\u67d4\u548c\u591c\u95f4\u7167\u660e\u3002",
                        WHITE_TABLE_LAMP_IMAGE, 89900, 129900, 36000, 26);
                ensureProduct(connection, brandId, lightingCategoryId,
                        "\u9ed1\u8272\u843d\u5730\u706f Floor Lamp",
                        "floor lamp \u9ed1\u8272\u843d\u5730\u706f living room sofa under 2000",
                        "\u9ed1\u8272\u843d\u5730\u706f\uff0c\u9002\u5408\u6c99\u53d1\u65c1\u8fb9\u3001\u9605\u8bfb\u89d2\u548c\u5ba2\u5385\u8f85\u52a9\u7167\u660e\u3002",
                        BLACK_FLOOR_LAMP_IMAGE, 169900, 229900, 82000, 21);
                ensureProduct(connection, brandId, storageCategoryId,
                        "\u80e1\u6843\u6728\u9910\u8fb9\u67dc Cabinet",
                        "cabinet \u80e1\u6843\u6728\u9910\u8fb9\u67dc sideboard dining storage under 4000",
                        "\u80e1\u6843\u6728\u9910\u8fb9\u67dc\uff0c\u9002\u5408\u9910\u5385\u9910\u5177\u6536\u7eb3\u3001\u5ba2\u5385\u9648\u5217\u548c\u6728\u8d28\u98ce\u683c\u3002",
                        SIDEBOARD_IMAGE, 329900, 449900, 170000, 12);

                connection.commit();
                printCount(connection);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
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

    private static void ensureProduct(Connection connection, long brandId, long categoryId, String name,
                                      String keyword, String introduction, String image, int price, int marketPrice,
                                      int costPrice, int stock) throws Exception {
        Long existing = findId(connection, "select id from product_spu where tenant_id=? and keyword=? and deleted=b'0'",
                TENANT_ID, keyword);
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
        ps.setString(8, "[\"" + image + "\"]");
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
& "$javaHome\bin\javac.exe" -encoding UTF-8 -cp $driver $javaFile
if ($LASTEXITCODE -ne 0) {
    throw "Failed to compile seed helper."
}
& "$javaHome\bin\java.exe" -cp "$tmpDir;$driver" SeedFurnitureAgentProducts
if ($LASTEXITCODE -ne 0) {
    throw "Failed to run seed helper."
}
