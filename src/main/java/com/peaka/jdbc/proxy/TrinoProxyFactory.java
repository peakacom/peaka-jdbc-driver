package com.peaka.jdbc.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public class TrinoProxyFactory {

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
    private static ResultSet wrapResultSet(ResultSet originalResultSet) {
        return (ResultSet) Proxy.newProxyInstance(
                TrinoProxyFactory.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new ResultSetHandler(originalResultSet));
    }

    // --- ResultSet Handler ---
    // Intercepts getObject calls to handle the OffsetDateTime conversion.
    private static class ResultSetHandler implements InvocationHandler {
        private final ResultSet original;

        public ResultSetHandler(ResultSet original) { this.original = original; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            
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
}