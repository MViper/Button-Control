package viper;

import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MySQLStorage {
    private final ButtonControl plugin;

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;
    private final boolean enabled;

    private Connection connection;

    public MySQLStorage(ButtonControl plugin) {
        this.plugin = plugin;

        FileConfiguration cfg = plugin.getConfigManager().getConfig();
        this.enabled = cfg.getBoolean("mysql.enabled", false);
        this.host = cfg.getString("mysql.host", "127.0.0.1");
        this.port = cfg.getInt("mysql.port", 3306);
        this.database = cfg.getString("mysql.database", "buttoncontrol");
        this.user = cfg.getString("mysql.user", "root");
        this.password = cfg.getString("mysql.password", "");
    }

    public boolean initialize() {
        if (!enabled) return false;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            getConnection();
            createTables();
            plugin.getLogger().info("MySQL aktiviert.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("MySQL konnte nicht initialisiert werden, verwende data.yml: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection != null && connection.isValid(2)) {
            return connection;
        }
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
            + "?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";
        connection = DriverManager.getConnection(url, user, password);
        return connection;
    }

    private void createTables() throws SQLException {
        try (Statement st = getConnection().createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS bc_controllers ("
                + "location VARCHAR(128) PRIMARY KEY,"
                + "owner_uuid VARCHAR(36) NOT NULL,"
                + "button_id VARCHAR(64) NOT NULL"
                + ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS bc_button_connections ("
                + "owner_uuid VARCHAR(36) NOT NULL,"
                + "button_id VARCHAR(64) NOT NULL,"
                + "block_location VARCHAR(128) NOT NULL,"
                + "PRIMARY KEY (button_id, block_location)"
                + ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS bc_controller_names ("
                + "button_id VARCHAR(64) PRIMARY KEY,"
                + "name VARCHAR(64)"
                + ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS bc_schedules ("
                + "button_id VARCHAR(64) PRIMARY KEY,"
                + "open_time BIGINT,"
                + "close_time BIGINT"
                + ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS bc_trust ("
                + "button_id VARCHAR(64) NOT NULL,"
                + "target_uuid VARCHAR(36) NOT NULL,"
                + "PRIMARY KEY (button_id, target_uuid)"
                + ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS bc_public_status ("
                + "button_id VARCHAR(64) PRIMARY KEY,"
                + "is_public BOOLEAN NOT NULL"
                + ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS bc_player_settings ("
                + "player_uuid VARCHAR(36) PRIMARY KEY,"
                + "instrument VARCHAR(32)"
                + ")");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS bc_motion_sensors ("
                + "location VARCHAR(128) PRIMARY KEY,"
                + "radius DOUBLE,"
                + "delay_ms BIGINT"
                + ")");
        }
    }

    public boolean canAccess(String buttonId, UUID playerUUID) {
        return isPublic(buttonId) || isOwner(buttonId, playerUUID) || isTrusted(buttonId, playerUUID);
    }

    public boolean isOwner(String buttonId, UUID playerUUID) {
        String uuid = playerUUID.toString();
        String q1 = "SELECT 1 FROM bc_controllers WHERE button_id = ? AND owner_uuid = ? LIMIT 1";
        String q2 = "SELECT 1 FROM bc_button_connections WHERE button_id = ? AND owner_uuid = ? LIMIT 1";
        try (PreparedStatement ps1 = getConnection().prepareStatement(q1);
             PreparedStatement ps2 = getConnection().prepareStatement(q2)) {
            ps1.setString(1, buttonId);
            ps1.setString(2, uuid);
            try (ResultSet rs = ps1.executeQuery()) {
                if (rs.next()) return true;
            }
            ps2.setString(1, buttonId);
            ps2.setString(2, uuid);
            try (ResultSet rs = ps2.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL isOwner Fehler: " + e.getMessage());
            return false;
        }
    }

    public String getButtonIdForLocation(String location) {
        String q = "SELECT button_id FROM bc_controllers WHERE location = ? LIMIT 1";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, location);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL getButtonIdForLocation Fehler: " + e.getMessage());
            return null;
        }
    }

    public void registerController(String location, UUID ownerUUID, String buttonId) {
        String q = "INSERT INTO bc_controllers (location, owner_uuid, button_id) VALUES (?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE owner_uuid = VALUES(owner_uuid), button_id = VALUES(button_id)";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, location);
            ps.setString(2, ownerUUID.toString());
            ps.setString(3, buttonId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL registerController Fehler: " + e.getMessage());
        }
    }

    public void removeController(String location) {
        // buttonId vor dem Löschen ermitteln
        String buttonId = getButtonIdForLocation(location);
        String q = "DELETE FROM bc_controllers WHERE location = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, location);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL removeController Fehler: " + e.getMessage());
        }
        // Alle zugehörigen Tabellen bereinigen
        if (buttonId != null) {
            deleteButtonData(buttonId);
        }
        removeMotionSensorSettings(location);
    }

    private void deleteButtonData(String buttonId) {
        String[] queries = {
            "DELETE FROM bc_controller_names WHERE button_id = ?",
            "DELETE FROM bc_public_status WHERE button_id = ?",
            "DELETE FROM bc_trust WHERE button_id = ?",
            "DELETE FROM bc_schedules WHERE button_id = ?",
            "DELETE FROM bc_button_connections WHERE button_id = ?"
        };
        for (String q : queries) {
            try (PreparedStatement ps = getConnection().prepareStatement(q)) {
                ps.setString(1, buttonId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("MySQL deleteButtonData Fehler: " + e.getMessage());
            }
        }
    }

    public String getButtonIdForPlacedController(String location) {
        return getButtonIdForLocation(location);
    }

    public List<String> getAllPlacedControllers() {
        List<String> result = new ArrayList<>();
        String q = "SELECT location FROM bc_controllers";
        try (PreparedStatement ps = getConnection().prepareStatement(q);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(rs.getString(1));
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL getAllPlacedControllers Fehler: " + e.getMessage());
        }
        return result;
    }

    public void setConnectedBlocks(String playerUUID, String buttonId, List<String> blocks) {
        String del = "DELETE FROM bc_button_connections WHERE owner_uuid = ? AND button_id = ?";
        String ins = "INSERT INTO bc_button_connections (owner_uuid, button_id, block_location) VALUES (?, ?, ?)";
        try (PreparedStatement psDel = getConnection().prepareStatement(del);
             PreparedStatement psIns = getConnection().prepareStatement(ins)) {
            psDel.setString(1, playerUUID);
            psDel.setString(2, buttonId);
            psDel.executeUpdate();

            for (String block : blocks) {
                psIns.setString(1, playerUUID);
                psIns.setString(2, buttonId);
                psIns.setString(3, block);
                psIns.addBatch();
            }
            psIns.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL setConnectedBlocks Fehler: " + e.getMessage());
        }
    }

    public List<String> getConnectedBlocks(String buttonId) {
        List<String> result = new ArrayList<>();
        String q = "SELECT block_location FROM bc_button_connections WHERE button_id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL getConnectedBlocks Fehler: " + e.getMessage());
        }
        return result;
    }

    public boolean removeFromAllConnectedBlocks(String locStr) {
        String q = "DELETE FROM bc_button_connections WHERE block_location = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, locStr);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL removeFromAllConnectedBlocks Fehler: " + e.getMessage());
            return false;
        }
    }

    public void setControllerName(String buttonId, String name) {
        String q = "INSERT INTO bc_controller_names (button_id, name) VALUES (?, ?)"
            + " ON DUPLICATE KEY UPDATE name = VALUES(name)";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL setControllerName Fehler: " + e.getMessage());
        }
    }

    public String getControllerName(String buttonId) {
        String q = "SELECT name FROM bc_controller_names WHERE button_id = ? LIMIT 1";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL getControllerName Fehler: " + e.getMessage());
            return null;
        }
    }

    public void setScheduleOpenTime(String buttonId, long ticks) {
        String q = "INSERT INTO bc_schedules (button_id, open_time, close_time) VALUES (?, ?, COALESCE((SELECT close_time FROM bc_schedules WHERE button_id = ?), -1))"
            + " ON DUPLICATE KEY UPDATE open_time = VALUES(open_time)";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            ps.setLong(2, ticks);
            ps.setString(3, buttonId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL setScheduleOpenTime Fehler: " + e.getMessage());
        }
    }

    public long getScheduleOpenTime(String buttonId) {
        String q = "SELECT open_time FROM bc_schedules WHERE button_id = ? LIMIT 1";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL getScheduleOpenTime Fehler: " + e.getMessage());
            return -1;
        }
    }

    public void setScheduleCloseTime(String buttonId, long ticks) {
        String q = "INSERT INTO bc_schedules (button_id, open_time, close_time) VALUES (?, COALESCE((SELECT open_time FROM bc_schedules WHERE button_id = ?), -1), ?)"
            + " ON DUPLICATE KEY UPDATE close_time = VALUES(close_time)";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            ps.setString(2, buttonId);
            ps.setLong(3, ticks);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL setScheduleCloseTime Fehler: " + e.getMessage());
        }
    }

    public long getScheduleCloseTime(String buttonId) {
        String q = "SELECT close_time FROM bc_schedules WHERE button_id = ? LIMIT 1";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL getScheduleCloseTime Fehler: " + e.getMessage());
            return -1;
        }
    }

    public void clearSchedule(String buttonId) {
        String q = "DELETE FROM bc_schedules WHERE button_id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL clearSchedule Fehler: " + e.getMessage());
        }
    }

    public void addTrustedPlayer(String buttonId, UUID targetUUID) {
        String q = "INSERT IGNORE INTO bc_trust (button_id, target_uuid) VALUES (?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            ps.setString(2, targetUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL addTrustedPlayer Fehler: " + e.getMessage());
        }
    }

    public void removeTrustedPlayer(String buttonId, UUID targetUUID) {
        String q = "DELETE FROM bc_trust WHERE button_id = ? AND target_uuid = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            ps.setString(2, targetUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL removeTrustedPlayer Fehler: " + e.getMessage());
        }
    }

    public void setPublic(String buttonId, boolean isPublic) {
        String q = "INSERT INTO bc_public_status (button_id, is_public) VALUES (?, ?)"
            + " ON DUPLICATE KEY UPDATE is_public = VALUES(is_public)";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            ps.setBoolean(2, isPublic);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL setPublic Fehler: " + e.getMessage());
        }
    }

    public boolean isPublic(String buttonId) {
        String q = "SELECT is_public FROM bc_public_status WHERE button_id = ? LIMIT 1";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL isPublic Fehler: " + e.getMessage());
            return false;
        }
    }

    public void setPlayerInstrument(UUID playerUUID, String instrument) {
        String q = "INSERT INTO bc_player_settings (player_uuid, instrument) VALUES (?, ?)"
            + " ON DUPLICATE KEY UPDATE instrument = VALUES(instrument)";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, instrument);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL setPlayerInstrument Fehler: " + e.getMessage());
        }
    }

    public String getPlayerInstrument(UUID playerUUID) {
        String q = "SELECT instrument FROM bc_player_settings WHERE player_uuid = ? LIMIT 1";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL getPlayerInstrument Fehler: " + e.getMessage());
            return null;
        }
    }

    public void setMotionSensorRadius(String location, double radius) {
        String q = "INSERT INTO bc_motion_sensors (location, radius, delay_ms) VALUES (?, ?, COALESCE((SELECT delay_ms FROM bc_motion_sensors WHERE location = ?), -1))"
            + " ON DUPLICATE KEY UPDATE radius = VALUES(radius)";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, location);
            ps.setDouble(2, radius);
            ps.setString(3, location);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL setMotionSensorRadius Fehler: " + e.getMessage());
        }
    }

    public double getMotionSensorRadius(String location) {
        String q = "SELECT radius FROM bc_motion_sensors WHERE location = ? LIMIT 1";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, location);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : -1;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL getMotionSensorRadius Fehler: " + e.getMessage());
            return -1;
        }
    }

    public void setMotionSensorDelay(String location, long delay) {
        String q = "INSERT INTO bc_motion_sensors (location, radius, delay_ms) VALUES (?, COALESCE((SELECT radius FROM bc_motion_sensors WHERE location = ?), -1), ?)"
            + " ON DUPLICATE KEY UPDATE delay_ms = VALUES(delay_ms)";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, location);
            ps.setString(2, location);
            ps.setLong(3, delay);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL setMotionSensorDelay Fehler: " + e.getMessage());
        }
    }

    public long getMotionSensorDelay(String location) {
        String q = "SELECT delay_ms FROM bc_motion_sensors WHERE location = ? LIMIT 1";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, location);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL getMotionSensorDelay Fehler: " + e.getMessage());
            return -1;
        }
    }

    public void removeMotionSensorSettings(String location) {
        String q = "DELETE FROM bc_motion_sensors WHERE location = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, location);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL removeMotionSensorSettings Fehler: " + e.getMessage());
        }
    }

    private boolean isTrusted(String buttonId, UUID playerUUID) {
        String q = "SELECT 1 FROM bc_trust WHERE button_id = ? AND target_uuid = ? LIMIT 1";
        try (PreparedStatement ps = getConnection().prepareStatement(q)) {
            ps.setString(1, buttonId);
            ps.setString(2, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL isTrusted Fehler: " + e.getMessage());
            return false;
        }
    }
}
