package io.github.gambitanimating;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Temporary: proves Java can reach SQL Server. Run it once, then delete it. */
public class ConnectionCheck {

    public static void main(String[] args) throws SQLException {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {

            rs.next();
            System.out.println("Connected. SELECT 1 returned " + rs.getInt(1));
        }
    }
}
