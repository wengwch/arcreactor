package cn.veryai.arcreactor.mapper.typehandler;

import cn.veryai.arcreactor.util.JsonUtil;
import cn.veryai.arcreactor.vo.UserForwardingView;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Bound only to the instance projection's user_forwardings column. */
public class UserForwardingMapJsonTypeHandler extends BaseTypeHandler<Map<String, UserForwardingView>> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index,
            Map<String, UserForwardingView> value, JdbcType jdbcType) throws SQLException {
        try {
            statement.setString(index, JsonUtil.toJson(value));
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Cannot serialize user forwarding JSON", exception);
        }
    }

    @Override
    public Map<String, UserForwardingView> getNullableResult(ResultSet result, String column) throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public Map<String, UserForwardingView> getNullableResult(ResultSet result, int column) throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public Map<String, UserForwardingView> getNullableResult(CallableStatement statement, int column) throws SQLException {
        return decode(statement.getString(column));
    }

    private Map<String, UserForwardingView> decode(String json) throws SQLException {
        if (json != null && json.isBlank()) throw new SQLException("Invalid user forwarding JSON");
        try {
            Map<String, UserForwardingView> value = JsonUtil.toMap(json, String.class, UserForwardingView.class);
            return value == null ? Map.of() : value;
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid user forwarding JSON", exception);
        }
    }
}
