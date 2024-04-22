[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.peaka/peaka-jdbc/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.peaka/peaka-jdbc)

# Peaka JDBC Driver

Welcome to the documentation for the Peaka JDBC Driver – your gateway to seamless integration with the powerful Peaka data platform.
This JDBC driver serves as a bridge between your Java application and the Peaka platform, enabling you to retrieve and manipulate data with ease and efficiency.

Let's dive in and unlock new possibilities for your data-driven projects with the Peaka JDBC Driver!

## Installation

Peaka JDBC Driver is distributed via the
[Maven central repository](https://central.sonatype.com/artifact/com.peaka/peaka-jdbc/).
Simply configure your build tool to pull the latest version of the SDK into
your projects. We recommend using the latest version of the JDBC driver.

- Group ID: `com.peaka`
- Artifact ID: `peaka-jdbc`

## Usage

First you need to register the driver with `Class.forName("com.peaka.jdbc.PeakaDriver");`.
Host name for the Peaka JDBC Driver is `dbc.peaka.studio` and port is `4567`. You need to
provide your API Key with `extraCredentials` property. You can find a sample code below:

```java
  private static final String QUERY = "SELECT SUM(\"amount\") AS total_amount\n" +
            "FROM \"stripe\".\"payment\".\"charges\"";
    //Follow instructions on how to create your api key: https://docs.peaka.com/how-to-guides/how-to-generate-api-keys
    //Connect sample data sets to Peaka then run the query above with sample data by running testQueryWithSampleData.
    private static final String PEAKA_API_KEY = "<Your API KEY>";

    //Register the driver with the classname
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
```
