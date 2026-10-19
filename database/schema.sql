-- AllFit Fashion sample MySQL database.
-- Targets MySQL 8.0.16+ (enforced CHECK constraints) and uses InnoDB.
-- Sample product IDs 1-10 match the first ten products in public_html/js/catalogue.js.
-- Seeded product colours are deterministic demo placeholders; correct them for real products.
-- Team/member, customer, order, review, and payment records below are fictional examples.
-- Payment examples are simulated; never store card numbers or CVVs in this database.

CREATE DATABASE IF NOT EXISTS allfit_fashion
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE allfit_fashion;

CREATE TABLE IF NOT EXISTS category (
    category_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_name VARCHAR(40) NOT NULL,
    PRIMARY KEY (category_id),
    CONSTRAINT uq_category_name UNIQUE (category_name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS subcategory (
    subcategory_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_id INT UNSIGNED NOT NULL,
    subcategory_name VARCHAR(60) NOT NULL,
    PRIMARY KEY (subcategory_id),
    CONSTRAINT uq_subcategory_name_per_category UNIQUE (category_id, subcategory_name),
    CONSTRAINT fk_subcategory_category
        FOREIGN KEY (category_id) REFERENCES category (category_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS product (
    product_id INT UNSIGNED NOT NULL,
    subcategory_id INT UNSIGNED NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    fit_label VARCHAR(40) NOT NULL DEFAULT 'Regular',
    color_name VARCHAR(40) NOT NULL DEFAULT 'Assorted',
    description TEXT NOT NULL,
    image_url VARCHAR(600) NOT NULL,
    current_price DECIMAL(10,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id),
    KEY ix_product_subcategory_active (subcategory_id, is_active),
    CONSTRAINT chk_product_current_price CHECK (current_price >= 0),
    CONSTRAINT fk_product_subcategory
        FOREIGN KEY (subcategory_id) REFERENCES subcategory (subcategory_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

SET @has_product_color = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product'
      AND column_name = 'color_name'
);
SET @product_color_ddl = IF(
    @has_product_color = 0,
    'ALTER TABLE product ADD COLUMN color_name VARCHAR(40) NOT NULL DEFAULT ''Assorted'' AFTER fit_label',
    'SELECT 1'
);
PREPARE product_color_stmt FROM @product_color_ddl;
EXECUTE product_color_stmt;
DEALLOCATE PREPARE product_color_stmt;

CREATE TABLE IF NOT EXISTS product_variant (
    product_variant_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id INT UNSIGNED NOT NULL,
    size_label VARCHAR(20) NOT NULL,
    stock_quantity INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (product_variant_id),
    CONSTRAINT uq_product_size UNIQUE (product_id, size_label),
    CONSTRAINT chk_variant_stock CHECK (stock_quantity >= 0),
    CONSTRAINT fk_variant_product
        FOREIGN KEY (product_id) REFERENCES product (product_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS customer (
    customer_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id),
    CONSTRAINT uq_customer_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS customer_account (
    customer_id BIGINT UNSIGNED NOT NULL,
    password_salt CHAR(24) NOT NULL,
    password_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id),
    CONSTRAINT fk_customer_account_customer
        FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS customer_address (
    address_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id BIGINT UNSIGNED NOT NULL,
    address_label VARCHAR(40) NOT NULL DEFAULT 'Delivery',
    address_line1 VARCHAR(200) NOT NULL,
    address_line2 VARCHAR(200) NULL,
    city VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country_code CHAR(2) NOT NULL DEFAULT 'ZA',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (address_id),
    KEY ix_customer_address_customer (customer_id),
    CONSTRAINT chk_address_country_code CHECK (country_code REGEXP '^[A-Z]{2}$'),
    CONSTRAINT fk_customer_address_customer
        FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS discount (
    discount_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    discount_code VARCHAR(40) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(7,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from DATETIME NULL,
    valid_until DATETIME NULL,
    PRIMARY KEY (discount_id),
    CONSTRAINT uq_discount_code UNIQUE (discount_code),
    CONSTRAINT chk_discount_type CHECK (discount_type IN ('percentage', 'fixed_amount')),
    CONSTRAINT chk_discount_value CHECK (
        (discount_type = 'percentage' AND discount_value > 0 AND discount_value <= 100)
        OR (discount_type = 'fixed_amount' AND discount_value > 0)
    ),
    CONSTRAINT chk_discount_dates CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from
    )
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `orders` (
    order_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_reference VARCHAR(24) NOT NULL,
    tracking_code VARCHAR(20) NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    address_id BIGINT UNSIGNED NULL,
    discount_id INT UNSIGNED NULL,
    discount_code_at_purchase VARCHAR(40) NULL,
    order_status VARCHAR(24) NOT NULL DEFAULT 'pending',
    shipping_method VARCHAR(16) NOT NULL DEFAULT 'normal',
    currency CHAR(3) NOT NULL DEFAULT 'ZAR',
    shipping_full_name VARCHAR(120) NOT NULL,
    shipping_email VARCHAR(254) NOT NULL,
    shipping_phone VARCHAR(40) NOT NULL,
    shipping_address_line1 VARCHAR(200) NOT NULL,
    shipping_address_line2 VARCHAR(200) NULL,
    shipping_city VARCHAR(100) NOT NULL,
    shipping_postal_code VARCHAR(20) NOT NULL,
    shipping_country_code CHAR(2) NOT NULL DEFAULT 'ZA',
    subtotal DECIMAL(12,2) NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    shipping_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    priority_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12,2) NOT NULL,
    placed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    CONSTRAINT uq_order_reference UNIQUE (order_reference),
    CONSTRAINT uq_order_tracking_code UNIQUE (tracking_code),
    KEY ix_orders_customer_date (customer_id, placed_at),
    KEY ix_orders_status_date (order_status, placed_at),
    CONSTRAINT chk_order_status CHECK (
        order_status IN ('pending', 'confirmed', 'processing', 'shipped', 'delivered', 'cancelled', 'refunded')
    ),
    CONSTRAINT chk_order_shipping_method CHECK (shipping_method IN ('normal', 'priority')),
    CONSTRAINT chk_order_currency CHECK (currency REGEXP '^[A-Z]{3}$'),
    CONSTRAINT chk_order_country_code CHECK (shipping_country_code REGEXP '^[A-Z]{2}$'),
    CONSTRAINT chk_order_amounts CHECK (
        subtotal >= 0
        AND discount_amount >= 0
        AND discount_amount <= subtotal
        AND shipping_amount >= 0
        AND priority_fee >= 0
        AND total_amount = subtotal - discount_amount + shipping_amount + priority_fee
    ),
    CONSTRAINT fk_order_customer
        FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_order_address
        FOREIGN KEY (address_id) REFERENCES customer_address (address_id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_order_discount
        FOREIGN KEY (discount_id) REFERENCES discount (discount_id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB;

SET @has_order_tracking_code = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND column_name = 'tracking_code'
);
SET @order_tracking_ddl = IF(
    @has_order_tracking_code = 0,
    'ALTER TABLE `orders` ADD COLUMN tracking_code VARCHAR(20) NULL AFTER order_reference',
    'SELECT 1'
);
PREPARE order_tracking_stmt FROM @order_tracking_ddl;
EXECUTE order_tracking_stmt;
DEALLOCATE PREPARE order_tracking_stmt;

UPDATE `orders`
SET tracking_code = CONCAT('TRK-', LPAD(order_id, 12, '0'))
WHERE tracking_code IS NULL OR tracking_code = '';

ALTER TABLE `orders` MODIFY COLUMN tracking_code VARCHAR(20) NOT NULL;

SET @has_order_tracking_index = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'orders'
      AND index_name = 'uq_order_tracking_code'
);
SET @order_tracking_index_ddl = IF(
    @has_order_tracking_index = 0,
    'ALTER TABLE `orders` ADD CONSTRAINT uq_order_tracking_code UNIQUE (tracking_code)',
    'SELECT 1'
);
PREPARE order_tracking_index_stmt FROM @order_tracking_index_ddl;
EXECUTE order_tracking_index_stmt;
DEALLOCATE PREPARE order_tracking_index_stmt;

CREATE TABLE IF NOT EXISTS order_item (
    order_item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id BIGINT UNSIGNED NOT NULL,
    product_variant_id BIGINT UNSIGNED NOT NULL,
    product_name_at_purchase VARCHAR(160) NOT NULL,
    size_label_at_purchase VARCHAR(20) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    unit_price_at_purchase DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(12,2)
        GENERATED ALWAYS AS (quantity * unit_price_at_purchase) STORED,
    PRIMARY KEY (order_item_id),
    CONSTRAINT uq_order_variant UNIQUE (order_id, product_variant_id),
    KEY ix_order_item_variant (product_variant_id),
    CONSTRAINT chk_order_item_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT chk_order_item_unit_price CHECK (unit_price_at_purchase >= 0),
    CONSTRAINT fk_order_item_order
        FOREIGN KEY (order_id) REFERENCES `orders` (order_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_order_item_variant
        FOREIGN KEY (product_variant_id) REFERENCES product_variant (product_variant_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS payment (
    payment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id BIGINT UNSIGNED NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'ZAR',
    payment_method VARCHAR(24) NOT NULL,
    payment_status VARCHAR(24) NOT NULL DEFAULT 'pending',
    provider_name VARCHAR(80) NULL,
    transaction_reference VARCHAR(120) NULL,
    processed_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_id),
    CONSTRAINT uq_payment_transaction_reference UNIQUE (transaction_reference),
    KEY ix_payment_order_created (order_id, created_at),
    CONSTRAINT chk_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_currency CHECK (currency REGEXP '^[A-Z]{3}$'),
    CONSTRAINT chk_payment_method CHECK (
        payment_method IN ('demo', 'card', 'eft', 'cash', 'other')
    ),
    CONSTRAINT chk_payment_status CHECK (
        payment_status IN ('pending', 'authorized', 'captured', 'failed', 'refunded', 'mock_approved')
    ),
    CONSTRAINT fk_payment_order
        FOREIGN KEY (order_id) REFERENCES `orders` (order_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS product_review (
    review_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id INT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NULL,
    rating TINYINT UNSIGNED NOT NULL,
    review_title VARCHAR(120) NULL,
    review_text TEXT NULL,
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (review_id),
    UNIQUE KEY uq_customer_product_review (customer_id, product_id),
    KEY ix_product_review_approved (product_id, is_approved),
    CONSTRAINT chk_product_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_product_review_product
        FOREIGN KEY (product_id) REFERENCES product (product_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_product_review_customer
        FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS team (
    team_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    team_name VARCHAR(120) NOT NULL,
    course_code VARCHAR(30) NOT NULL DEFAULT 'INFS 222',
    description VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (team_id),
    CONSTRAINT uq_team_course_name UNIQUE (course_code, team_name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS team_member (
    team_member_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    student_number VARCHAR(40) NULL,
    email VARCHAR(254) NULL,
    PRIMARY KEY (team_member_id),
    CONSTRAINT uq_team_member_student_number UNIQUE (student_number),
    CONSTRAINT uq_team_member_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS team_membership (
    team_id INT UNSIGNED NOT NULL,
    team_member_id INT UNSIGNED NOT NULL,
    member_role VARCHAR(60) NOT NULL DEFAULT 'Member',
    joined_at DATE NOT NULL DEFAULT (CURRENT_DATE),
    PRIMARY KEY (team_id, team_member_id),
    CONSTRAINT fk_team_membership_team
        FOREIGN KEY (team_id) REFERENCES team (team_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_team_membership_member
        FOREIGN KEY (team_member_id) REFERENCES team_member (team_member_id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB;

-- Lookup data mirrors the catalogue's department/subcategory hierarchy.
INSERT IGNORE INTO category (category_id, category_name) VALUES
    (1, 'Women'),
    (2, 'Men'),
    (3, 'Kids');

INSERT IGNORE INTO subcategory (subcategory_id, category_id, subcategory_name) VALUES
    (1, 1, 'Dresses'),
    (2, 1, 'Tops'),
    (3, 1, 'Bottoms'),
    (4, 1, 'Outerwear'),
    (5, 1, 'Shoes'),
    (6, 1, 'Bags'),
    (7, 1, 'Underwear'),
    (8, 2, 'Shirts'),
    (9, 2, 'Bottoms'),
    (10, 2, 'Outerwear'),
    (11, 2, 'Shoes'),
    (12, 2, 'Underwear'),
    (13, 2, 'Accessories'),
    (14, 3, 'Tops'),
    (15, 3, 'Bottoms'),
    (16, 3, 'Dresses'),
    (17, 3, 'Outerwear'),
    (18, 3, 'Shoes'),
    (19, 3, 'Accessories');

-- Product IDs 1-10 mirror the explicit products in catalogue.js.
INSERT IGNORE INTO product
    (product_id, subcategory_id, product_name, fit_label, description, image_url, current_price)
VALUES
    (1, 2, 'Square Neck Top', 'Regular',
     'A soft, fitted top with a clean square neckline, easy to style from day to evening.',
     'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=480', 299.00),
    (2, 3, 'Wide Leg Trousers', 'Regular',
     'Relaxed wide-leg trousers with a high-rise shape for a polished, comfortable fit.',
     'https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?w=480', 499.00),
    (3, 6, 'Shoulder Bag', 'Regular',
     'A versatile everyday shoulder bag with a spacious interior for your essentials.',
     'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=480', 349.00),
    (4, 5, 'Classic Sneakers', 'Regular',
     'Low-profile everyday sneakers with a timeless shape and cushioned feel.',
     'https://images.unsplash.com/photo-1549298916-b41d501d3772?w=480', 599.00),
    (5, 1, 'Midi Dress', 'Regular',
     'An easy midi-length dress with a flattering silhouette for effortless occasion dressing.',
     'https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=480', 699.00),
    (6, 4, 'Knit Cardigan', 'Regular',
     'A soft knit cardigan made for comfortable layering through the changing seasons.',
     'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=480', 449.00),
    (7, 9, 'Cargo Pants', 'Regular',
     'Utility-inspired cargo pants with a relaxed fit and practical everyday pockets.',
     'https://images.unsplash.com/photo-1517438476312-10d79c077509?w=480', 549.00),
    (8, 2, 'Halter Neck Top', 'Regular',
     'A lightweight halter-neck top that brings a clean, modern finish to warm-weather looks.',
     'https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?w=480', 279.00),
    (9, 17, 'Kids Denim Jacket', 'Regular',
     'A durable denim layer with a classic cut, designed for everyday play and outings.',
     'https://images.unsplash.com/photo-1519457431-44ccd64a579b?w=480', 399.00),
    (10, 1, 'Plus Size Maxi Dress', 'Plus Size',
     'A flowing maxi dress designed for a comfortable fit and an easy, confident look.',
     'https://images.unsplash.com/photo-1585487000160-6ebcfceb0d03?w=480', 799.00);

-- Product IDs 11-100 mirror the generated catalogue entries in catalogue.js.
INSERT IGNORE INTO product
    (product_id, subcategory_id, product_name, fit_label, description, image_url, current_price)
VALUES
    (11, 1, 'Pleated Wrap Midi', 'Regular', 'Pleated Wrap Midi: A considered dress with an easy-to-wear fit, made to bring a little polish to everyday plans.', 'https://images.unsplash.com/photo-1496747611176-843222e1e57c?auto=format&fit=crop&w=480&h=560&q=80', 591.39),
    (12, 1, 'Floral Day Dress', 'Regular', 'Floral Day Dress: A considered dress with an easy-to-wear fit, made to bring a little polish to everyday plans.', 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?auto=format&fit=crop&w=480&h=560&q=80', 628.88),
    (13, 1, 'Satin Slip Dress', 'Regular', 'Satin Slip Dress: A considered dress with an easy-to-wear fit, made to bring a little polish to everyday plans.', 'https://images.unsplash.com/photo-1585487000160-6ebcfceb0d03?auto=format&fit=crop&w=480&h=560&q=80', 666.37),
    (14, 1, 'Button-Front Shirt Dress', 'Regular', 'Button-Front Shirt Dress: A considered dress with an easy-to-wear fit, made to bring a little polish to everyday plans.', 'https://images.unsplash.com/photo-1496747611176-843222e1e57c?auto=format&fit=crop&w=480&h=560&q=80', 703.86),
    (15, 1, 'Linen Tiered Dress', 'Regular', 'Linen Tiered Dress: A considered dress with an easy-to-wear fit, made to bring a little polish to everyday plans.', 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?auto=format&fit=crop&w=480&h=560&q=80', 741.35),
    (16, 1, 'Smocked Summer Dress', 'Regular', 'Smocked Summer Dress: A considered dress with an easy-to-wear fit, made to bring a little polish to everyday plans.', 'https://images.unsplash.com/photo-1585487000160-6ebcfceb0d03?auto=format&fit=crop&w=480&h=560&q=80', 778.84),
    (17, 2, 'Ribbed Scoop Tee', 'Regular', 'Ribbed Scoop Tee: An effortless wardrobe staple with a flattering shape and comfortable finish.', 'https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?auto=format&fit=crop&w=480&h=560&q=80', 816.33),
    (18, 2, 'Linen Button Blouse', 'Regular', 'Linen Button Blouse: An effortless wardrobe staple with a flattering shape and comfortable finish.', 'https://images.unsplash.com/photo-1551488831-00ddcb6c6bd3?auto=format&fit=crop&w=480&h=560&q=80', 853.82),
    (19, 2, 'Soft Cotton Henley', 'Regular', 'Soft Cotton Henley: An effortless wardrobe staple with a flattering shape and comfortable finish.', 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?auto=format&fit=crop&w=480&h=560&q=80', 891.31),
    (20, 2, 'Ruched Sleeve Top', 'Regular', 'Ruched Sleeve Top: An effortless wardrobe staple with a flattering shape and comfortable finish.', 'https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?auto=format&fit=crop&w=480&h=560&q=80', 928.80),
    (21, 2, 'Relaxed Poplin Shirt', 'Regular', 'Relaxed Poplin Shirt: An effortless wardrobe staple with a flattering shape and comfortable finish.', 'https://images.unsplash.com/photo-1551488831-00ddcb6c6bd3?auto=format&fit=crop&w=480&h=560&q=80', 966.29),
    (22, 2, 'Cropped Knit Tee', 'Regular', 'Cropped Knit Tee: An effortless wardrobe staple with a flattering shape and comfortable finish.', 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?auto=format&fit=crop&w=480&h=560&q=80', 183.78),
    (23, 3, 'Tailored Ankle Pants', 'Regular', 'Tailored Ankle Pants: A comfortable everyday bottom with an easy fit and thoughtful, versatile styling.', 'https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?auto=format&fit=crop&w=480&h=560&q=80', 221.27),
    (24, 3, 'Straight-Leg Jeans', 'Regular', 'Straight-Leg Jeans: A comfortable everyday bottom with an easy fit and thoughtful, versatile styling.', 'https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?auto=format&fit=crop&w=480&h=560&q=80', 258.76),
    (25, 3, 'Pleated Wide Trousers', 'Regular', 'Pleated Wide Trousers: A comfortable everyday bottom with an easy fit and thoughtful, versatile styling.', 'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?auto=format&fit=crop&w=480&h=560&q=80', 296.25),
    (26, 3, 'Everyday Denim Shorts', 'Regular', 'Everyday Denim Shorts: A comfortable everyday bottom with an easy fit and thoughtful, versatile styling.', 'https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?auto=format&fit=crop&w=480&h=560&q=80', 333.74),
    (27, 3, 'Soft Jersey Leggings', 'Regular', 'Soft Jersey Leggings: A comfortable everyday bottom with an easy fit and thoughtful, versatile styling.', 'https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?auto=format&fit=crop&w=480&h=560&q=80', 371.23),
    (28, 3, 'Linen Pull-On Pants', 'Regular', 'Linen Pull-On Pants: A comfortable everyday bottom with an easy fit and thoughtful, versatile styling.', 'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?auto=format&fit=crop&w=480&h=560&q=80', 408.72),
    (29, 4, 'Classic Denim Jacket', 'Regular', 'Classic Denim Jacket: A versatile layer designed for comfort, easy layering, and everyday wear.', 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?auto=format&fit=crop&w=480&h=560&q=80', 446.21),
    (30, 4, 'Lightweight Trench', 'Regular', 'Lightweight Trench: A versatile layer designed for comfort, easy layering, and everyday wear.', 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=480&h=560&q=80', 483.70),
    (31, 4, 'Quilted Everyday Vest', 'Regular', 'Quilted Everyday Vest: A versatile layer designed for comfort, easy layering, and everyday wear.', 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?auto=format&fit=crop&w=480&h=560&q=80', 521.19),
    (32, 5, 'Leather-look Loafer', 'Regular', 'Leather-look Loafer: An easy-to-style pair with a comfortable feel and a versatile finish.', 'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=480&h=560&q=80', 558.68),
    (33, 5, 'Everyday Court Sneaker', 'Regular', 'Everyday Court Sneaker: An easy-to-style pair with a comfortable feel and a versatile finish.', 'https://images.unsplash.com/photo-1543163521-1bf539c55dd2?auto=format&fit=crop&w=480&h=560&q=80', 596.17),
    (34, 5, 'Strap Flat Sandal', 'Regular', 'Strap Flat Sandal: An easy-to-style pair with a comfortable feel and a versatile finish.', 'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=480&h=560&q=80', 633.66),
    (35, 6, 'Everyday Crossbody', 'Regular', 'Everyday Crossbody: A practical accessory sized for daily essentials and easy outfit pairing.', 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?auto=format&fit=crop&w=480&h=560&q=80', 671.15),
    (36, 6, 'Soft Shopper Tote', 'Regular', 'Soft Shopper Tote: A practical accessory sized for daily essentials and easy outfit pairing.', 'https://images.unsplash.com/photo-1584917865442-de89df76afd3?auto=format&fit=crop&w=480&h=560&q=80', 708.64),
    (37, 6, 'Mini Shoulder Bag', 'Regular', 'Mini Shoulder Bag: A practical accessory sized for daily essentials and easy outfit pairing.', 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?auto=format&fit=crop&w=480&h=560&q=80', 746.13),
    (38, 7, 'Cotton Brief Set', 'Regular', 'Cotton Brief Set: A soft, comfortable everyday essential designed for a gentle feel and reliable fit.', 'https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?auto=format&fit=crop&w=480&h=560&q=80', 783.62),
    (39, 7, 'Seamless Everyday Bra', 'Regular', 'Seamless Everyday Bra: A soft, comfortable everyday essential designed for a gentle feel and reliable fit.', 'https://images.unsplash.com/photo-1583846783214-7229a91b20ed?auto=format&fit=crop&w=480&h=560&q=80', 821.11),
    (40, 7, 'Lace-Trim Brief', 'Regular', 'Lace-Trim Brief: A soft, comfortable everyday essential designed for a gentle feel and reliable fit.', 'https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?auto=format&fit=crop&w=480&h=560&q=80', 858.60),
    (41, 7, 'Soft Lounge Bralette', 'Regular', 'Soft Lounge Bralette: A soft, comfortable everyday essential designed for a gentle feel and reliable fit.', 'https://images.unsplash.com/photo-1583846783214-7229a91b20ed?auto=format&fit=crop&w=480&h=560&q=80', 896.09),
    (42, 7, 'Cotton Camisole', 'Regular', 'Cotton Camisole: A soft, comfortable everyday essential designed for a gentle feel and reliable fit.', 'https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?auto=format&fit=crop&w=480&h=560&q=80', 933.58),
    (43, 8, 'Oxford Button-Down', 'Regular', 'Oxford Button-Down: A dependable shirt with a comfortable cut, ready for workdays and weekends.', 'https://images.unsplash.com/photo-1618354691373-d851c5c3a990?auto=format&fit=crop&w=480&h=560&q=80', 801.41),
    (44, 8, 'Textured Polo Shirt', 'Regular', 'Textured Polo Shirt: A dependable shirt with a comfortable cut, ready for workdays and weekends.', 'https://images.unsplash.com/photo-1603252109303-2751441dd157?auto=format&fit=crop&w=480&h=560&q=80', 844.28),
    (45, 8, 'Relaxed Linen Shirt', 'Regular', 'Relaxed Linen Shirt: A dependable shirt with a comfortable cut, ready for workdays and weekends.', 'https://images.unsplash.com/photo-1618354691373-d851c5c3a990?auto=format&fit=crop&w=480&h=560&q=80', 887.15),
    (46, 8, 'Classic Crew T-Shirt', 'Regular', 'Classic Crew T-Shirt: A dependable shirt with a comfortable cut, ready for workdays and weekends.', 'https://images.unsplash.com/photo-1603252109303-2751441dd157?auto=format&fit=crop&w=480&h=560&q=80', 930.02),
    (47, 8, 'Checked Casual Shirt', 'Regular', 'Checked Casual Shirt: A dependable shirt with a comfortable cut, ready for workdays and weekends.', 'https://images.unsplash.com/photo-1618354691373-d851c5c3a990?auto=format&fit=crop&w=480&h=560&q=80', 972.89),
    (48, 8, 'Long-Sleeve Henley', 'Regular', 'Long-Sleeve Henley: A dependable shirt with a comfortable cut, ready for workdays and weekends.', 'https://images.unsplash.com/photo-1603252109303-2751441dd157?auto=format&fit=crop&w=480&h=560&q=80', 1015.76),
    (49, 8, 'Lightweight Overshirt', 'Regular', 'Lightweight Overshirt: A dependable shirt with a comfortable cut, ready for workdays and weekends.', 'https://images.unsplash.com/photo-1618354691373-d851c5c3a990?auto=format&fit=crop&w=480&h=560&q=80', 1058.63),
    (50, 9, 'Straight Fit Chinos', 'Regular', 'Straight Fit Chinos: A practical everyday fit with comfort and movement built into the design.', 'https://images.unsplash.com/photo-1473966968600-fa801b869a1a?auto=format&fit=crop&w=480&h=560&q=80', 1101.50),
    (51, 9, 'Relaxed Denim Jeans', 'Regular', 'Relaxed Denim Jeans: A practical everyday fit with comfort and movement built into the design.', 'https://images.unsplash.com/photo-1517438476312-10d79c077509?auto=format&fit=crop&w=480&h=560&q=80', 1144.37),
    (52, 9, 'Everyday Joggers', 'Regular', 'Everyday Joggers: A practical everyday fit with comfort and movement built into the design.', 'https://images.unsplash.com/photo-1473966968600-fa801b869a1a?auto=format&fit=crop&w=480&h=560&q=80', 1187.24),
    (53, 9, 'Tailored Work Trousers', 'Regular', 'Tailored Work Trousers: A practical everyday fit with comfort and movement built into the design.', 'https://images.unsplash.com/photo-1517438476312-10d79c077509?auto=format&fit=crop&w=480&h=560&q=80', 1230.11),
    (54, 9, 'Canvas Utility Shorts', 'Regular', 'Canvas Utility Shorts: A practical everyday fit with comfort and movement built into the design.', 'https://images.unsplash.com/photo-1473966968600-fa801b869a1a?auto=format&fit=crop&w=480&h=560&q=80', 1272.98),
    (55, 9, 'Slim Stretch Jeans', 'Regular', 'Slim Stretch Jeans: A practical everyday fit with comfort and movement built into the design.', 'https://images.unsplash.com/photo-1517438476312-10d79c077509?auto=format&fit=crop&w=480&h=560&q=80', 1315.85),
    (56, 10, 'Canvas Field Jacket', 'Regular', 'Canvas Field Jacket: A comfortable outer layer made for changing weather and easy everyday styling.', 'https://images.unsplash.com/photo-1544923246-77307dd654cb?auto=format&fit=crop&w=480&h=560&q=80', 1358.72),
    (57, 10, 'Lightweight Bomber', 'Regular', 'Lightweight Bomber: A comfortable outer layer made for changing weather and easy everyday styling.', 'https://images.unsplash.com/photo-1551028719-00167b16eac5?auto=format&fit=crop&w=480&h=560&q=80', 1401.59),
    (58, 10, 'Zip-Through Hoodie', 'Regular', 'Zip-Through Hoodie: A comfortable outer layer made for changing weather and easy everyday styling.', 'https://images.unsplash.com/photo-1544923246-77307dd654cb?auto=format&fit=crop&w=480&h=560&q=80', 1444.46),
    (59, 10, 'Denim Trucker Jacket', 'Regular', 'Denim Trucker Jacket: A comfortable outer layer made for changing weather and easy everyday styling.', 'https://images.unsplash.com/photo-1551028719-00167b16eac5?auto=format&fit=crop&w=480&h=560&q=80', 1487.33),
    (60, 10, 'Everyday Puffer Vest', 'Regular', 'Everyday Puffer Vest: A comfortable outer layer made for changing weather and easy everyday styling.', 'https://images.unsplash.com/photo-1544923246-77307dd654cb?auto=format&fit=crop&w=480&h=560&q=80', 259.20),
    (61, 11, 'Low-Top Court Sneaker', 'Regular', 'Low-Top Court Sneaker: A versatile everyday shoe with a comfortable fit and timeless styling.', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=480&h=560&q=80', 302.07),
    (62, 11, 'Everyday Walking Shoe', 'Regular', 'Everyday Walking Shoe: A versatile everyday shoe with a comfortable fit and timeless styling.', 'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=480&h=560&q=80', 344.94),
    (63, 11, 'Classic Canvas Trainer', 'Regular', 'Classic Canvas Trainer: A versatile everyday shoe with a comfortable fit and timeless styling.', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=480&h=560&q=80', 387.81),
    (64, 11, 'Casual Slip-On', 'Regular', 'Casual Slip-On: A versatile everyday shoe with a comfortable fit and timeless styling.', 'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=480&h=560&q=80', 430.68),
    (65, 12, 'Cotton Boxer Briefs', 'Regular', 'Cotton Boxer Briefs: A soft everyday layer designed for a comfortable fit throughout the day.', 'https://images.unsplash.com/photo-1598033129183-c4f50c736f10?auto=format&fit=crop&w=480&h=560&q=80', 473.55),
    (66, 12, 'Everyday Trunk Set', 'Regular', 'Everyday Trunk Set: A soft everyday layer designed for a comfortable fit throughout the day.', 'https://images.unsplash.com/photo-1618354691373-d851c5c3a990?auto=format&fit=crop&w=480&h=560&q=80', 516.42),
    (67, 12, 'Soft Lounge Shorts', 'Regular', 'Soft Lounge Shorts: A soft everyday layer designed for a comfortable fit throughout the day.', 'https://images.unsplash.com/photo-1598033129183-c4f50c736f10?auto=format&fit=crop&w=480&h=560&q=80', 559.29),
    (68, 13, 'Canvas Weekend Cap', 'Regular', 'Canvas Weekend Cap: A useful finishing touch designed to pair easily with everyday essentials.', 'https://images.unsplash.com/photo-1622560480654-d96214fdc887?auto=format&fit=crop&w=480&h=560&q=80', 602.16),
    (69, 13, 'Woven Everyday Belt', 'Regular', 'Woven Everyday Belt: A useful finishing touch designed to pair easily with everyday essentials.', 'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?auto=format&fit=crop&w=480&h=560&q=80', 645.03),
    (70, 13, 'Classic Knit Beanie', 'Regular', 'Classic Knit Beanie: A useful finishing touch designed to pair easily with everyday essentials.', 'https://images.unsplash.com/photo-1622560480654-d96214fdc887?auto=format&fit=crop&w=480&h=560&q=80', 687.90),
    (71, 13, 'Compact Crossbody Pouch', 'Regular', 'Compact Crossbody Pouch: A useful finishing touch designed to pair easily with everyday essentials.', 'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?auto=format&fit=crop&w=480&h=560&q=80', 730.77),
    (72, 14, 'Everyday Graphic Tee', 'Regular', 'Everyday Graphic Tee: A soft, easy-care top made for busy school days, play, and weekends.', 'https://images.unsplash.com/photo-1519457431-44ccd64a579b?auto=format&fit=crop&w=480&h=560&q=80', 224.24),
    (73, 14, 'Soft Cotton Polo', 'Regular', 'Soft Cotton Polo: A soft, easy-care top made for busy school days, play, and weekends.', 'https://images.unsplash.com/photo-1519238263530-99bdd11df2ea?auto=format&fit=crop&w=480&h=560&q=80', 247.41),
    (74, 14, 'Striped Long-Sleeve Top', 'Regular', 'Striped Long-Sleeve Top: A soft, easy-care top made for busy school days, play, and weekends.', 'https://images.unsplash.com/photo-1519457431-44ccd64a579b?auto=format&fit=crop&w=480&h=560&q=80', 270.58),
    (75, 14, 'Play-All-Day Hoodie', 'Regular', 'Play-All-Day Hoodie: A soft, easy-care top made for busy school days, play, and weekends.', 'https://images.unsplash.com/photo-1519238263530-99bdd11df2ea?auto=format&fit=crop&w=480&h=560&q=80', 293.75),
    (76, 14, 'Button-Front School Shirt', 'Regular', 'Button-Front School Shirt: A soft, easy-care top made for busy school days, play, and weekends.', 'https://images.unsplash.com/photo-1519457431-44ccd64a579b?auto=format&fit=crop&w=480&h=560&q=80', 316.92),
    (77, 14, 'Colour-Block Sweatshirt', 'Regular', 'Colour-Block Sweatshirt: A soft, easy-care top made for busy school days, play, and weekends.', 'https://images.unsplash.com/photo-1519238263530-99bdd11df2ea?auto=format&fit=crop&w=480&h=560&q=80', 340.09),
    (78, 15, 'Pull-On Denim Jeans', 'Regular', 'Pull-On Denim Jeans: Comfortable bottoms designed to move easily through play and everyday adventures.', 'https://images.unsplash.com/photo-1519457431-44ccd64a579b?auto=format&fit=crop&w=480&h=560&q=80', 363.26),
    (79, 15, 'Comfy Jogger Pants', 'Regular', 'Comfy Jogger Pants: Comfortable bottoms designed to move easily through play and everyday adventures.', 'https://images.unsplash.com/photo-1503919005314-30d93d07d823?auto=format&fit=crop&w=480&h=560&q=80', 386.43),
    (80, 15, 'Everyday Chino Shorts', 'Regular', 'Everyday Chino Shorts: Comfortable bottoms designed to move easily through play and everyday adventures.', 'https://images.unsplash.com/photo-1519457431-44ccd64a579b?auto=format&fit=crop&w=480&h=560&q=80', 409.60),
    (81, 15, 'Soft Cotton Leggings', 'Regular', 'Soft Cotton Leggings: Comfortable bottoms designed to move easily through play and everyday adventures.', 'https://images.unsplash.com/photo-1503919005314-30d93d07d823?auto=format&fit=crop&w=480&h=560&q=80', 432.77),
    (82, 15, 'School-Day Trousers', 'Regular', 'School-Day Trousers: Comfortable bottoms designed to move easily through play and everyday adventures.', 'https://images.unsplash.com/photo-1519457431-44ccd64a579b?auto=format&fit=crop&w=480&h=560&q=80', 455.94),
    (83, 15, 'Playground Cargo Shorts', 'Regular', 'Playground Cargo Shorts: Comfortable bottoms designed to move easily through play and everyday adventures.', 'https://images.unsplash.com/photo-1503919005314-30d93d07d823?auto=format&fit=crop&w=480&h=560&q=80', 479.11),
    (84, 16, 'Floral Twirl Dress', 'Regular', 'Floral Twirl Dress: A comfortable dress with a playful, easy-care style for special days and everyday fun.', 'https://images.unsplash.com/photo-1503919005314-30d93d07d823?auto=format&fit=crop&w=480&h=560&q=80', 502.28),
    (85, 16, 'Soft Jersey Pinafore', 'Regular', 'Soft Jersey Pinafore: A comfortable dress with a playful, easy-care style for special days and everyday fun.', 'https://images.unsplash.com/photo-1519238263530-99bdd11df2ea?auto=format&fit=crop&w=480&h=560&q=80', 525.45),
    (86, 16, 'Button-Front Day Dress', 'Regular', 'Button-Front Day Dress: A comfortable dress with a playful, easy-care style for special days and everyday fun.', 'https://images.unsplash.com/photo-1503919005314-30d93d07d823?auto=format&fit=crop&w=480&h=560&q=80', 548.62),
    (87, 16, 'Party-Ready Tulle Dress', 'Regular', 'Party-Ready Tulle Dress: A comfortable dress with a playful, easy-care style for special days and everyday fun.', 'https://images.unsplash.com/photo-1519238263530-99bdd11df2ea?auto=format&fit=crop&w=480&h=560&q=80', 571.79),
    (88, 17, 'Lightweight Rain Jacket', 'Regular', 'Lightweight Rain Jacket: A practical layer that keeps little ones comfortable through changing weather.', 'https://images.unsplash.com/photo-1519238263530-99bdd11df2ea?auto=format&fit=crop&w=480&h=560&q=80', 594.96),
    (89, 17, 'Cosy Zip Hoodie', 'Regular', 'Cosy Zip Hoodie: A practical layer that keeps little ones comfortable through changing weather.', 'https://images.unsplash.com/photo-1519457431-44ccd64a579b?auto=format&fit=crop&w=480&h=560&q=80', 107.13),
    (90, 17, 'Quilted Puffer Jacket', 'Regular', 'Quilted Puffer Jacket: A practical layer that keeps little ones comfortable through changing weather.', 'https://images.unsplash.com/photo-1519238263530-99bdd11df2ea?auto=format&fit=crop&w=480&h=560&q=80', 130.30),
    (91, 17, 'Everyday Fleece Vest', 'Regular', 'Everyday Fleece Vest: A practical layer that keeps little ones comfortable through changing weather.', 'https://images.unsplash.com/photo-1519457431-44ccd64a579b?auto=format&fit=crop&w=480&h=560&q=80', 153.47),
    (92, 18, 'Playground Runner', 'Regular', 'Playground Runner: A comfortable, durable pair built for little feet on the move.', 'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=480&h=560&q=80', 176.64),
    (93, 18, 'Easy-Fit School Shoe', 'Regular', 'Easy-Fit School Shoe: A comfortable, durable pair built for little feet on the move.', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=480&h=560&q=80', 199.81),
    (94, 18, 'Colour Pop Sneaker', 'Regular', 'Colour Pop Sneaker: A comfortable, durable pair built for little feet on the move.', 'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=480&h=560&q=80', 222.98),
    (95, 18, 'Weekend Canvas Shoe', 'Regular', 'Weekend Canvas Shoe: A comfortable, durable pair built for little feet on the move.', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=480&h=560&q=80', 246.15),
    (96, 19, 'Mini Backpack', 'Regular', 'Mini Backpack: A cheerful everyday accessory sized for school, outings, and play.', 'https://images.unsplash.com/photo-1519238263530-99bdd11df2ea?auto=format&fit=crop&w=480&h=560&q=80', 269.32),
    (97, 19, 'Colourful Bucket Hat', 'Regular', 'Colourful Bucket Hat: A cheerful everyday accessory sized for school, outings, and play.', 'https://images.unsplash.com/photo-1503919005314-30d93d07d823?auto=format&fit=crop&w=480&h=560&q=80', 292.49),
    (98, 19, 'School-Day Lunch Bag', 'Regular', 'School-Day Lunch Bag: A cheerful everyday accessory sized for school, outings, and play.', 'https://images.unsplash.com/photo-1519238263530-99bdd11df2ea?auto=format&fit=crop&w=480&h=560&q=80', 315.66),
    (99, 19, 'Cosy Knit Beanie', 'Regular', 'Cosy Knit Beanie: A cheerful everyday accessory sized for school, outings, and play.', 'https://images.unsplash.com/photo-1503919005314-30d93d07d823?auto=format&fit=crop&w=480&h=560&q=80', 338.83),
    (100, 19, 'Printed Crossbody Bag', 'Regular', 'Printed Crossbody Bag: A cheerful everyday accessory sized for school, outings, and play.', 'https://images.unsplash.com/photo-1519238263530-99bdd11df2ea?auto=format&fit=crop&w=480&h=560&q=80', 362.00)
;

UPDATE product
SET color_name = CASE MOD(product_id, 8)
    WHEN 0 THEN 'Black'
    WHEN 1 THEN 'White'
    WHEN 2 THEN 'Blue'
    WHEN 3 THEN 'Beige'
    WHEN 4 THEN 'Green'
    WHEN 5 THEN 'Pink'
    WHEN 6 THEN 'Red'
    ELSE 'Denim'
END
WHERE product_id BETWEEN 1 AND 100
  AND color_name = 'Assorted';

INSERT IGNORE INTO product_variant (product_id, size_label, stock_quantity) VALUES
    (1, 'XS', 9), (1, 'S', 20), (1, 'M', 12), (1, 'L', 4), (1, 'XL', 15), (1, 'XXL', 7),
    (2, 'XS', 16), (2, 'S', 8), (2, 'M', 19), (2, 'L', 11), (2, 'XL', 3), (2, 'XXL', 14),
    (3, 'One size', 4),
    (4, '3', 11), (4, '4', 3), (4, '5', 14), (4, '6', 6), (4, '7', 17), (4, '8', 9), (4, '9', 20),
    (5, 'XS', 18), (5, 'S', 10), (5, 'M', 2), (5, 'L', 13), (5, 'XL', 5), (5, 'XXL', 16),
    (6, 'XS', 6), (6, 'S', 17), (6, 'M', 9), (6, 'L', 1), (6, 'XL', 12), (6, 'XXL', 4),
    (7, 'S', 12), (7, 'M', 4), (7, 'L', 15), (7, 'XL', 7), (7, 'XXL', 18),
    (8, 'XS', 20), (8, 'S', 12), (8, 'M', 4), (8, 'L', 15), (8, 'XL', 7), (8, 'XXL', 18),
    (9, '2-3Y', 8), (9, '4-5Y', 19), (9, '6-7Y', 11), (9, '8-9Y', 3), (9, '10-12Y', 14),
    (10, 'L', 15), (10, 'XL', 7), (10, 'XXL', 18);

-- Generated catalogue sizes and stock mirror createProduct() in catalogue.js.
INSERT IGNORE INTO product_variant (product_id, size_label, stock_quantity)
SELECT p.product_id, size_options.size_label,
       2 + MOD(p.product_id * 7 + (size_options.size_index - 1) * 11, 19)
FROM product AS p
JOIN subcategory AS sc ON sc.subcategory_id = p.subcategory_id
JOIN category AS c ON c.category_id = sc.category_id
CROSS JOIN JSON_TABLE(
    CASE
        WHEN c.category_name = 'Women' AND sc.subcategory_name = 'Shoes'
            THEN JSON_ARRAY('3', '4', '5', '6', '7', '8', '9')
        WHEN c.category_name = 'Women' AND sc.subcategory_name = 'Bags'
            THEN JSON_ARRAY('One size')
        WHEN c.category_name = 'Women'
            THEN JSON_ARRAY('XS', 'S', 'M', 'L', 'XL', 'XXL')
        WHEN c.category_name = 'Men' AND sc.subcategory_name = 'Shoes'
            THEN JSON_ARRAY('6', '7', '8', '9', '10', '11', '12')
        WHEN c.category_name = 'Men' AND sc.subcategory_name = 'Accessories'
            THEN JSON_ARRAY('One size')
        WHEN c.category_name = 'Men'
            THEN JSON_ARRAY('S', 'M', 'L', 'XL', 'XXL')
        WHEN c.category_name = 'Kids' AND sc.subcategory_name = 'Shoes'
            THEN JSON_ARRAY('10C', '11C', '12C', '13C', '1Y', '2Y', '3Y')
        WHEN c.category_name = 'Kids' AND sc.subcategory_name = 'Accessories'
            THEN JSON_ARRAY('One size')
        ELSE JSON_ARRAY('2-3Y', '4-5Y', '6-7Y', '8-9Y', '10-12Y')
    END,
    '$[*]' COLUMNS (
        size_index FOR ORDINALITY,
        size_label VARCHAR(20) PATH '$'
    )
) AS size_options
WHERE p.product_id BETWEEN 11 AND 100;
INSERT IGNORE INTO customer (customer_id, full_name, email, phone) VALUES
    (1, 'Alex Example', 'customer@example.com', '+27000000000');

INSERT IGNORE INTO customer_address
    (address_id, customer_id, address_label, address_line1, city, postal_code, country_code, is_default)
VALUES
    (1, 1, 'Home', '1 Example Street', 'Johannesburg', '2000', 'ZA', TRUE);

INSERT IGNORE INTO discount
    (discount_id, discount_code, discount_type, discount_value, is_active)
VALUES
    (1, 'SAVE10', 'percentage', 10.00, TRUE);

INSERT IGNORE INTO `orders`
    (order_id, order_reference, tracking_code, customer_id, address_id, discount_id, discount_code_at_purchase,
     order_status, shipping_method, currency, shipping_full_name, shipping_email, shipping_phone,
     shipping_address_line1, shipping_city, shipping_postal_code, shipping_country_code,
     subtotal, discount_amount, shipping_amount, priority_fee, total_amount, placed_at)
VALUES
    (1, 'AF-SAMPLE-1001', 'TRK-SAMPLE1001', 1, 1, 1, 'SAVE10',
     'confirmed', 'priority', 'ZAR', 'Alex Example', 'customer@example.com', '+27000000000',
     '1 Example Street', 'Johannesburg', '2000', 'ZA',
     898.00, 89.80, 80.82, 15.00, 904.02, '2026-10-01 10:30:00');

INSERT IGNORE INTO order_item
    (order_id, product_variant_id, product_name_at_purchase, size_label_at_purchase,
     quantity, unit_price_at_purchase)
SELECT 1, pv.product_variant_id, p.product_name, pv.size_label, sample.quantity, sample.unit_price
FROM (
    SELECT 1 AS product_id, 'M' AS size_label, 1 AS quantity, CAST(299.00 AS DECIMAL(10,2)) AS unit_price
    UNION ALL
    SELECT 4, '8', 1, CAST(599.00 AS DECIMAL(10,2))
) AS sample
JOIN product AS p ON p.product_id = sample.product_id
JOIN product_variant AS pv
    ON pv.product_id = sample.product_id AND pv.size_label = sample.size_label;

INSERT IGNORE INTO payment
    (payment_id, order_id, amount, currency, payment_method, payment_status,
     provider_name, transaction_reference, processed_at)
VALUES
    (1, 1, 904.02, 'ZAR', 'demo', 'mock_approved',
     'AllFit demo checkout', 'DEMO-TXN-1001', '2026-10-01 10:30:05');

INSERT IGNORE INTO product_review
    (review_id, product_id, customer_id, rating, review_title, review_text, is_approved)
VALUES
    (1, 1, 1, 5, 'Sample review', 'Fictional example review for database testing.', TRUE),
    (2, 4, 1, 4, 'Sample review', 'Fictional example review for database testing.', TRUE);

INSERT IGNORE INTO team (team_id, team_name, course_code, description) VALUES
    (1, 'INFS 222 Sample Team', 'INFS 222',
     'Fictional sample team data; replace these members with the actual assignment team.');

INSERT IGNORE INTO team_member (team_member_id, full_name, student_number, email) VALUES
    (1, 'Sample Member One', 'SAMPLE-001', 'member1@example.invalid'),
    (2, 'Sample Member Two', 'SAMPLE-002', 'member2@example.invalid'),
    (3, 'Sample Member Three', 'SAMPLE-003', 'member3@example.invalid'),
    (4, 'Sample Member Four', 'SAMPLE-004', 'member4@example.invalid'),
    (5, 'Sample Member Five', 'SAMPLE-005', 'member5@example.invalid');

INSERT IGNORE INTO team_membership (team_id, team_member_id, member_role) VALUES
    (1, 1, 'Member'),
    (1, 2, 'Member'),
    (1, 3, 'Member'),
    (1, 4, 'Member'),
    (1, 5, 'Member');

-- 1. Display all products seeded in the catalogue, their category and variants.
SELECT
    p.product_id,
    p.product_name,
    c.category_name,
    sc.subcategory_name,
    p.fit_label,
    p.current_price,
    COALESCE(
        GROUP_CONCAT(
            CONCAT(pv.size_label, ' (stock: ', pv.stock_quantity, ')')
            ORDER BY pv.product_variant_id SEPARATOR ', '
        ),
        'No variants'
    ) AS available_sizes
FROM product AS p
JOIN subcategory AS sc ON sc.subcategory_id = p.subcategory_id
JOIN category AS c ON c.category_id = sc.category_id
LEFT JOIN product_variant AS pv ON pv.product_id = p.product_id
WHERE p.is_active = TRUE
GROUP BY p.product_id, p.product_name, c.category_name, sc.subcategory_name,
         p.fit_label, p.current_price
ORDER BY p.product_id;

-- 2. Display a customer's orders. Set the input variable, then execute the
-- prepared statement; do not concatenate user input into SQL text.
SET @customer_email = 'customer@example.com';
PREPARE customer_orders_query FROM
'SELECT o.order_reference, o.placed_at, o.order_status, o.shipping_method,
        o.currency, o.subtotal, o.discount_amount, o.shipping_amount,
        o.priority_fee, o.total_amount
 FROM `orders` AS o
 JOIN customer AS c ON c.customer_id = o.customer_id
 WHERE c.email = ?
 ORDER BY o.placed_at DESC';
EXECUTE customer_orders_query USING @customer_email;
DEALLOCATE PREPARE customer_orders_query;

-- 3. Display each order item and calculate item subtotal and final order total.
SET @order_reference = 'AF-SAMPLE-1001';
PREPARE order_items_totals_query FROM
'WITH item_totals AS (
     SELECT order_id, SUM(line_total) AS calculated_items_subtotal
     FROM order_item
     GROUP BY order_id
 )
 SELECT o.order_reference, oi.product_name_at_purchase,
        oi.size_label_at_purchase, oi.quantity, oi.unit_price_at_purchase,
        oi.line_total, totals.calculated_items_subtotal,
        o.discount_amount, o.shipping_amount, o.priority_fee,
        ROUND(totals.calculated_items_subtotal - o.discount_amount
              + o.shipping_amount + o.priority_fee, 2) AS calculated_order_total,
        o.total_amount AS stored_order_total
 FROM `orders` AS o
 JOIN order_item AS oi ON oi.order_id = o.order_id
 JOIN item_totals AS totals ON totals.order_id = o.order_id
 WHERE o.order_reference = ?
 ORDER BY oi.order_item_id';
EXECUTE order_items_totals_query USING @order_reference;
DEALLOCATE PREPARE order_items_totals_query;

-- 4. Display payment and transaction details for an order reference.
SET @payment_order_reference = 'AF-SAMPLE-1001';
PREPARE payment_transaction_query FROM
'SELECT o.order_reference, p.payment_id, p.amount, p.currency,
        p.payment_method, p.payment_status, p.provider_name,
        p.transaction_reference, p.created_at, p.processed_at
 FROM payment AS p
 JOIN `orders` AS o ON o.order_id = p.order_id
 WHERE o.order_reference = ?
 ORDER BY p.created_at DESC';
EXECUTE payment_transaction_query USING @payment_order_reference;
DEALLOCATE PREPARE payment_transaction_query;

-- Accounts/customers visible to store operations; password hashes are intentionally excluded.
SELECT c.customer_id, c.full_name, c.email, c.phone, c.created_at,
       CASE WHEN ca.customer_id IS NULL THEN 'Guest checkout' ELSE 'Registered account' END AS account_type
FROM customer AS c
LEFT JOIN customer_account AS ca ON ca.customer_id = c.customer_id
ORDER BY c.created_at DESC, c.customer_id DESC;

-- Find an order using the tracking code shown on the checkout receipt.
SET @tracking_code = 'TRK-SAMPLE1001';
PREPARE tracking_status_query FROM
'SELECT order_reference, tracking_code, order_status, placed_at, total_amount
 FROM `orders`
 WHERE tracking_code = ?';
EXECUTE tracking_status_query USING @tracking_code;
DEALLOCATE PREPARE tracking_status_query;

-- 5. List members for a team name supplied by the user.
SET @team_name = 'INFS 222 Sample Team';
PREPARE team_members_query FROM
'SELECT t.team_name, t.course_code, tm.full_name, tm.student_number,
        tm.email, membership.member_role, membership.joined_at
 FROM team AS t
 JOIN team_membership AS membership ON membership.team_id = t.team_id
 JOIN team_member AS tm ON tm.team_member_id = membership.team_member_id
 WHERE t.team_name = ?
 ORDER BY tm.full_name';
EXECUTE team_members_query USING @team_name;
DEALLOCATE PREPARE team_members_query;
