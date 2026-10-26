package com.peaka.jdbc.proxy;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Trino's number type, as a BI tool sees it through this driver: NUMERIC holding plain BigDecimal values,
 * where the Trino driver alone says OTHER and keeps 20000 as 2E+4.
 */
public class TrinoProxyFactoryNumberTest {

    private static final List<String> NAMES = Arrays.asList("amount", "small", "label", "price", "missing");
    private static final List<String> TYPES = Arrays.asList("number", "number", "varchar", "decimal(10,2)", "number");
    private static final List<Object> VALUES = Arrays.asList(new BigDecimal("2E+4"), new BigDecimal("1E-7"), "x", new BigDecimal("1.50"), null);

    /** One row as the Trino driver hands it over: number is OTHER, with its value as the server keeps it. */
    private static ResultSet trinoRow() {
        ResultSetMetaData metaData = (ResultSetMetaData) Proxy.newProxyInstance(
                TrinoProxyFactoryNumberTest.class.getClassLoader(), new Class<?>[]{ResultSetMetaData.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getColumnCount":
                            return NAMES.size();
                        case "getColumnTypeName":
                            return TYPES.get((Integer) args[0] - 1);
                        case "getColumnType":
                            String type = TYPES.get((Integer) args[0] - 1);
                            return type.equals("number") ? Types.OTHER : type.startsWith("decimal") ? Types.DECIMAL : Types.VARCHAR;
                        case "getColumnClassName":
                            return TYPES.get((Integer) args[0] - 1).equals("number") ? Number.class.getName() : String.class.getName();
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
        return (ResultSet) Proxy.newProxyInstance(
                TrinoProxyFactoryNumberTest.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getMetaData":
                            return metaData;
                        case "findColumn":
                            return NAMES.indexOf(args[0]) + 1;
                        case "getObject":
                        case "getBigDecimal":
                            return VALUES.get(args[0] instanceof Integer ? (Integer) args[0] - 1 : NAMES.indexOf(args[0]));
                        case "getString":
                            Object value = VALUES.get(args[0] instanceof Integer ? (Integer) args[0] - 1 : NAMES.indexOf(args[0]));
                            return value == null ? null : value.toString();
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    @Test
    public void aNumberColumnIsNumericHoldingBigDecimal() throws Exception {
        ResultSetMetaData metaData = TrinoProxyFactory.wrapResultSet(trinoRow()).getMetaData();
        assertEquals(Types.NUMERIC, metaData.getColumnType(1));
        assertEquals(BigDecimal.class.getName(), metaData.getColumnClassName(1));
        assertEquals("number", metaData.getColumnTypeName(1));
        // other columns are as the Trino driver said
        assertEquals(Types.VARCHAR, metaData.getColumnType(3));
        assertEquals(Types.DECIMAL, metaData.getColumnType(4));
    }

    @Test
    public void aNumberValueIsPlain() throws Exception {
        ResultSet row = TrinoProxyFactory.wrapResultSet(trinoRow());
        assertEquals("20000", row.getObject(1).toString());
        assertEquals("20000", row.getObject("amount", BigDecimal.class).toString());
        assertEquals(0, new BigDecimal("20000").compareTo(row.getBigDecimal("amount")));
        assertEquals("20000", row.getString(1));
        assertEquals("0.0000001", row.getString("small"));
        assertNull(row.getObject(5));
        assertNull(row.getString(5));
    }

    @Test
    public void otherColumnsReadAsBefore() throws Exception {
        ResultSet row = TrinoProxyFactory.wrapResultSet(trinoRow());
        assertEquals("x", row.getString(3));
        assertEquals(new BigDecimal("1.50"), row.getObject(4));
    }

    @Test
    public void getColumnsListsANumberColumnAsNumeric() throws Exception {
        List<String> typeNames = Arrays.asList("number", "varchar(10)");
        List<Integer> dataTypes = Arrays.asList(Types.OTHER, Types.VARCHAR);
        int[] row = {-1};
        ResultSet columns = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next":
                            return ++row[0] < typeNames.size();
                        case "getString":
                            return "TYPE_NAME".equals(args[0]) ? typeNames.get(row[0]) : String.valueOf(dataTypes.get(row[0]));
                        case "getInt":
                            return dataTypes.get(row[0]);
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
        ResultSet listed = TrinoProxyFactory.wrapColumnsResultSet(columns);
        listed.next();
        assertEquals(Types.NUMERIC, listed.getInt("DATA_TYPE"));
        assertEquals(Types.NUMERIC, listed.getInt(5));
        assertEquals(String.valueOf(Types.NUMERIC), listed.getString("data_type"));
        listed.next();
        assertEquals(Types.VARCHAR, listed.getInt("DATA_TYPE"));
    }
}
