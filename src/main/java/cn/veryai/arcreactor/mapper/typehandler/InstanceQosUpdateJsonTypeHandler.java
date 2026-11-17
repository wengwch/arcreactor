package cn.veryai.arcreactor.mapper.typehandler;

import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import cn.veryai.arcreactor.util.JsonUtil;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Bound only to the instance projection's qos_update column. */
public class InstanceQosUpdateJsonTypeHandler extends BaseTypeHandler<InstanceQosUpdate> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index,
            InstanceQosUpdate value, JdbcType jdbcType) throws SQLException {
        try {
            statement.setString(index, JsonUtil.toJson(value));
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Cannot serialize instance QoS update JSON", exception);
        }
    }

    @Override
    public InstanceQosUpdate getNullableResult(ResultSet result, String column) throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public InstanceQosUpdate getNullableResult(ResultSet result, int column) throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public InstanceQosUpdate getNullableResult(CallableStatement statement, int column) throws SQLException {
        return decode(statement.getString(column));
    }

    private InstanceQosUpdate decode(String json) throws SQLException {
        if (json != null && json.isBlank()) throw new SQLException("Invalid instance QoS update JSON");
        try {
            return JsonUtil.toObj(json, InstanceQosUpdate.class);
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid instance QoS update JSON", exception);
        }
    }
}
