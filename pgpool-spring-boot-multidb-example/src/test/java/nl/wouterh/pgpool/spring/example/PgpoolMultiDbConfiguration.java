package nl.wouterh.pgpool.spring.example;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import nl.wouterh.pgpool.PgPoolConfig;
import nl.wouterh.pgpool.PgPoolManager;
import nl.wouterh.pgpool.PooledDatabase;
import nl.wouterh.pgpool.common.example.CommonTestContainer;
import nl.wouterh.pgpool.common.example.ExampleTableFiller;
import nl.wouterh.pgpool.liquibase.LiquibaseDatabaseInitializer;
import nl.wouterh.pgpool.spring.PgPoolDataSource;
import nl.wouterh.pgpool.spring.hikari.HikariDataSourceFactory;
import nl.wouterh.pgpool.testcontainers.PostgreSQLContainerConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.JdbcDatabaseContainer;

@Configuration
public class PgpoolMultiDbConfiguration {

  @Bean
  ExecutorService executorService() {
    return Executors.newCachedThreadPool();
  }

  @Bean
  PgPoolManager pgPoolManager(
      @Qualifier("db1") PgPoolDataSource dataSource1,
      @Qualifier("db2") PgPoolDataSource dataSource2,
      ExecutorService executorService
  ) throws SQLException {
    // Optionally start container concurrently, not blocking spring context initialization
    Future<JdbcDatabaseContainer> startedPostgreSQLContainer = executorService.submit(() -> {
      CommonTestContainer.postgres.start();
      return CommonTestContainer.postgres;
    });

    PgPoolManager manager = new PgPoolManager(PgPoolConfig.builder()
        .connectionProvider(new PostgreSQLContainerConnectionProvider(startedPostgreSQLContainer))
        .waitForDropOnShutdown(true)
        .dropThreads(2)
        .pooledDatabase(PooledDatabase.builder()
            .name("db1")
            .createThreads(2)
            .spares(10)
            .listener(new HikariDataSourceFactory())
            .initializer(new LiquibaseDatabaseInitializer("db/changelog/changelog-1.xml"))
            .initializer(new ExampleTableFiller())
            .build())
        .pooledDatabase(PooledDatabase.builder()
            .name("db2")
            .createThreads(2)
            .spares(10)
            .listener(new HikariDataSourceFactory())
            .initializer(new LiquibaseDatabaseInitializer("db/changelog/changelog-2.xml"))
            .build())
        .build());

    // Optionally start manager concurrently, not blocking spring context initialization
    executorService.submit(() -> {
      manager.start();
      return manager;
    });

    return manager;
  }

  @Bean
  @Qualifier("db1")
  PgPoolDataSource dataSource1() {
    return new PgPoolDataSource("db1");
  }

  @Bean
  @Qualifier("db2")
  PgPoolDataSource dataSource2() {
    return new PgPoolDataSource("db2");
  }
}