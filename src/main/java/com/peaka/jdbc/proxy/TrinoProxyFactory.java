package com.peaka.jdbc.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

public class TrinoProxyFactory {

    /**
     * Trino's arbitrary-precision decimal (480+), e.g. a plain Oracle NUMBER read with peaka.number-type=number.
     * The Trino driver reports it as JDBC OTHER holding a BigDecimal kept without trailing zeros, so a BI
     * tool sees an object rather than a number and prints 20000 as "2E+4". This driver reports it as
     * NUMERIC with plain BigDecimal values, the way Oracle's own driver reports a plain NUMBER.
     */
    private static final String NUMBER_TYPE = "number";

    /**
     * Calls the wrapped driver and lets its own exception through. Reflection wraps it in an
     * InvocationTargetException, which a proxy can only rethrow as UndeclaredThrowableException:
     * a tool waiting for an SQLException would see neither the error nor Trino's message.
     */
    private static Object invokeOriginal(Method method, Object original, Object[] args) throws Throwable {
        try {
            return method.invoke(original, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Entry point: Wraps the original Trino Connection.
     */
    public static Connection wrap(Connection originalConnection) {
        return (Connection) Proxy.newProxyInstance(
                TrinoProxyFactory.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionHandler(originalConnection));
    }

    // --- Connection Handler ---
    // Intercepts calls to Connection methods. If a Statement is created, it wraps it.
    private static class ConnectionHandler implements InvocationHandler {
        private final Connection original;

        public ConnectionHandler(Connection original) { this.original = original; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = invokeOriginal(method, original, args);
            
            // If the result is a Statement (including PreparedStatement), wrap it
            if (result instanceof Statement) {
                return wrapStatement((Statement) result);
            }
            if (result instanceof DatabaseMetaData) {
                return wrapDatabaseMetaData((DatabaseMetaData) result);
            }
            return result;
        }
    }

    // --- Statement Wrapper ---
    private static Statement wrapStatement(Statement originalStatement) {
        // Dynamically get interfaces to support both Statement and PreparedStatement
        // For simplicity, we explicitly list standard JDBC interfaces here.
        // You might want to add PreparedStatement.class if not already covered.
        Class<?>[] interfaces = new Class<?>[]{Statement.class, java.sql.PreparedStatement.class};
        
        return (Statement) Proxy.newProxyInstance(
                TrinoProxyFactory.class.getClassLoader(),
                interfaces, 
                new StatementHandler(originalStatement));
    }

    // --- Statement Handler ---
    // Intercepts calls to Statement methods. If a ResultSet is returned, it wraps it.
    private static class StatementHandler implements InvocationHandler {
        private final Statement original;

        public StatementHandler(Statement original) { this.original = original; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = invokeOriginal(method, original, args);

            // If the method returns a ResultSet (e.g., executeQuery), wrap it
            if (result instanceof ResultSet) {
                return wrapResultSet((ResultSet) result);
            }
            return result;
        }
    }

    // --- ResultSet Wrapper (The Core Logic) ---
    static ResultSet wrapResultSet(ResultSet originalResultSet) {
        return (ResultSet) Proxy.newProxyInstance(
                TrinoProxyFactory.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new ResultSetHandler(originalResultSet));
    }

    // --- ResultSet Handler ---
    // Intercepts getObject calls to handle the OffsetDateTime conversion, and reads number columns as NUMERIC.
    private static class ResultSetHandler implements InvocationHandler {
        private final ResultSet original;
        private Set<Integer> numberColumns;

        public ResultSetHandler(ResultSet original) { this.original = original; }

        /** The 1-based indexes of the number columns, read once from the result's own metadata. */
        private Set<Integer> numberColumns() throws SQLException {
            if (numberColumns == null) {
                numberColumns = numberColumnsOf(original.getMetaData());
            }
            return numberColumns;
        }

        private boolean isNumberColumn(Object column) throws SQLException {
            if (numberColumns().isEmpty()) {
                return false;
            }
            if (column instanceof Integer) {
                return numberColumns.contains(column);
            }
            return column instanceof String && numberColumns.contains(original.findColumn((String) column));
        }

        private Object objectAt(Object column) throws SQLException {
            return column instanceof Integer ? original.getObject((Integer) column) : original.getObject((String) column);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("getMetaData") && args == null) {
                ResultSetMetaData metaData = original.getMetaData();
                return numberColumns().isEmpty() ? metaData : wrapResultSetMetaData(metaData, numberColumns);
            }
            boolean readsAValue = name.equals("getObject") || name.equals("getBigDecimal") || name.equals("getString");
            if (readsAValue && args != null && args.length >= 1 && isNumberColumn(args[0])) {
                boolean asNumber = args.length == 1
                        || (args.length == 2 && (args[1] == BigDecimal.class || args[1] == Number.class || args[1] == Object.class));
                if (name.equals("getObject") && asNumber) {
                    return plain(objectAt(args[0]));
                }
                if (name.equals("getBigDecimal") && args.length == 1) {
                    return plain(invokeOriginal(method, original, args));
                }
                if (name.equals("getString") && args.length == 1) {
                    Object number = plain(objectAt(args[0]));
                    return number instanceof BigDecimal ? ((BigDecimal) number).toPlainString() : number == null ? null : number.toString();
                }
            }

            // Target method: getObject(int/String, Class)
            if (method.getName().equals("getObject") && args != null && args.length == 2) {
                Object typeArg = args[1];

                // Check if KNIME is requesting OffsetDateTime (which Trino driver doesn't support)
                if (typeArg != null && typeArg.equals(OffsetDateTime.class)) {
                    
                    // 1. Request ZonedDateTime from Trino (which is supported)
                    // args[0] is either the column index (Integer) or column name (String)
                    ZonedDateTime zdt = null;
                    if (args[0] instanceof Integer) {
                        zdt = original.getObject((Integer) args[0], ZonedDateTime.class);
                    } else if (args[0] instanceof String) {
                        zdt = original.getObject((String) args[0], ZonedDateTime.class);
                    }

                    if (zdt == null) return null;

                    // 2. Convert ZonedDateTime to OffsetDateTime and return
                    return zdt.toOffsetDateTime();
                }
            }

            // For all other methods (next, getString, close, etc.), delegate to the original driver
            return invokeOriginal(method, original, args);
        }
    }

    /** A number value without a negative scale, so that even its toString() writes 20000 rather than 2E+4. */
    static Object plain(Object value) {
        if (value instanceof BigDecimal && ((BigDecimal) value).scale() < 0) {
            return ((BigDecimal) value).setScale(0);
        }
        // a Double only for NaN and the infinities, which no BigDecimal can hold
        return value;
    }

    private static Set<Integer> numberColumnsOf(ResultSetMetaData metaData) throws SQLException {
        Set<Integer> columns = new HashSet<>();
        for (int column = 1; column <= metaData.getColumnCount(); column++) {
            if (NUMBER_TYPE.equalsIgnoreCase(metaData.getColumnTypeName(column))) {
                columns.add(column);
            }
        }
        return columns;
    }

    // --- ResultSetMetaData Wrapper ---
    // Reports number columns as NUMERIC holding BigDecimal; the type name stays "number".
    static ResultSetMetaData wrapResultSetMetaData(ResultSetMetaData original, Set<Integer> numberColumns) {
        return (ResultSetMetaData) Proxy.newProxyInstance(
                TrinoProxyFactory.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class},
                (proxy, method, args) -> {
                    if (args != null && args.length == 1 && numberColumns.contains(args[0])) {
                        switch (method.getName()) {
                            case "getColumnType":
                                return Types.NUMERIC;
                            case "getColumnClassName":
                                return BigDecimal.class.getName();
                            default:
                                break;
                        }
                    }
                    return invokeOriginal(method, original, args);
                });
    }

    // --- DatabaseMetaData Wrapper ---
    // getColumns lists number columns as NUMERIC too, so a tool's navigator agrees with its result grid.
    static DatabaseMetaData wrapDatabaseMetaData(DatabaseMetaData original) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                TrinoProxyFactory.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class},
                (proxy, method, args) -> {
                    Object result = invokeOriginal(method, original, args);
                    if (method.getName().equals("getColumns") && result instanceof ResultSet) {
                        return wrapColumnsResultSet((ResultSet) result);
                    }
                    return result;
                });
    }

    /** DATA_TYPE is the fifth column of DatabaseMetaData.getColumns, TYPE_NAME the sixth. */
    private static final int DATA_TYPE_COLUMN = 5;

    static ResultSet wrapColumnsResultSet(ResultSet original) {
        return (ResultSet) Proxy.newProxyInstance(
                TrinoProxyFactory.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if (args != null && args.length == 1 && isDataTypeColumn(args[0])
                            && NUMBER_TYPE.equalsIgnoreCase(original.getString("TYPE_NAME"))) {
                        switch (method.getName()) {
                            case "getInt":
                                return Types.NUMERIC;
                            case "getShort":
                                return (short) Types.NUMERIC;
                            case "getLong":
                                return (long) Types.NUMERIC;
                            case "getObject":
                                return Types.NUMERIC;
                            case "getString":
                                return String.valueOf(Types.NUMERIC);
                            default:
                                break;
                        }
                    }
                    return invokeOriginal(method, original, args);
                });
    }

    private static boolean isDataTypeColumn(Object column) {
        return Integer.valueOf(DATA_TYPE_COLUMN).equals(column)
                || (column instanceof String && ((String) column).equalsIgnoreCase("DATA_TYPE"));
    }
}