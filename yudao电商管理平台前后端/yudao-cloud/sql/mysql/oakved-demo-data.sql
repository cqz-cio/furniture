-- Oakved demo catalog: tenant 121, 26 mall products, ERP products, stock and mappings.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @tenant_id = 121;
SET @seed_user = 'furniture-agent-seed';
SET @default_image = 'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=1200&q=80';

UPDATE system_tenant SET expire_time = '2099-12-31 23:59:59', updater = @seed_user,
  update_time = CURRENT_TIMESTAMP WHERE id = @tenant_id AND deleted = b'0';

INSERT INTO product_brand(name,pic_url,sort,description,status,creator,updater,tenant_id)
SELECT 'Trendz Demo',@default_image,10,'Demo brand for furniture catalog',0,@seed_user,@seed_user,@tenant_id
WHERE NOT EXISTS (SELECT 1 FROM product_brand WHERE tenant_id=@tenant_id AND name='Trendz Demo' AND deleted=b'0');
SET @brand_id = (SELECT id FROM product_brand WHERE tenant_id=@tenant_id AND name='Trendz Demo' AND deleted=b'0' ORDER BY id LIMIT 1);

INSERT INTO product_category(parent_id,name,pic_url,big_pic_url,sort,status,creator,updater,tenant_id)
SELECT 0,'Furniture Agent Demo',@default_image,@default_image,10,0,@seed_user,@seed_user,@tenant_id
WHERE NOT EXISTS (SELECT 1 FROM product_category WHERE tenant_id=@tenant_id AND parent_id=0 AND name='Furniture Agent Demo' AND deleted=b'0');
SET @root_category_id = (SELECT id FROM product_category WHERE tenant_id=@tenant_id AND parent_id=0 AND name='Furniture Agent Demo' AND deleted=b'0' ORDER BY id LIMIT 1);

DROP PROCEDURE IF EXISTS ensure_oakved_category;
DELIMITER $$
CREATE PROCEDURE ensure_oakved_category(IN category_name varchar(128), IN category_sort int)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM product_category WHERE tenant_id=@tenant_id AND parent_id=@root_category_id AND name=category_name AND deleted=b'0') THEN
    INSERT INTO product_category(parent_id,name,pic_url,big_pic_url,sort,status,creator,updater,tenant_id)
    VALUES(@root_category_id,category_name,@default_image,@default_image,category_sort,0,@seed_user,@seed_user,@tenant_id);
  END IF;
END$$
DELIMITER ;
CALL ensure_oakved_category('Sofas',20);
CALL ensure_oakved_category('Dining Tables',30);
CALL ensure_oakved_category('Dining Chairs',35);
CALL ensure_oakved_category('Coffee Tables',36);
CALL ensure_oakved_category('Beds',40);
CALL ensure_oakved_category('Desks',45);
CALL ensure_oakved_category('Rugs',46);
CALL ensure_oakved_category('Bedroom Storage',47);
CALL ensure_oakved_category('Wardrobes',48);
CALL ensure_oakved_category('Side Tables',49);
CALL ensure_oakved_category('Lighting',50);
CALL ensure_oakved_category('Media Storage',60);
DROP PROCEDURE ensure_oakved_category;

DROP PROCEDURE IF EXISTS seed_oakved_product;
DELIMITER $$
CREATE PROCEDURE seed_oakved_product(
  IN product_name varchar(128), IN product_keyword varchar(256), IN category_name varchar(128),
  IN image_url varchar(512), IN product_price int, IN product_market_price int,
  IN product_cost_price int, IN product_stock int)
BEGIN
  DECLARE v_category_id bigint;
  DECLARE v_spu_id bigint;
  DECLARE v_sku_id bigint;
  SELECT id INTO v_category_id FROM product_category
    WHERE tenant_id=@tenant_id AND parent_id=@root_category_id AND name=category_name AND deleted=b'0' ORDER BY id LIMIT 1;
  SELECT id INTO v_spu_id FROM product_spu
    WHERE tenant_id=@tenant_id AND keyword=product_keyword AND deleted=b'0' ORDER BY id LIMIT 1;
  IF v_spu_id IS NULL THEN
    INSERT INTO product_spu(name,keyword,introduction,description,category_id,brand_id,pic_url,slider_pic_urls,
      unit,sort,status,spec_type,price,market_price,cost_price,stock,delivery_types,delivery_template_id,
      recommend_hot,recommend_benefit,recommend_best,recommend_new,recommend_good,give_integral,
      sub_commission_type,sales_count,virtual_sales_count,browse_count,creator,updater,tenant_id)
    VALUES(product_name,product_keyword,product_name,CONCAT('<p>',product_name,'</p>'),v_category_id,@brand_id,image_url,
      JSON_ARRAY(image_url,REPLACE(image_url,'w=1200','w=1600')),1,100,1,b'0',product_price,product_market_price,
      product_cost_price,product_stock,'1',0,b'1',b'0',b'1',b'1',b'1',0,b'0',0,0,0,@seed_user,@seed_user,@tenant_id);
    SET v_spu_id = LAST_INSERT_ID();
  ELSE
    UPDATE product_spu SET name=product_name,category_id=v_category_id,brand_id=@brand_id,pic_url=image_url,
      slider_pic_urls=JSON_ARRAY(image_url,REPLACE(image_url,'w=1200','w=1600')),price=product_price,
      market_price=product_market_price,cost_price=product_cost_price,stock=product_stock,status=1,
      updater=@seed_user,update_time=CURRENT_TIMESTAMP WHERE id=v_spu_id AND tenant_id=@tenant_id;
  END IF;
  SELECT id INTO v_sku_id FROM product_sku WHERE tenant_id=@tenant_id AND spu_id=v_spu_id AND deleted=b'0' ORDER BY id LIMIT 1;
  IF v_sku_id IS NULL THEN
    INSERT INTO product_sku(spu_id,properties,price,market_price,cost_price,bar_code,pic_url,stock,weight,volume,
      sales_count,creator,updater,tenant_id)
    VALUES(v_spu_id,'[]',product_price,product_market_price,product_cost_price,CONCAT('FA-',v_spu_id),image_url,
      product_stock,35.0,1.6,0,@seed_user,@seed_user,@tenant_id);
  ELSE
    UPDATE product_sku SET price=product_price,market_price=product_market_price,cost_price=product_cost_price,
      pic_url=image_url,stock=product_stock,updater=@seed_user,update_time=CURRENT_TIMESTAMP
      WHERE id=v_sku_id AND tenant_id=@tenant_id;
  END IF;
END$$
DELIMITER ;

CALL seed_oakved_product('Cream Fabric Sofa','sofa cream performance fabric living room','Sofas','https://images.unsplash.com/photo-1768144092684-c1a5dd6c7aad?auto=format&fit=crop&w=1200&q=80',699900,899900,420000,18);
CALL seed_oakved_product('Cloud Modular Sofa','sofa cloud modular linen living room','Sofas','https://images.unsplash.com/photo-1618221195710-dd6b41faaea6?auto=format&fit=crop&w=1200&q=80',329900,459900,220000,18);
CALL seed_oakved_product('Leather Lounge Sofa','sofa leather lounge brown premium','Sofas','https://images.unsplash.com/photo-1678225179685-b8ad3b6d7249?auto=format&fit=crop&w=1200&q=80',1299900,1599900,780000,8);
CALL seed_oakved_product('Ivory Performance Sofa','sofa ivory performance fabric apartment','Sofas','https://images.unsplash.com/photo-1493663284031-b7e3aefcae8e?auto=format&fit=crop&w=1200&q=80',749900,929900,450000,13);
CALL seed_oakved_product('Compact Linen Sofa','sofa compact linen small apartment','Sofas','https://images.unsplash.com/photo-1631679706909-1844bbd07221?auto=format&fit=crop&w=1200&q=80',329900,459900,220000,18);
CALL seed_oakved_product('Brown Leather Club Sofa','sofa brown leather club study','Sofas','https://images.unsplash.com/photo-1616593871468-2a9452218369?auto=format&fit=crop&w=1200&q=80',1299900,1599900,780000,8);
CALL seed_oakved_product('Natural Oak Dining Table','dining table natural oak six seat','Dining Tables','https://images.unsplash.com/photo-1730630906214-1256b57d65b7?auto=format&fit=crop&w=1200&q=80',459900,599900,260000,12);
CALL seed_oakved_product('Upholstered Shelter Bed','bed upholstered shelter queen bedroom','Beds','https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80',529900,699900,310000,10);
CALL seed_oakved_product('Brass Drum Pendant','lighting brass drum pendant dining','Lighting','https://images.unsplash.com/photo-1721523262897-49620a979091?auto=format&fit=crop&w=1200&q=80',189900,259900,90000,25);
CALL seed_oakved_product('Fluted Oak Media Console','media console fluted oak living room','Media Storage','https://images.unsplash.com/photo-1646861039459-fd9e3aabf3fb?auto=format&fit=crop&w=1200&q=80',359900,499900,190000,14);
CALL seed_oakved_product('Black Round Dining Table','dining table black round four seat','Dining Tables','https://images.unsplash.com/photo-1729606312336-63d202c090b1?auto=format&fit=crop&w=1200&q=80',569900,699900,320000,9);
CALL seed_oakved_product('Reclaimed Wood Dining Table','dining table reclaimed wood eight seat','Dining Tables','https://images.unsplash.com/photo-1758977403438-1b8546560d31?auto=format&fit=crop&w=1200&q=80',599900,759900,350000,7);
CALL seed_oakved_product('Grey Upholstered Dining Chair','dining chair grey upholstered wood legs','Dining Chairs','https://images.unsplash.com/photo-1503602642458-232111445657?auto=format&fit=crop&w=1200&q=80',89900,129900,42000,32);
CALL seed_oakved_product('Black Spindle Dining Chair','dining chair black spindle modern','Dining Chairs','https://images.unsplash.com/photo-1592078615290-033ee584e267?auto=format&fit=crop&w=1200&q=80',129900,179900,58000,24);
CALL seed_oakved_product('Smoked Glass Coffee Table','coffee table smoked glass modern','Coffee Tables','https://images.unsplash.com/photo-1609766857120-0183863c7971?auto=format&fit=crop&w=1200&q=80',249900,329900,125000,15);
CALL seed_oakved_product('Natural Oak Coffee Table','coffee table natural oak living room','Coffee Tables','https://images.unsplash.com/photo-1605774337664-7a846e9cdf17?auto=format&fit=crop&w=1200&q=80',219900,299900,110000,18);
CALL seed_oakved_product('Walnut Drum Side Table','side table walnut drum sofa bedside','Side Tables','https://images.unsplash.com/photo-1565374369705-acde12f3caa2?auto=format&fit=crop&w=1200&q=80',129900,169900,62000,20);
CALL seed_oakved_product('Walnut Writing Desk','desk walnut writing home office','Desks','https://images.unsplash.com/photo-1575318633968-0383e7d07ca0?auto=format&fit=crop&w=1200&q=80',399900,529900,210000,11);
CALL seed_oakved_product('Handwoven Beige Wool Rug','rug beige wool handwoven living room','Rugs','https://images.unsplash.com/photo-1520762042279-ae3264026bd2?auto=format&fit=crop&w=1200&q=80',189900,259900,90000,16);
CALL seed_oakved_product('Textured Grey Area Rug','rug grey textured area living room','Rugs','https://images.unsplash.com/photo-1618220252344-8ec99ec624b1?auto=format&fit=crop&w=1200&q=80',159900,219900,78000,19);
CALL seed_oakved_product('Oak Two-Drawer Nightstand','nightstand oak two drawer bedroom','Bedroom Storage','https://images.unsplash.com/photo-1565374235393-6fe32a07cc86?auto=format&fit=crop&w=1200&q=80',149900,199900,72000,18);
CALL seed_oakved_product('Walnut Six-Drawer Dresser','dresser walnut six drawer bedroom','Bedroom Storage','https://images.unsplash.com/photo-1579283111509-855c7eea1c49?auto=format&fit=crop&w=1200&q=80',299900,399900,150000,10);
CALL seed_oakved_product('Natural Oak Wardrobe','wardrobe natural oak bedroom storage','Wardrobes','https://images.unsplash.com/photo-1778731660303-1fa5ede75477?auto=format&fit=crop&w=1200&q=80',699900,899900,380000,6);
CALL seed_oakved_product('Opal Glass Table Lamp','table lamp opal glass bedside','Lighting','https://images.unsplash.com/photo-1753932847231-7949af383b98?auto=format&fit=crop&w=1200&q=80',89900,129900,36000,26);
CALL seed_oakved_product('Black Arc Floor Lamp','floor lamp black arc reading','Lighting','https://images.unsplash.com/photo-1494438639946-1ebd1d20bf85?auto=format&fit=crop&w=1200&q=80',169900,229900,82000,21);
CALL seed_oakved_product('Walnut Four-Door Sideboard','sideboard walnut four door dining storage','Media Storage','https://images.unsplash.com/photo-1713810958247-01dbd76b4a61?auto=format&fit=crop&w=1200&q=80',329900,449900,170000,12);
DROP PROCEDURE seed_oakved_product;

SET @erp_user = 'mall-erp-seed';
INSERT INTO erp_product_unit(name,status,creator,updater,tenant_id) VALUES('Piece',0,@erp_user,@erp_user,@tenant_id)
  ON DUPLICATE KEY UPDATE status=VALUES(status),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;
INSERT INTO erp_product_category(parent_id,name,code,sort,status,creator,updater,tenant_id)
  VALUES(0,'Furniture','FURNITURE',10,0,@erp_user,@erp_user,@tenant_id)
  ON DUPLICATE KEY UPDATE name=VALUES(name),status=VALUES(status),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;
SET @erp_root_category_id = (SELECT id FROM erp_product_category
  WHERE tenant_id=@tenant_id AND code='FURNITURE' AND deleted=b'0' ORDER BY id LIMIT 1);
INSERT INTO erp_product_category(parent_id,name,code,sort,status,creator,updater,tenant_id)
SELECT @erp_root_category_id,c.name,CONCAT('MALL_CATEGORY_',c.id),c.sort,0,@erp_user,@erp_user,@tenant_id
FROM product_category c
WHERE c.tenant_id=@tenant_id AND c.parent_id=@root_category_id AND c.deleted=b'0'
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),sort=VALUES(sort),status=VALUES(status),
  updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;
INSERT INTO erp_warehouse(name,address,sort,remark,principal,warehouse_price,truckage_price,status,default_status,creator,updater,tenant_id)
  VALUES('Main Warehouse','',10,'Tenant 121 demo inventory','',0,0,0,b'1',@erp_user,@erp_user,@tenant_id)
  ON DUPLICATE KEY UPDATE status=VALUES(status),default_status=VALUES(default_status),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;
INSERT INTO erp_product(name,bar_code,category_id,unit_id,status,standard,remark,expiry_day,weight,purchase_price,sale_price,min_price,creator,updater,tenant_id)
SELECT p.name,CONCAT('RH-121-',s.id),c.id,u.id,0,CONCAT('Mall SKU ',s.id),'Synchronized from tenant 121 mall catalog',0,
  COALESCE(s.weight,0),s.cost_price/100,s.price/100,s.price/100,@erp_user,@erp_user,@tenant_id
FROM product_sku s JOIN product_spu p ON p.id=s.spu_id AND p.tenant_id=s.tenant_id AND p.deleted=b'0'
JOIN product_category pc ON pc.id=p.category_id AND pc.tenant_id=p.tenant_id AND pc.deleted=b'0'
JOIN erp_product_category c ON c.tenant_id=@tenant_id AND c.code=CONCAT('MALL_CATEGORY_',pc.id) AND c.deleted=b'0'
JOIN erp_product_unit u ON u.tenant_id=@tenant_id AND u.name='Piece' AND u.deleted=b'0'
WHERE s.tenant_id=@tenant_id AND s.deleted=b'0' AND p.creator=@seed_user AND p.status=1
ON DUPLICATE KEY UPDATE name=VALUES(name),category_id=VALUES(category_id),purchase_price=VALUES(purchase_price),
  sale_price=VALUES(sale_price),min_price=VALUES(min_price),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;
INSERT INTO mall_erp_product_mapping(mall_spu_id,mall_sku_id,erp_product_id,erp_product_code,sync_status,last_synced_at,last_error,version,creator,updater,tenant_id)
SELECT s.spu_id,s.id,e.id,e.bar_code,'SUCCESS',CURRENT_TIMESTAMP,'',0,@erp_user,@erp_user,@tenant_id
FROM product_sku s JOIN product_spu p ON p.id=s.spu_id AND p.tenant_id=s.tenant_id AND p.deleted=b'0'
JOIN erp_product e ON e.tenant_id=@tenant_id AND e.bar_code=CONCAT('RH-121-',s.id) AND e.deleted=b'0'
WHERE s.tenant_id=@tenant_id AND s.deleted=b'0' AND p.creator=@seed_user AND p.status=1
ON DUPLICATE KEY UPDATE erp_product_id=VALUES(erp_product_id),sync_status='SUCCESS',last_synced_at=CURRENT_TIMESTAMP,last_error='',updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;
INSERT INTO erp_stock(product_id,warehouse_id,count,creator,updater,tenant_id)
SELECT m.erp_product_id,w.id,s.stock,@erp_user,@erp_user,@tenant_id
FROM mall_erp_product_mapping m JOIN product_sku s ON s.id=m.mall_sku_id AND s.tenant_id=m.tenant_id AND s.deleted=b'0'
JOIN erp_warehouse w ON w.tenant_id=@tenant_id AND w.name='Main Warehouse' AND w.deleted=b'0'
WHERE m.tenant_id=@tenant_id AND m.deleted=b'0'
ON DUPLICATE KEY UPDATE count=VALUES(count),updater=VALUES(updater),update_time=CURRENT_TIMESTAMP;

DROP PROCEDURE IF EXISTS assert_oakved_demo_counts;
DELIMITER $$
CREATE PROCEDURE assert_oakved_demo_counts()
BEGIN
  IF (SELECT COUNT(*) FROM product_spu WHERE tenant_id=121 AND creator='furniture-agent-seed' AND status=1 AND deleted=b'0') <> 26 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Oakved baseline expected 26 active demo products';
  END IF;
  IF (SELECT COUNT(*) FROM mall_erp_product_mapping WHERE tenant_id=121 AND deleted=b'0') <> 26 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Oakved baseline expected 26 ERP mappings';
  END IF;
END$$
DELIMITER ;
CALL assert_oakved_demo_counts();
DROP PROCEDURE assert_oakved_demo_counts;
