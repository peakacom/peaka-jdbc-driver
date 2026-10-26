package com.peaka.jdbc;

import com.peaka.jdbc.proxy.TrinoProxyFactory;
import io.trino.jdbc.TrinoDriver;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * NonRegisteringPeakaDriver is a driver that does not register itself with the DriverManager.
 * It is used to be a base for PeakaDriver.
 */
class NonRegisteringPeakaDriver implements Driver {

    private final Driver targetDriver;

    private static final String PEAKA_PREFIX = "jdbc:peaka:";
    private static final String TRINO_PREFIX = "jdbc:trino:";

    public NonRegisteringPeakaDriver() {
        this.targetDriver = new TrinoDriver();
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        String trinoUrl = url.replaceFirst(PEAKA_PREFIX, TRINO_PREFIX);

        Connection rawTrinoConnection = targetDriver.connect(trinoUrl, info);

        if (rawTrinoConnection == null) {
            return null;
        }

        return TrinoProxyFactory.wrap(rawTrinoConnection);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(PEAKA_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        if (url != null && url.startsWith(PEAKA_PREFIX)) {
            url = url.replaceFirst(PEAKA_PREFIX, TRINO_PREFIX);
        }
        return targetDriver.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 2;
    }

    @Override
    public boolean jdbcCompliant() {
        return targetDriver.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return targetDriver.getParentLogger();
    }
}