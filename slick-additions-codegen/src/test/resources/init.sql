CREATE TABLE IF NOT EXISTS "colors" (
    "id"   BIGINT  NOT NULL PRIMARY KEY AUTO_INCREMENT,
    "name" VARCHAR NOT NULL
);
CREATE TABLE IF NOT EXISTS "people" (
    "id"          BIGINT  NOT NULL PRIMARY KEY AUTO_INCREMENT,
    "first"       VARCHAR NOT NULL,
    "last"        VARCHAR NOT NULL,
    "city"        VARCHAR NOT NULL DEFAULT 'New York',
    "date_joined" DATE    NOT NULL DEFAULT now(),
    "balance"     NUMERIC NOT NULL DEFAULT 0.0,
    "best_friend" BIGINT  NULL REFERENCES "people"("id") ON DELETE SET NULL,
    "col8"        FLOAT8  NULL,
    "col9"        BOOL    NULL,
    "col10"       INT     NULL,
    "col11"       INT     NULL,
    "col12"       INT     NULL,
    "col13"       INT     NULL,
    "col14"       INT     NULL,
    "col15"       INT     NULL,
    "col16"       INT     NULL,
    "col17"       INT     NULL,
    "col18"       INT     NULL,
    "col19"       INT     NULL,
    "col20"       INT     NULL,
    "col21"       INT     NULL,
    "col22"       INT     NULL,
    "col23"       INT     NULL,
    "col24"       INT     NULL
);
