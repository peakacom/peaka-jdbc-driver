package com.peaka.jdbc;

import io.trino.jdbc.TrinoDriverUri;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class NonRegisteringPeakaDriver implements Driver {
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        url = url.replace("jdbc:peaka:", "jdbc:trino:");
        return DriverManager.getConnection(url,info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        if (url == null) {
            throw new SQLException("Invalid url");
        }
        return url.startsWith("jdbc:peaka:");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        url = url.replace("jdbc:peaka:", "jdbc:trino");
        return TrinoDriverUri.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }
}
