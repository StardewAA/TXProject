package cn.iocoder.txgy.module.training.dal.mysql.live;

import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;

/** SQL 中显式声明租户/所有者范围；仅平台角色入口可以跨租户读取。 */
@Repository
public class LiveCenterRepository {
    @Resource private JdbcTemplate jdbc;
    public List<Map<String,Object>> list(String sql,Object... args) {
        return jdbc.query(sql,(rs,n)-> {
            Map<String,Object> row=new LinkedHashMap<>();
            var meta=rs.getMetaData();
            for(int i=1;i<=meta.getColumnCount();i++) {
                StringBuilder key=new StringBuilder(); boolean upper=false;
                for(char c:meta.getColumnLabel(i).toCharArray()) {
                    if(c=='_') upper=true; else {key.append(upper?Character.toUpperCase(c):c);upper=false;}
                }
                Object value=rs.getObject(i);
                if(value instanceof Timestamp time) value=time.toLocalDateTime();
                if(value instanceof byte[] bytes && bytes.length==1) value=(int)bytes[0];
                row.put(key.toString(),value);
            }
            return row;
        },args);
    }
    public Map<String,Object> one(String sql,Object... args) {
        var rows=list(sql,args);return rows.isEmpty()?null:rows.getFirst();
    }
    public long count(String sql,Object... args) { Long n=jdbc.queryForObject(sql,Long.class,args);return n==null?0:n; }
    public int update(String sql,Object... args) {return jdbc.update(sql,args);}
    public long insert(String sql,Object... args) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->{var ps=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
