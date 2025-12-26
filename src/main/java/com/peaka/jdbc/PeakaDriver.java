package com.peaka.jdbc;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * PeakaDriver is a driver that registers itself with the DriverManager.
 * It is used to connect to the Peaka database.
 */
public class PeakaDriver extends NonRegisteringPeakaDriver {
    static {
        try {
            DriverManager.registerDriver(new PeakaDriver());
        } catch (SQLException e) {
            Logger.getLogger(PeakaDriver.class.getPackage().getName())
                    .log(Level.SEVERE, "Failed to register Peaka driver", e);
            throw new RuntimeException(e);
        }
    }
}
