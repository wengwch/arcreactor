package cn.veryai.arcreactor.actor.projection;

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

/** Shares rental projection writes and the projection offset update in one JDBC transaction. */
public final class RentalProjectionSession implements JdbcSession, AutoCloseable {
    private final Connection connection;
    private final SqlSession sqlSession;

    public RentalProjectionSession(DataSource dataSource, SqlSessionFactory sqlSessionFactory) {
        Connection openedConnection = null;
        try {
            openedConnection = Objects.requireNonNull(dataSource).getConnection();
            openedConnection.setAutoCommit(false);
            var configuration = Objects.requireNonNull(sqlSessionFactory).getConfiguration();
            connection = openedConnection;
            sqlSession = new DefaultSqlSession(
                    configuration, configuration.newExecutor(new JdbcTransaction(connection)), false);
        } catch (SQLException | RuntimeException error) {
            closeQuietly(openedConnection);
            throw new IllegalStateException("Cannot open rental projection session", error);
        }
    }

    public <Mapper> Mapper mapper(Class<Mapper> mapperType) {
        return sqlSession.getMapper(mapperType);
    }

    @Override
    public <Result> Result withConnection(Function<Connection, Result> action) throws Exception {
        return action.apply(connection);
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
