-- Central catalog only. Never copy the Android demonstration catalog into production.
CREATE TABLE app_metadata (
  key TEXT PRIMARY KEY NOT NULL,
  value TEXT NOT NULL
);
INSERT INTO app_metadata (key, value) VALUES ('schema_version', '1');

CREATE TABLE users (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'kasir' CHECK (role IN ('pemilik', 'kasir')),
  active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
  created_at TEXT NOT NULL
);

CREATE TABLE sessions (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(id),
  refresh_hash TEXT NOT NULL UNIQUE,
  device TEXT,
  expires_at TEXT NOT NULL,
  revoked INTEGER NOT NULL DEFAULT 0 CHECK (revoked IN (0, 1)),
  created_at TEXT NOT NULL
);
CREATE INDEX sessions_user_idx ON sessions (user_id);

CREATE TABLE vehicles (
  id TEXT PRIMARY KEY NOT NULL,
  brand TEXT NOT NULL,
  model TEXT NOT NULL,
  generation TEXT,
  year_from INTEGER,
  year_to INTEGER,
  facelift INTEGER NOT NULL DEFAULT 0 CHECK (facelift IN (0, 1)),
  attributes TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(attributes)),
  verified INTEGER NOT NULL DEFAULT 0 CHECK (verified IN (0, 1)),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  CHECK (year_from IS NULL OR year_to IS NULL OR year_from <= year_to)
);
CREATE INDEX vehicles_model_idx ON vehicles (model);
CREATE INDEX vehicles_updated_idx ON vehicles (updated_at);

CREATE TABLE part_categories (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  position TEXT,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);
CREATE INDEX categories_updated_idx ON part_categories (updated_at);

CREATE TABLE parts (
  id TEXT PRIMARY KEY NOT NULL,
  category_id TEXT REFERENCES part_categories(id),
  name TEXT NOT NULL,
  brand TEXT,
  part_number TEXT,
  is_original INTEGER NOT NULL DEFAULT 0 CHECK (is_original IN (0, 1)),
  photo_key TEXT,
  notes TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);
CREATE INDEX parts_number_idx ON parts (part_number);
CREATE INDEX parts_name_idx ON parts (name);
CREATE INDEX parts_category_idx ON parts (category_id);
CREATE INDEX parts_updated_idx ON parts (updated_at);

CREATE TABLE fitment_groups (
  id TEXT PRIMARY KEY NOT NULL,
  category_id TEXT NOT NULL REFERENCES part_categories(id),
  label TEXT,
  notes TEXT,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);
CREATE INDEX groups_category_idx ON fitment_groups (category_id);
CREATE INDEX groups_updated_idx ON fitment_groups (updated_at);

CREATE TABLE group_parts (
  group_id TEXT NOT NULL REFERENCES fitment_groups(id),
  part_id TEXT NOT NULL REFERENCES parts(id),
  status TEXT NOT NULL DEFAULT 'mungkin' CHECK (status IN ('pasti', 'mungkin', 'tidak_cocok')),
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  PRIMARY KEY (group_id, part_id)
);
CREATE INDEX group_parts_part_idx ON group_parts (part_id);
CREATE INDEX group_parts_updated_idx ON group_parts (updated_at);

CREATE TABLE sources (
  id TEXT PRIMARY KEY NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('internet', 'katalog', 'toko')),
  url TEXT,
  note TEXT,
  fetched_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);
CREATE INDEX sources_updated_idx ON sources (updated_at);

CREATE TABLE fitments (
  id TEXT PRIMARY KEY NOT NULL,
  group_id TEXT NOT NULL REFERENCES fitment_groups(id),
  vehicle_id TEXT NOT NULL REFERENCES vehicles(id),
  year_from INTEGER,
  year_to INTEGER,
  constraints TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(constraints)),
  status TEXT NOT NULL DEFAULT 'mungkin' CHECK (status IN ('pasti', 'mungkin', 'tidak_cocok')),
  source_id TEXT REFERENCES sources(id),
  confirmed_by TEXT REFERENCES users(id),
  confirmed_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  CHECK (year_from IS NULL OR year_to IS NULL OR year_from <= year_to)
);
CREATE INDEX fitments_vehicle_idx ON fitments (vehicle_id, year_from, year_to);
CREATE INDEX fitments_group_idx ON fitments (group_id);
CREATE INDEX fitments_source_idx ON fitments (source_id);
CREATE INDEX fitments_confirmed_by_idx ON fitments (confirmed_by);
CREATE INDEX fitments_updated_idx ON fitments (updated_at);

CREATE TABLE confirmations (
  id TEXT PRIMARY KEY NOT NULL,
  fitment_id TEXT NOT NULL REFERENCES fitments(id),
  user_id TEXT NOT NULL REFERENCES users(id),
  result TEXT NOT NULL CHECK (result IN ('cocok', 'tidak_cocok')),
  note TEXT,
  created_at TEXT NOT NULL
);
CREATE INDEX confirmations_fitment_idx ON confirmations (fitment_id);
CREATE INDEX confirmations_user_idx ON confirmations (user_id);

CREATE TABLE search_synonyms (
  term TEXT PRIMARY KEY NOT NULL,
  canonical TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);
CREATE INDEX synonyms_updated_idx ON search_synonyms (updated_at);

CREATE TABLE change_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  table_name TEXT NOT NULL,
  record_id TEXT NOT NULL,
  action TEXT NOT NULL,
  old_value TEXT,
  new_value TEXT,
  user_id TEXT REFERENCES users(id),
  created_at TEXT NOT NULL
);
CREATE INDEX change_log_user_idx ON change_log (user_id);

CREATE TABLE usage_counters (
  key TEXT NOT NULL,
  window TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0 CHECK (count >= 0),
  PRIMARY KEY (key, window)
);

-- Credentials and sessions never enter the mobile catalog change stream.
-- Composite group_parts keys are encoded losslessly as a JSON array.
CREATE TABLE sync_log (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  table_name TEXT NOT NULL CHECK (table_name IN (
    'vehicles', 'part_categories', 'parts', 'fitment_groups',
    'group_parts', 'sources', 'fitments', 'confirmations', 'search_synonyms'
  )),
  record_id TEXT NOT NULL,
  action TEXT NOT NULL CHECK (action IN ('insert', 'update', 'delete')),
  changed_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX sync_log_record_idx ON sync_log (table_name, record_id, seq);
CREATE INDEX sync_log_changed_idx ON sync_log (changed_at);

CREATE TRIGGER vehicles_sync_insert AFTER INSERT ON vehicles BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('vehicles', NEW.id, 'insert');
END;
CREATE TRIGGER vehicles_sync_update AFTER UPDATE ON vehicles BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('vehicles', NEW.id, 'update');
END;
CREATE TRIGGER vehicles_sync_delete AFTER DELETE ON vehicles BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('vehicles', OLD.id, 'delete');
END;

CREATE TRIGGER part_categories_sync_insert AFTER INSERT ON part_categories BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('part_categories', NEW.id, 'insert');
END;
CREATE TRIGGER part_categories_sync_update AFTER UPDATE ON part_categories BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('part_categories', NEW.id, 'update');
END;
CREATE TRIGGER part_categories_sync_delete AFTER DELETE ON part_categories BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('part_categories', OLD.id, 'delete');
END;

CREATE TRIGGER parts_sync_insert AFTER INSERT ON parts BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('parts', NEW.id, 'insert');
END;
CREATE TRIGGER parts_sync_update AFTER UPDATE ON parts BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('parts', NEW.id, 'update');
END;
CREATE TRIGGER parts_sync_delete AFTER DELETE ON parts BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('parts', OLD.id, 'delete');
END;

CREATE TRIGGER fitment_groups_sync_insert AFTER INSERT ON fitment_groups BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('fitment_groups', NEW.id, 'insert');
END;
CREATE TRIGGER fitment_groups_sync_update AFTER UPDATE ON fitment_groups BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('fitment_groups', NEW.id, 'update');
END;
CREATE TRIGGER fitment_groups_sync_delete AFTER DELETE ON fitment_groups BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('fitment_groups', OLD.id, 'delete');
END;

CREATE TRIGGER group_parts_sync_insert AFTER INSERT ON group_parts BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('group_parts', json_array(NEW.group_id, NEW.part_id), 'insert');
END;
CREATE TRIGGER group_parts_sync_update AFTER UPDATE ON group_parts BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('group_parts', json_array(NEW.group_id, NEW.part_id), 'update');
END;
CREATE TRIGGER group_parts_sync_delete AFTER DELETE ON group_parts BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('group_parts', json_array(OLD.group_id, OLD.part_id), 'delete');
END;

CREATE TRIGGER sources_sync_insert AFTER INSERT ON sources BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('sources', NEW.id, 'insert');
END;
CREATE TRIGGER sources_sync_update AFTER UPDATE ON sources BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('sources', NEW.id, 'update');
END;
CREATE TRIGGER sources_sync_delete AFTER DELETE ON sources BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('sources', OLD.id, 'delete');
END;

CREATE TRIGGER fitments_sync_insert AFTER INSERT ON fitments BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('fitments', NEW.id, 'insert');
END;
CREATE TRIGGER fitments_sync_update AFTER UPDATE ON fitments BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('fitments', NEW.id, 'update');
END;
CREATE TRIGGER fitments_sync_delete AFTER DELETE ON fitments BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('fitments', OLD.id, 'delete');
END;

CREATE TRIGGER confirmations_sync_insert AFTER INSERT ON confirmations BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('confirmations', NEW.id, 'insert');
END;
CREATE TRIGGER confirmations_sync_update AFTER UPDATE ON confirmations BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('confirmations', NEW.id, 'update');
END;
CREATE TRIGGER confirmations_sync_delete AFTER DELETE ON confirmations BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('confirmations', OLD.id, 'delete');
END;

CREATE TRIGGER search_synonyms_sync_insert AFTER INSERT ON search_synonyms BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('search_synonyms', NEW.term, 'insert');
END;
CREATE TRIGGER search_synonyms_sync_update AFTER UPDATE ON search_synonyms BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('search_synonyms', NEW.term, 'update');
END;
CREATE TRIGGER search_synonyms_sync_delete AFTER DELETE ON search_synonyms BEGIN
  INSERT INTO sync_log (table_name, record_id, action) VALUES ('search_synonyms', OLD.term, 'delete');
END;
