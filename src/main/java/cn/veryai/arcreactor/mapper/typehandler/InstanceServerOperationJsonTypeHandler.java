package cn.veryai.arcreactor.mapper.typehandler;

import cn.veryai.arcreactor.actor.instance.model.InstanceServerOperation;
import cn.veryai.arcreactor.util.JsonUtil;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Bound only to the instance projection's server_operation column. */
public class InstanceServerOperationJsonTypeHandler extends BaseTypeHandler<InstanceServerOperation> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index,
            InstanceServerOperation value, JdbcType jdbcType) throws SQLException {
        try {
            statement.setString(index, JsonUtil.toJson(value));
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Cannot serialize instance server operation JSON", exception);
        }
    }

    @Override
    public InstanceServerOperation getNullableResult(ResultSet result, String column) throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public InstanceServerOperation getNullableResult(ResultSet result, int column) throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public InstanceServerOperation getNullableResult(CallableStatement statement, int column) throws SQLException {
        return decode(statement.getString(column));
    }

    private InstanceServerOperation decode(String json) throws SQLException {
        if (json != null && json.isBlank()) throw new SQLException("Invalid instance server operation JSON");
        try {
            return JsonUtil.toObj(json, InstanceServerOperation.class);
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid instance server operation JSON", exception);
        }
    }
}
