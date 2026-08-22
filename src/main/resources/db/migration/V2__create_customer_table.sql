CREATE TABLE customer (
                          id BIGSERIAL PRIMARY KEY,

                          customer_code VARCHAR(50) NOT NULL UNIQUE,

                          customer_name VARCHAR(100) NOT NULL,

                          mobile_number VARCHAR(10) NOT NULL,

                          email VARCHAR(150),

                          address VARCHAR(500),

                          city VARCHAR(100),

                          state VARCHAR(100),

                          pincode VARCHAR(6),

                          remarks VARCHAR(500),

                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);