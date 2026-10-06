package dev.plattnericus.cases.storage;

/** The few statements that differ between SQLite and MySQL/MariaDB. */
public interface SqlDialect {

    String insertIgnore();

    /** Upsert for a table whose primary key is (owner, slot). */
    String upsertEquipped(String table);

    String autoText(int length);

    SqlDialect SQLITE = new SqlDialect() {
        @Override
        public String insertIgnore() {
            return "INSERT OR IGNORE INTO ";
        }

        @Override
        public String upsertEquipped(String table) {
            return "INSERT INTO " + table + " (owner, slot, instance_id) VALUES (?, ?, ?) "
                    + "ON CONFLICT(owner, slot) DO UPDATE SET instance_id = excluded.instance_id";
        }

        @Override
        public String autoText(int length) {
            return "TEXT";
        }
    };

    SqlDialect MYSQL = new SqlDialect() {
        @Override
        public String insertIgnore() {
            return "INSERT IGNORE INTO ";
        }

        @Override
        public String upsertEquipped(String table) {
            return "INSERT INTO " + table + " (owner, slot, instance_id) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE instance_id = VALUES(instance_id)";
        }

        @Override
        public String autoText(int length) {
            return "VARCHAR(" + length + ")";
        }
    };
}
