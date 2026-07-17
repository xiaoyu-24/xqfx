package com.xqfx.requirements.compatibility;

import com.mysql.cj.jdbc.Driver;
import org.hibernate.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyCompatibilityTest {

    @Test
    void usesHibernateLineCompatibleWithMySql57() {
        var version = Version.getVersionString();

        assertTrue(version.startsWith("6.5.") || version.startsWith("6.4."),
                "Hibernate 6.5.x/6.4.x is required for the MySQL 5.7 compatibility baseline, actual: " + version);
    }

    @Test
    void usesConnectorEightLineForMySql57() throws java.sql.SQLException {
        assertEquals(8, new Driver().getMajorVersion(),
                "MySQL Connector/J 8.x is required for the MySQL 5.7 compatibility baseline");
    }
}
