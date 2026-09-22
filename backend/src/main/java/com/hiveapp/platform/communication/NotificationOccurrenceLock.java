package com.hiveapp.platform.communication;

import java.sql.Connection;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

/**
 * Cross-node PostgreSQL idempotency lock; bounded in-process equivalent for disposable H2 tests.
 */
@Component
@RequiredArgsConstructor
public class NotificationOccurrenceLock {
  private static final ReentrantLock[] LOCAL = new ReentrantLock[256];

  static {
    for (int i = 0; i < LOCAL.length; i++) LOCAL[i] = new ReentrantLock();
  }

  private final DataSource dataSource;

  public void acquire(String hash) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Notification publication requires a transaction.");
    long key = Long.parseUnsignedLong(hash.substring(0, 16), 16);
    Connection connection = DataSourceUtils.getConnection(dataSource);
    try {
      String database = connection.getMetaData().getDatabaseProductName();
      if (database.equals("PostgreSQL")) {
        try (var statement = connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
          statement.setLong(1, key);
          statement.execute();
        }
      } else if (database.equals("H2")) {
        var lock = LOCAL[Math.floorMod(key, LOCAL.length)];
        lock.lock();
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              public void afterCompletion(int status) {
                lock.unlock();
              }
            });
      } else throw new IllegalStateException("Unsupported notification database.");
    } catch (java.sql.SQLException failure) {
      throw new org.springframework.dao.DataAccessResourceFailureException(
          "Notification publication lock unavailable", failure);
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }
}
