package cn.veryai.arcreactor.mapper.typehandler;

import cn.veryai.arcreactor.util.JsonUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Explicitly bound in Mapper XML to avoid changing unrelated Map mappings. */
public class StringMapJsonTypeHandler extends BaseTypeHandler<Map<String, String>> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index,
                                    Map<String, String> value, JdbcType jdbcType) throws SQLException {
        try {
            statement.setString(index, JsonUtil.toJson(value));
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Cannot serialize string-map JSON", exception);
        }
    }

    @Override
    public Map<String, String> getNullableResult(ResultSet result, String column) throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public Map<String, String> getNullableResult(ResultSet result, int column) throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public Map<String, String> getNullableResult(CallableStatement statement, int column) throws SQLException {
        return decode(statement.getString(column));
    }

    private Map<String, String> decode(String json) throws SQLException {
        if (json != null && json.isBlank()) throw new SQLException("Invalid string-map JSON");
        try {
            Map<String, String> value = JsonUtil.toMap(json, String.class, String.class);
            return value == null ? new HashMap<>() : value;
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid string-map JSON", exception);
        }
    }
}
