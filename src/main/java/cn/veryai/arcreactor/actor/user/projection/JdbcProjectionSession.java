package cn.veryai.arcreactor.actor.user.projection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransaction;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.projection.jdbc.JdbcSession;

/** Transaction shared by user projection writes and the projection offset update. */
public final class JdbcProjectionSession implements JdbcSession, AutoCloseable {
  private final Connection connection;
  private final SqlSession sqlSession;

  public JdbcProjectionSession(DataSource dataSource, SqlSessionFactory sqlSessionFactory) {
    Connection opened = null;
    try {
      opened = Objects.requireNonNull(dataSource).getConnection();
      opened.setAutoCommit(false);
      var configuration = Objects.requireNonNull(sqlSessionFactory).getConfiguration();
      connection = opened;
      sqlSession = new DefaultSqlSession(
          configuration, configuration.newExecutor(new JdbcTransaction(connection)), false);
    } catch (SQLException | RuntimeException error) {
      closeQuietly(opened);
      throw new IllegalStateException("cannot open user projection JDBC session", error);
    }
  }

  public <Mapper> Mapper mapper(Class<Mapper> mapperType) {
    return sqlSession.getMapper(mapperType);
  }

  @Override
  public <Result> Result withConnection(Function<Connection, Result> function) throws Exception {
    return function.apply(connection);
  }

  @Override public void commit() { sqlSession.commit(true); }
  @Override public void rollback() { sqlSession.rollback(true); }
  @Override public void close() { sqlSession.close(); }

  private static void closeQuietly(Connection connection) {
    if (connection == null) return;
    try {
      connection.close();
    } catch (SQLException ignored) {
      // Preserve the original session creation error.
    }
  }
}
