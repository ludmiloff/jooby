package org.jooby.jdbc;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import java.util.Properties;

import javax.sql.DataSource;

import org.jooby.Env;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javaslang.control.Try.CheckedRunnable;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Jdbc.class, Properties.class, HikariConfig.class, HikariDataSource.class,
    System.class })
public class JdbcTest {

  private Block onStop = unit -> {
    Env env = unit.get(Env.class);
    expect(env.onStop(unit.capture(CheckedRunnable.class))).andReturn(env);
  };

  private Block mysql = unit -> {
    Properties props = unit.get(Properties.class);
    expect(props.setProperty("dataSource.useServerPrepStmts", "true")).andReturn(null);
    expect(props.setProperty("dataSource.prepStmtCacheSqlLimit", "2048")).andReturn(null);
    expect(props.setProperty("dataSource.cachePrepStmts", "true")).andReturn(null);
    expect(props.setProperty("dataSource.prepStmtCacheSize", "250")).andReturn(null);
    expect(props.setProperty("dataSource.encoding", "UTF-8")).andReturn(null);
  };

  @Test(expected = IllegalArgumentException.class)
  public void nullname() throws Exception {
    new Jdbc(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyname() throws Exception {
    new Jdbc("");
  }

  @Test
  public void memdb() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db", ConfigValueFactory.fromAnyRef("mem"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(currentTimeMillis(123))
        .expect(props("org.h2.jdbcx.JdbcDataSource", "jdbc:h2:mem:123;DB_CLOSE_DELAY=-1", "h2.123",
            "sa", "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("123"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void fsdb() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db", ConfigValueFactory.fromAnyRef("fs"))
        .withValue("application.name", fromAnyRef("jdbctest"))
        .withValue("application.tmpdir", fromAnyRef("target"))
        .withValue("application.charset", fromAnyRef("UTF-8"))
        .resolve();

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.h2.jdbcx.JdbcDataSource", "jdbc:h2:target/jdbctest", "h2.jdbctest",
            "sa", "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("jdbctest"))
        .expect(onStop)
        .expect(unit -> {
          unit.get(HikariDataSource.class).close();
        })
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        }, unit -> {
          unit.captured(CheckedRunnable.class).iterator().next().run();
        });
  }

  @Test
  public void cceExceptionInSource() throws Exception {
    ClassCastException cce = new ClassCastException();
    StackTraceElement e = new StackTraceElement(Jdbc.class.getName(), "accept", null, 0);
    cce.setStackTrace(new StackTraceElement[]{e });
    assertEquals(true, Jdbc.CCE.apply(cce).isSuccess());
  }

  @Test
  public void cceExceptionWithoutSource() throws Exception {
    ClassCastException cce = new ClassCastException();
    StackTraceElement e = new StackTraceElement(JdbcTest.class.getName(), "accept", null, 0);
    cce.setStackTrace(new StackTraceElement[]{e });
    assertEquals(true, Jdbc.CCE.apply(cce).isSuccess());
  }

  @Test
  public void dbWithCallback() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db", ConfigValueFactory.fromAnyRef("fs"))
        .withValue("application.name", fromAnyRef("jdbctest"))
        .withValue("application.tmpdir", fromAnyRef("target"))
        .withValue("application.charset", fromAnyRef("UTF-8"))
        .resolve();

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.h2.jdbcx.JdbcDataSource", "jdbc:h2:target/jdbctest", "h2.jdbctest",
            "sa", "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("jdbctest"))
        .expect(onStop)
        .expect(unit -> {
          HikariConfig h = unit.get(HikariConfig.class);
          h.setAllowPoolSuspension(true);
        })
        .run(unit -> {
          new Jdbc()
              .doWith((final HikariConfig h) -> {
                h.setAllowPoolSuspension(true);
              })
              .configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void databaseWithCredentials() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db.url",
        ConfigValueFactory.fromAnyRef("jdbc:mysql://localhost/db"))
        .withValue("db.user", fromAnyRef("foo"))
        .withValue("db.password", fromAnyRef("bar"))
        .withValue("application.charset", fromAnyRef("UTF-8"))
        .withValue("application.name", fromAnyRef("jdbctest"))
        .withValue("application.tmpdir", fromAnyRef("target"))
        .resolve();

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("com.mysql.jdbc.jdbc2.optional.MysqlDataSource", "jdbc:mysql://localhost/db",
            "mysql.db", "foo", "bar", false))
        .expect(mysql)
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("db"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void derby() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db", ConfigValueFactory.fromAnyRef("jdbc:derby:testdb"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.apache.derby.jdbc.ClientDataSource", "jdbc:derby:testdb", "derby.testdb",
            null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("testdb"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void connectionString() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.apache.derby.jdbc.ClientDataSource", null, "derby.testdb",
            null, "", false))
        .expect(hikariConfig())
        .expect(unit -> {
          Properties props = unit.mock(Properties.class);
          expect(props.setProperty("url", "jdbc:derby:testdb")).andReturn(null);

          HikariConfig hconf = unit.get(HikariConfig.class);
          expect(hconf.getDataSourceProperties()).andReturn(props);
        })
        .expect(hikariDataSource())
        .expect(serviceKey("testdb"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc("jdbc:derby:testdb").configure(unit.get(Env.class), config,
              unit.get(Binder.class));
        });
  }

  @Test
  public void db2() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef("jdbc:db2://127.0.0.1:50000/SAMPLE"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("com.ibm.db2.jcc.DB2SimpleDataSource", "jdbc:db2://127.0.0.1:50000/SAMPLE",
            "db2.SAMPLE", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("SAMPLE"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void hsql() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef("jdbc:hsqldb:file"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.hsqldb.jdbc.JDBCDataSource", "jdbc:hsqldb:file",
            "hsqldb.file", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("file"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void mariadb() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef("jdbc:mariadb://localhost/db"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.mariadb.jdbc.MySQLDataSource", "jdbc:mariadb://localhost/db",
            "mariadb.db", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("db"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });

  }

  @Test
  public void mysql() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef("jdbc:mysql://localhost/db"))
        .withValue("application.charset", fromAnyRef("UTF-8"))
        .withValue("application.name", fromAnyRef("jdbctest"))
        .withValue("application.tmpdir", fromAnyRef("target"))
        .resolve();

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("com.mysql.jdbc.jdbc2.optional.MysqlDataSource", "jdbc:mysql://localhost/db",
            "mysql.db", null, "", false))
        .expect(mysql)
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("db"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void dbspecific() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db.url",
        ConfigValueFactory.fromAnyRef("jdbc:mysql://localhost/db?useEncoding=true&characterEncoding=UTF-8"))
        .withValue("application.charset", fromAnyRef("UTF-8"))
        .withValue("application.name", fromAnyRef("jdbctest"))
        .withValue("application.tmpdir", fromAnyRef("target"))
        // override defaults
        .withValue("db.cachePrepStmts", fromAnyRef(false))
        .resolve();

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("com.mysql.jdbc.jdbc2.optional.MysqlDataSource", "jdbc:mysql://localhost/db?useEncoding=true&characterEncoding=UTF-8",
            "mysql.db", null, "", false))
        .expect(mysql)
        .expect(unit -> {
          Properties props = unit.get(Properties.class);
          expect(props.setProperty("dataSource.cachePrepStmts", "false")).andReturn(null);
        })
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("db"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void setHikariOptions() throws Exception {
    long connectionTimeout = 1000;
    int maximumPoolSize = 10;
    long idleTimeout = 800000;

    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db", ConfigValueFactory.fromAnyRef("fs"))
        .withValue("application.name", fromAnyRef("jdbctest"))
        .withValue("application.tmpdir", fromAnyRef("target"))
        .withValue("application.charset", fromAnyRef("UTF-8"))
        .withValue("hikari.connectionTimeout", fromAnyRef(connectionTimeout))
        .withValue("hikari.maximumPoolSize", fromAnyRef(maximumPoolSize))
        .withValue("hikari.idleTimeout", fromAnyRef(idleTimeout))
        .withValue("hikari.autoCommit", fromAnyRef(false))
        .resolve();

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.h2.jdbcx.JdbcDataSource", "jdbc:h2:target/jdbctest", "h2.jdbctest",
            "sa", "", false))
        .expect(unit -> {
          Properties props = unit.get(Properties.class);
          expect(props.setProperty("maximumPoolSize", "10")).andReturn(null);
          expect(props.setProperty("connectionTimeout", "1000")).andReturn(null);
          expect(props.setProperty("idleTimeout", "800000")).andReturn(null);
          expect(props.setProperty("autoCommit", "false")).andReturn(null);
        })
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("jdbctest"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void overrideDataSource() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db", ConfigValueFactory.fromAnyRef("fs"))
        .withValue("application.name", fromAnyRef("jdbctest"))
        .withValue("application.tmpdir", fromAnyRef("target"))
        .withValue("application.charset", fromAnyRef("UTF-8"))
        .withValue("hikari.dataSourceClassName", fromAnyRef("test.MyDataSource"))
        .resolve();

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.h2.jdbcx.JdbcDataSource", "jdbc:h2:target/jdbctest", "h2.jdbctest",
            "sa", "", true))
        .expect(unit -> {
          Properties properties = unit.get(Properties.class);
          expect(properties.setProperty("dataSourceClassName", "test.MyDataSource"))
              .andReturn(null);
        })
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("jdbctest"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void twoDatabases() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db.audit", ConfigValueFactory.fromAnyRef("fs"))
        .withValue("application.name", fromAnyRef("jdbctest"))
        .withValue("application.tmpdir", fromAnyRef("target"))
        .withValue("application.charset", fromAnyRef("UTF-8"))
        .withValue("hikari.audit.dataSourceClassName", fromAnyRef("test.MyDataSource"))
        .resolve();

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.h2.jdbcx.JdbcDataSource", "jdbc:h2:target/jdbctest", "h2.jdbctest",
            "sa", "", true))
        .expect(unit -> {
          Properties properties = unit.get(Properties.class);
          expect(properties.setProperty("dataSourceClassName", "test.MyDataSource"))
              .andReturn(null);
        })
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("jdbctest"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc("db.audit").configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void sqlserver() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef(
            "jdbc:sqlserver://localhost:1433;databaseName=AdventureWorks;integratedSecurity=true;"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(
            props("com.microsoft.sqlserver.jdbc.SQLServerDataSource",
                "jdbc:sqlserver://localhost:1433;databaseName=AdventureWorks;integratedSecurity=true;",
                "sqlserver.AdventureWorks", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("AdventureWorks"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void oracle() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef("jdbc:oracle:thin:@myhost:1521:orcl"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("oracle.jdbc.pool.OracleDataSource", "jdbc:oracle:thin:@myhost:1521:orcl",
            "oracle.orcl", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("orcl"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void pgsql() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef("jdbc:pgsql://server/database"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("com.impossibl.postgres.jdbc.PGDataSource", "jdbc:pgsql://server/database",
            "pgsql.database", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("database"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void postgresql() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef("jdbc:postgresql://server/database"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.postgresql.ds.PGSimpleDataSource", "jdbc:postgresql://server/database",
            "postgresql.database", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("database"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void sybase() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef("jdbc:jtds:sybase://server/database"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("com.sybase.jdbcx.SybDataSource", "jdbc:jtds:sybase://server/database",
            "sybase.database", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("database"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void firebirdsql() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef("jdbc:firebirdsql:host:mydb"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.firebirdsql.pool.FBSimpleDataSource", "jdbc:firebirdsql:host:mydb",
            "firebirdsql.mydb", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("mydb"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void sqlite() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config.withValue("db",
        ConfigValueFactory.fromAnyRef("jdbc:sqlite:testdb"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("org.sqlite.SQLiteDataSource", "jdbc:sqlite:testdb",
            "sqlite.testdb", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("testdb"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @Test
  public void unknownDb() throws Exception {
    Config config = ConfigFactory.parseResources(getClass(), "jdbc.conf");
    Config dbconf = config
        .withValue("db", ConfigValueFactory.fromAnyRef("jdbc:custom:testdb"))
        .withValue("databases.custom.dataSourceClassName",
            ConfigValueFactory.fromAnyRef("custom.DS"));

    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(props("custom.DS", "jdbc:custom:testdb",
            "custom.testdb", null, "", false))
        .expect(hikariConfig())
        .expect(hikariDataSource())
        .expect(serviceKey("testdb"))
        .expect(onStop)
        .run(unit -> {
          new Jdbc().configure(unit.get(Env.class), dbconf, unit.get(Binder.class));
        });
  }

  @SuppressWarnings("unchecked")
  private Block serviceKey(final String db) {
    return unit -> {
      Env env = unit.get(Env.class);
      expect(env.serviceKey()).andReturn(new Env.ServiceKey());

      AnnotatedBindingBuilder<DataSource> binding = unit.mock(AnnotatedBindingBuilder.class);
      binding.toInstance(unit.get(HikariDataSource.class));
      binding.toInstance(unit.get(HikariDataSource.class));

      Binder binder = unit.get(Binder.class);
      expect(binder.bind(Key.get(DataSource.class))).andReturn(binding);
      expect(binder.bind(Key.get(DataSource.class, Names.named(db)))).andReturn(binding);
    };
  }

  private Block hikariConfig() {
    return unit -> {
      Properties properties = unit.get(Properties.class);
      HikariConfig hikari = unit.constructor(HikariConfig.class)
          .build(properties);
      unit.registerMock(HikariConfig.class, hikari);
    };
  }

  private Block hikariDataSource() {
    return unit -> {
      HikariConfig properties = unit.get(HikariConfig.class);
      HikariDataSource hikari = unit.constructor(HikariDataSource.class)
          .build(properties);
      unit.registerMock(HikariDataSource.class, hikari);
    };
  }

  private Block currentTimeMillis(final long millis) {
    return unit -> {
      unit.mockStatic(System.class);
      expect(System.currentTimeMillis()).andReturn(millis);
    };
  }

  private Block props(final String dataSourceClassName, final String url, final String name,
      final String username, final String password, final boolean hasDataSourceClassName) {
    return unit -> {
      Properties properties = unit.constructor(Properties.class)
          .build();

      expect(properties
          .setProperty("dataSource.dataSourceClassName", dataSourceClassName))
              .andReturn(null);
      if (username != null) {
        expect(properties
            .setProperty("dataSource.user", username))
                .andReturn(null);
        expect(properties
            .setProperty("dataSource.password", password))
                .andReturn(null);
      }
      if (url != null) {
        expect(properties
            .setProperty("dataSource.url", url))
                .andReturn(null);
      }

      expect(properties.containsKey("dataSourceClassName")).andReturn(hasDataSourceClassName);
      if (!hasDataSourceClassName) {
        expect(properties.getProperty("dataSource.dataSourceClassName"))
            .andReturn(dataSourceClassName);
        expect(properties.setProperty("dataSourceClassName", dataSourceClassName)).andReturn(null);
      }
      expect(properties.remove("dataSource.dataSourceClassName")).andReturn(dataSourceClassName);
      expect(properties.setProperty("poolName", name)).andReturn(null);

      unit.registerMock(Properties.class, properties);
    };
  }
}
