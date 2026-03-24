ALTER TABLE products ADD COLUMN thumbnail_url VARCHAR(500) NULL;

CREATE TABLE product_images (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    object_key  VARCHAR(500) NOT NULL,
    image_type  VARCHAR(20)  NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_product_image FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE CASCADE
);
