package cn.veryai.arcreactor.actor.hypervisor.projection;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransaction;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.projection.jdbc.JdbcSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Connection-scoped transaction shared by a projection handler and its offset update. */
public final class JdbcProjectionSession implements JdbcSession {
    private final Connection connection;
    private final SqlSession sqlSession;

    public JdbcProjectionSession(DataSource dataSource, SqlSessionFactory sqlSessionFactory) {
        Connection openedConnection = null;
        try {
            openedConnection = Objects.requireNonNull(dataSource).getConnection();
            openedConnection.setAutoCommit(false);
            connection = openedConnection;
            var configuration = Objects.requireNonNull(sqlSessionFactory).getConfiguration();
            sqlSession = new DefaultSqlSession(
                    configuration, configuration.newExecutor(new JdbcTransaction(connection)), false);
        } catch (SQLException | RuntimeException exception) {
            closeQuietly(openedConnection);
            throw new IllegalStateException("cannot open projection JDBC session", exception);
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
            // Preserve the original session creation failure.
        }
    }
}
