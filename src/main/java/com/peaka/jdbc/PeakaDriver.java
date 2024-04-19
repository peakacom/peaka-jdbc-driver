package com.peaka.jdbc;

import io.trino.jdbc.TrinoDriver;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PeakaDriver extends NonRegisteringPeakaDriver {
    static {
        try {
            DriverManager.registerDriver(new PeakaDriver());
            DriverManager.registerDriver(new TrinoDriver());
        } catch (SQLException e) {
            Logger.getLogger(PeakaDriver.class.getPackage().getName())
                    .log(Level.SEVERE, "Failed to register driver", e);
            throw new RuntimeException(e);
        }
    }
}
