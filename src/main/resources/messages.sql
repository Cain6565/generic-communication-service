CREATE TABLE messages (
                          id SERIAL PRIMARY KEY,
                          method VARCHAR(20),
                          url VARCHAR(255),
                          version VARCHAR(20),
                          headers JSONB,
                          body text,
                          sender VARCHAR(100),
                          group_id VARCHAR(100),
                          protocol VARCHAR(20),
                          status VARCHAR(20),
                          timestamp TIMESTAMP
);
