package com.peaka.jdbc;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;


public class PeakaDriverTest {
    private static final String QUERY = "SELECT SUM(\"amount\") AS total_amount\n" +
            "FROM \"stripe\".\"payment\".\"charges\"";
    //Follow instructions on how to create your api key: https://docs.peaka.com/how-to-guides/how-to-generate-api-keys
    //Connect sample data sets to then run the query above with sample data by running testQueryWithSampleData.
    private static final String PEAKA_API_KEY = "<Your API KEY>";

    @Test
    public void testQueryWithSampleData() {
        try {
            Class.forName("com.peaka.jdbc.PeakaDriver");
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        String url = "jdbc:peaka://dbc.peaka.studio:4567";
        Properties properties = new Properties();
        properties.setProperty("extraCredentials", "peakaKey:" + PEAKA_API_KEY);
        properties.setProperty("SSL", "true");

        try {
            Connection connection = DriverManager.getConnection(url, properties);
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(QUERY);
            while (rs.next()) {
                System.out.print("total_amount: " + rs.getInt("total_amount"));
            }
            stmt.close();
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
