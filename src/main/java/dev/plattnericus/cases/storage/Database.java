package dev.plattnericus.cases.storage;

import dev.plattnericus.cases.config.PluginSettings;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the JDBC connection. Every statement runs on one dedicated thread, which serialises all
 * writes (no lost updates, no SQLite lock contention) and keeps the server thread free.
 * Both drivers ship with Paper; no extra dependency is shaded.
 */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private static final int SCHEMA_VERSION = 4;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MCCases-Database");
        t.setDaemon(false);
        return t;
    });
    private final PluginSettings.Storage settings;
    private final File dataFolder;
    private final Logger logger;
    private final SqlDialect dialect;
    private final String prefix;
    private Connection connection;

    public Database(PluginSettings.Storage settings, File dataFolder, Logger logger) {
        this.settings = settings;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.dialect = isMySql() ? SqlDialect.MYSQL : SqlDialect.SQLITE;
        this.prefix = settings.tablePrefix();
    }

    private boolean isMySql() {
        return settings.type().equals("mysql") || settings.type().equals("mariadb");
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public String table(String name) {
        return prefix + name;
    }

    /** Connects and migrates synchronously; called once during enable. */
    public void open() throws SQLException {
        try {
            run(c -> {
                migrate(c);
                return null;
            }).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof SQLException sql) {
                throw sql;
            }
            throw e;
        }
    }

    private Connection connection() throws SQLException {
        if (connection != null && !connection.isClosed() && (!isMySql() || connection.isValid(2))) {
            return connection;
        }
        if (isMySql()) {
            String url = "jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.database()
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true";
            connection = DriverManager.getConnection(url, settings.user(), settings.password());
        } else {
            File file = new File(dataFolder, settings.sqliteFile());
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                // FULL: an acknowledged reward survives power loss, not only process crashes
                s.execute("PRAGMA synchronous=FULL");
                s.execute("PRAGMA busy_timeout=5000");
            }
        }
        connection.setAutoCommit(true);
        return connection;
    }

    public <T> CompletableFuture<T> run(Work<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.run(connection());
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /** Runs {@code work} inside a transaction; rolls back on any failure. */
    public <T> CompletableFuture<T> transaction(Work<T> work) {
        return run(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        });
    }

    private void migrate(Connection c) throws SQLException {
        String text = dialect.autoText(255);
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("schema") + " (version INTEGER NOT NULL)");
            int version = 0;
            try (var rs = s.executeQuery("SELECT version FROM " + table("schema"))) {
                if (rs.next()) {
                    version = rs.getInt(1);
                }
            }
            if (version > SCHEMA_VERSION) throw new SQLException("Database schema is newer than this plugin: " + version);
            if (version < 1) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("skins") + " ("
                        + "instance_id CHAR(36) NOT NULL PRIMARY KEY,"
                        + "owner CHAR(36) NOT NULL,"
                        + "skin_id VARCHAR(96) NOT NULL,"
                        + "float_value DOUBLE NOT NULL,"
                        + "pattern INTEGER NOT NULL,"
                        + "wear_seed BIGINT NOT NULL,"
                        + "stattrak INTEGER NOT NULL,"
                        + "stattrak_kills INTEGER NOT NULL DEFAULT 0,"
                        + "variant VARCHAR(64),"
                        + "variant_name VARCHAR(64),"
                        + "classification " + text + ","
                        + "class_tier INTEGER NOT NULL DEFAULT 0,"
                        + "class_color INTEGER NOT NULL DEFAULT 0,"
                        + "fade_percent DOUBLE,"
                        + "source VARCHAR(96),"
                        + "origin VARCHAR(16) NOT NULL,"
                        + "created_at BIGINT NOT NULL,"
                        + "favorite INTEGER NOT NULL DEFAULT 0,"
                        + "status VARCHAR(16) NOT NULL)");
                ensureIndex(c, "skins_owner", "skins", "owner, status");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("equipped") + " ("
                        + "owner CHAR(36) NOT NULL,"
                        + "slot VARCHAR(32) NOT NULL,"
                        + "instance_id CHAR(36) NOT NULL,"
                        + "PRIMARY KEY (owner, slot))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("openings") + " ("
                        + "opening_id CHAR(36) NOT NULL PRIMARY KEY,"
                        + "owner CHAR(36) NOT NULL,"
                        + "owner_name VARCHAR(16),"
                        + "case_id VARCHAR(96) NOT NULL,"
                        + "instance_id CHAR(36) NOT NULL,"
                        + "skin_id VARCHAR(96) NOT NULL,"
                        + "rarity VARCHAR(32) NOT NULL,"
                        + "float_value DOUBLE NOT NULL,"
                        + "pattern INTEGER NOT NULL,"
                        + "stattrak INTEGER NOT NULL,"
                        + "test INTEGER NOT NULL,"
                        + "opened_at BIGINT NOT NULL)");
                ensureIndex(c, "openings_owner", "openings", "owner, opened_at");
                s.executeUpdate("DELETE FROM " + table("schema"));
                s.executeUpdate("INSERT INTO " + table("schema") + " (version) VALUES (1)");
            }
            if (version < 2) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("market_listings") + " ("
                        + "listing_id CHAR(36) NOT NULL PRIMARY KEY, instance_id CHAR(36) NOT NULL UNIQUE,"
                        + "seller CHAR(36) NOT NULL, seller_name VARCHAR(16) NOT NULL,"
                        + "price BIGINT NOT NULL, created_at BIGINT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("wallets") + " ("
                        + "owner CHAR(36) NOT NULL PRIMARY KEY, balance BIGINT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("commerce_log") + " ("
                        + "event_id CHAR(36) NOT NULL PRIMARY KEY, kind VARCHAR(16) NOT NULL,"
                        + "actor CHAR(36) NOT NULL, counterparty CHAR(36), amount BIGINT NOT NULL,"
                        + "details TEXT NOT NULL, created_at BIGINT NOT NULL)");
                s.executeUpdate("DELETE FROM " + table("schema"));
                s.executeUpdate("INSERT INTO " + table("schema") + " (version) VALUES (2)");
            }
            if (version < 3) {
                // DDL is restartable even on MySQL (which implicitly commits DDL).
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("legacy_market_listings")
                        + " (listing_id CHAR(36) PRIMARY KEY, instance_id CHAR(36), seller CHAR(36), seller_name VARCHAR(16), price BIGINT, created_at BIGINT)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("market_payments")
                        + " (tx_id CHAR(36) PRIMARY KEY, listing_id CHAR(36) NOT NULL, instance_id CHAR(36) NOT NULL, buyer CHAR(36) NOT NULL, seller CHAR(36) NOT NULL, amount BIGINT NOT NULL, state VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("market_reservations")
                        + " (listing_id CHAR(36) PRIMARY KEY, tx_id CHAR(36) NOT NULL UNIQUE)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("emerald_claims")
                        + " (claim_id CHAR(36) PRIMARY KEY, owner CHAR(36) NOT NULL, amount BIGINT NOT NULL, remaining BIGINT NOT NULL, reserved_delivery CHAR(36), created_at BIGINT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("emerald_deliveries")
                        + " (delivery_id CHAR(36) PRIMARY KEY, claim_id CHAR(36) NOT NULL, owner CHAR(36) NOT NULL, amount BIGINT NOT NULL, state VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("trade_contracts")
                        + " (contract_id CHAR(36) PRIMARY KEY, owner CHAR(36) NOT NULL, instance_id CHAR(36) NOT NULL UNIQUE, inputs TEXT NOT NULL, announced INTEGER NOT NULL DEFAULT 0, created_at BIGINT NOT NULL)");
                // Never reinterpret existing Coin prices as item prices. Archive and return the skins.
                c.setAutoCommit(false);
                try {
                    s.executeUpdate(dialect.insertIgnore() + table("legacy_market_listings")
                            + " SELECT * FROM " + table("market_listings"));
                    s.executeUpdate("UPDATE " + table("skins") + " SET status='OWNED' WHERE status='LISTED' AND instance_id IN (SELECT instance_id FROM " + table("market_listings") + ")");
                    s.executeUpdate("DELETE FROM " + table("market_listings"));
                    s.executeUpdate("DELETE FROM " + table("schema"));
                    s.executeUpdate("INSERT INTO " + table("schema") + " (version) VALUES (3)");
                    c.commit();
                } catch (SQLException error) { c.rollback(); throw error; }
                finally { c.setAutoCommit(true); }
            }

            if (version < 4) {
                // MySQL DDL commits implicitly: detect the column so interrupted upgrades retry safely.
                boolean present;
                try (var columns = c.getMetaData().getColumns(c.getCatalog(), null, table("skins"), "traded")) {
                    present = columns.next();
                }
                if (!present) s.executeUpdate("ALTER TABLE " + table("skins") + " ADD COLUMN traded INTEGER NOT NULL DEFAULT 0");
                c.setAutoCommit(false);
                try {
                    // Recover only skin IDs recorded by the actual direct-trade audit, never actor IDs.
                    var pattern = java.util.regex.Pattern.compile("instance=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
                    try (var update = c.prepareStatement("UPDATE " + table("skins") + " SET traded=1 WHERE instance_id=?");
                         var rs = s.executeQuery("SELECT details FROM " + table("commerce_log") + " WHERE kind='TRADE'")) {
                        int pending = 0;
                        while (rs.next()) {
                            var matches = pattern.matcher(rs.getString(1));
                            while (matches.find()) {
                                update.setString(1, matches.group(1)); update.addBatch();
                                if (++pending == 256) { update.executeBatch(); update.clearBatch(); pending = 0; }
                            }
                        }
                        if (pending > 0) update.executeBatch();
                    }
                    s.executeUpdate("DELETE FROM " + table("schema"));
                    s.executeUpdate("INSERT INTO " + table("schema") + " (version) VALUES (4)");
                    c.commit();
                } catch (SQLException error) { c.rollback(); throw error; }
                finally { c.setAutoCommit(true); }
            }

            ensureIndex(c, "market_payment_owner", "market_payments", "buyer, state");
            ensureIndex(c, "emerald_claim_owner", "emerald_claims", "owner, remaining");
            ensureIndex(c, "emerald_delivery_owner", "emerald_deliveries", "owner, state");
        }
    }
    private void ensureIndex(Connection c, String index, String table, String columns) throws SQLException {
        if (isMySql()) {
            try (var rs = c.getMetaData().getIndexInfo(c.getCatalog(), null, table(table), false, false)) {
                while (rs.next()) if (table(index).equalsIgnoreCase(rs.getString("INDEX_NAME"))) return;
            }
        }
        try (Statement s = c.createStatement()) { s.executeUpdate("CREATE INDEX " + (isMySql() ? "" : "IF NOT EXISTS ") + table(index) + " ON " + table(table) + " (" + columns + ")"); }
    }

    /**
     * Waits for queued writes, then closes the connection. Called on disable so no acknowledged
     * write is lost on shutdown.
     */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("Database writes did not finish within 15s; remaining tasks are dropped.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Closing the database failed", e);
        }
    }
}
