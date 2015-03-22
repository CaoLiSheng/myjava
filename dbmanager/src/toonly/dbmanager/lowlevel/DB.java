/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package toonly.dbmanager.lowlevel;

import org.slf4j.LoggerFactory;
import toonly.debugger.Debugger;
import toonly.wrapper.StringWrapper;

import java.nio.charset.Charset;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author CPU
 *
 * 关系数据库操作类：
 * @* 表结构查询：目前仅支持show databases;和show tables in xxx;
 * @1 普通查询：参数必须是不含占位符的字符串
 * @2 带占位符的查询：参数可以是String(sql)，Object...
 * @3 普通增删改：参数同样必须是String(sql)
 * @4 带占位符的增删改：参数可以是String(sql)，Object...
 * @** 参数中带conn的：conn在外部可能设置自动提交模式，所以返回结果是个问题，需要测试测试。。。
 * @注 Object...需要通过instanceof检测，代换成合适的字符串
 * @另注 Object的类型除包括char之外的七大类型外，还对java.util.Date类型做了特殊处理， 替换成了long，其余各种类型，都调用toString方法
 */
public final class DB {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(DB.class);

	//Begin 单例模式
	private static final DB INSTANCE = new DB();

	public static DB instance() {
		return INSTANCE;
	}

	public static DB instance(DSConstructor sdc) {
		DS ds = sdc.construct();
		INSTANCE.ds = (null != ds) ? ds : INSTANCE.ds;
		return INSTANCE;
	}
	//End 单例模式

	//Begin 常量定义
    private static final String Label_DATABASE = "Database";
    private static final String Label_TABLE = "Tables_in_%s";
	private static final String Show_TABLES = "SHOW TABLES IN `%s`;";
	//End 常量定义

	private DS ds;

	private DB() {
		this.ds = new DS();
		System.out.println(this.ds.toString());
	}

    public void close() {
        this.ds.close();
    }

    private void debug(String sql, Object... params) {
        Debugger.debugRun(this, () -> {
            System.out.println(String.format("SQL wa [%s]", sql));
            System.out.println(String.format("\tParams wa %s", new StringWrapper(Arrays.deepToString(params)).unwrap().val()));
        });
    }

    private void debug(String sql) {
        Debugger.debugRun(this, () -> System.out.println(String.format("SQL wa [%s]", sql)));
    }

    private void log(SQLException ex) {
        log.info("msg : {}", ex.getLocalizedMessage());
        String locationString = null;
        for (StackTraceElement e : ex.getStackTrace()) {
            locationString = e.toString();
            if (locationString.contains(DB.class.getName()))
                break;
        }
        log.info("location in DB : {}", locationString);
    }

    public RS simpleQuery(String sql) {
        this.debug(sql);

		String[] labels = parseLabels(sql);
		try (Connection conn = this.ds.getConnection();
             Statement stat = conn.createStatement();
             ResultSet rs = stat.executeQuery(sql)) {
            return new RS(rs, labels);
		} catch (SQLException ex) {
			this.log(ex);
			return new RS();
		}
	}

    public RS preparedQuery(String sql, List<Object> params) {
        return this.preparedQuery(sql, params.toArray());
    }

	public RS preparedQuery(String sql, Object... params) {
        this.debug(sql, params);

		String[] labels = parseLabels(sql);
        try (Connection conn = this.ds.getConnection(); PreparedStatement stat = conn.prepareStatement(sql)) {
			parsePlaceholders(stat, params);
			try (ResultSet rs = stat.executeQuery()) {
                return new RS(rs, labels);
            }
		} catch (SQLException ex) {
			this.log(ex);
			return new RS();
		}
    }

	private String[] parseLabels(String sql) {
		String upperSql = sql.toUpperCase();
		String select = "SELECT";
		sql = sql.substring(upperSql.indexOf(select) + select.length());
		upperSql = sql.toUpperCase();
		String from = "FROM";
		sql = sql.substring(0, upperSql.indexOf(from));
		String[] labels = sql.split(",");
		for (int i = 0; i < labels.length; i++) {
			String label = labels[i];
			if (label.contains("`")) {
				label = label.replaceAll("`", "");
			}
			if (label.toLowerCase().contains("as")) {
				labels[i] = label.substring(label.toLowerCase().indexOf("as")+2).trim();
			} else if (label.contains(".")) {
				labels[i] = label.substring(label.indexOf(".")+1).trim();
			} else {
				labels[i] = label.trim();
			}
		}
		return labels;
	}

	public List<String> showTables(String schemaName) {
        String sql = this.getShow_TABLES(schemaName);
        this.debug(sql);

		try (Connection conn = this.ds.getConnection();
             Statement stat = conn.createStatement();
             ResultSet rs = stat.executeQuery(sql)) {
            ArrayList<String> ret = new ArrayList<>();
            while (rs.next()) {
                ret.add(rs.getString(this.getLabel_TABLE(schemaName)));
            }
            return ret;
		} catch (SQLException ex) {
			this.log(ex);
			return new ArrayList<>();
		}
	}

	private String getLabel_TABLE(String schemaName) {
		return String.format(DB.Label_TABLE, schemaName);
	}

	private String getShow_TABLES(String schemaName) {
		return String.format(DB.Show_TABLES, schemaName);
	}

	public List<String> showDatabases() {
        String sql = "SHOW DATABASES;";
        this.debug(sql);

		try (Connection conn = this.ds.getConnection();
			Statement stat = conn.createStatement();
            ResultSet rs = stat.executeQuery(sql)) {
            List<String> ret = new ArrayList<>();
            while (rs.next()) {
                ret.add(rs.getString(Label_DATABASE));
            }
			return ret;
		} catch (SQLException ex) {
			this.log(ex);
			return new ArrayList<>();
		}
	}

	public boolean simpleExecute(String sql, int expected) {
		try (Connection conn = this.ds.getConnection()) {
			return this.simpleExecute(conn, sql, expected);
		} catch (SQLException ex) {
			this.log(ex);
			return false;
		}
	}

	public boolean simpleExecute(String sql) {
		try (Connection conn = this.ds.getConnection()) {
			return this.simpleExecute(conn, sql);
		} catch (SQLException ex) {
			this.log(ex);
			return false;
		}
	}

	public boolean simpleExecute(Connection conn, String sql, int expected) throws SQLException {
        this.debug(sql);

		boolean isInsert = isInsert(sql);
		boolean isDrop = isDrop(sql);
		try (Statement stat = conn.createStatement()) {
            int ret = stat.executeUpdate(sql);
            return asExpected(isInsert, ret, expected, isDrop);
        }
	}

	public boolean simpleExecute(Connection conn, String sql) throws SQLException {
        this.debug(sql);

		try (Statement stat = conn.createStatement()) {
            stat.executeUpdate(sql);
            return true;
        }
	}

    public boolean preparedExecute(String sql, int expected, List<Object> params) {
        return this.preparedExecute(sql, expected, params.toArray());
    }

	public boolean preparedExecute(String sql, int expected, Object... params) {
		try (Connection conn = this.ds.getConnection()) {
			return this.preparedExecute(conn, sql, expected, params);
		} catch (SQLException ex) {
			this.log(ex);
			return false;
		}
	}

    public boolean preparedExecute(Connection conn, String sql, int expected, List<Object> params) throws SQLException {
        return this.preparedExecute(conn, sql, expected, params.toArray());
    }

	public boolean preparedExecute(Connection conn, String sql, int expected, Object... params) throws SQLException {
        this.debug(sql, params);

		boolean isInsert = isInsert(sql);
		boolean isDrop = isDrop(sql);
        PreparedStatement stat = null;
		try {
            stat = conn.prepareStatement(sql);
            parsePlaceholders(stat, params);
            int ret = stat.executeUpdate();
            return asExpected(isInsert, ret, expected, isDrop);
        } finally {
            if (null != stat) {
                stat.close();
                stat = null;
            }
        }
    }

	/**
	 * 批处理需要回滚
	 * @param sql
	 * @param expected
	 * @param params
	 * @return
	 */
	public boolean batchExecute(String sql, int expected, List<Object[]> params) {
		try (Connection conn = this.ds.getConnection()) {
			return this.batchExecute(conn, sql, expected, params);
		} catch (SQLException ex) {
			this.log(ex);
			return false;
		}
	}

	/**
	 * 批处理需要回滚
	 * @param sql
	 * @param expected
	 * @param batch
	 * @return
	 */
	public boolean batchExecute(String sql, int expected, Batch batch) {
		try (Connection conn = this.ds.getConnection()) {
			return this.batchExecute(conn, sql, expected, batch);
		} catch (SQLException ex) {
			this.log(ex);
			return false;
		}
	}

	/**
	 * 回滚和提交都交给外部处理
	 * @param conn
	 * @param sql
	 * @param expected
	 * @param params
	 * @return
	 * @throws java.sql.SQLException
	 */
	public boolean batchExecute(Connection conn, String sql, int expected, List<Object[]> params) throws SQLException {
        this.debug(sql, params);

		boolean isInsert = isInsert(sql);
		boolean isDrop = isDrop(sql);
		try (PreparedStatement stat = conn.prepareStatement(sql)) {
            for (Object[] objects : params) {
                parsePlaceholders(stat, objects);
                stat.addBatch();
            }
            int[] counts = stat.executeBatch();
            int ret = 0;
            for (int count : counts) {
                ret += count;
            }
            return asExpected(isInsert, ret, expected, isDrop);
        }
	}

	/**
	 * 回滚和提交都交给外部处理
	 * @param conn
	 * @param sql
	 * @param expected
	 * @param batch
	 * @return
	 * @throws java.sql.SQLException
	 */
	public boolean batchExecute(Connection conn, String sql, int expected, Batch batch) throws SQLException {
        this.debug(sql);

		boolean isInsert = isInsert(sql);
		boolean isDrop = isDrop(sql);
		try (PreparedStatement stat = conn.prepareStatement(sql)) {
            for (int i = 0; i < expected; i++) {
                Object[] objects = batch.row(i);
                parsePlaceholders(stat, objects);
                stat.addBatch();
            }
            int[] counts = stat.executeBatch();
            int ret = 0;
            for (int count : counts) {
                ret += count;
            }
            return asExpected(isInsert, ret, expected, isDrop);
        }
	}

	public boolean transaction(Trans trans) {
		Connection conn = null;
		try {
			conn = this.ds.getConnection();
			boolean autoCommit = conn.getAutoCommit();
			conn.setAutoCommit(false);
			trans.trans(conn);
			conn.commit();
			conn.setAutoCommit(autoCommit);
			return true;
		} catch (SQLException ex) {
			this.log(ex);
			if (null != conn) {
				try {
					conn.rollback();
				} catch (SQLException e) {
					Logger.getLogger(DB.class.getName()).log(Level.SEVERE, null, e);
				}
			}
			return false;
		} finally {
			if (null != conn) {
				try {
					conn.close();
					System.out.println(ds.toString());
				} catch (SQLException ex) {
					this.log(ex);
				}
			}
		}
	}

	private static boolean asExpected(boolean isInsert, int ret, int expected, boolean isDrop) {
		Debugger.debugRun(DB.class, () ->
                log.info("isInsert : {}; ret : {}; expected : {}; isDrop : {}.", isInsert, ret, expected, isDrop));
		if (isDrop) {
			return true;
		}
		return isInsert ? (asExpectedWhenInsert(ret, expected)) : expected == ret;
	}

	private static boolean asExpectedWhenInsert(int ret, int expected) {
		return (ret >= expected && ret <= (2 * expected));
	}

	private boolean isInsert(String sql) {
		return sql.trim().toUpperCase().startsWith("INSERT");
	}

	private boolean isDrop(String sql) {
		return sql.trim().toUpperCase().startsWith("DROP");
	}

	private void parsePlaceholders(PreparedStatement stat, Object[] params) throws SQLException {
		int index = 1;
		for (Object obj : params) {
			if (obj instanceof Integer) {
				stat.setInt(index++, (int) obj);
			} else if (obj instanceof Short) {
				stat.setShort(index++, (short) obj);
			} else if (obj instanceof Byte) {
				stat.setByte(index++, (byte) obj);
			} else if (obj instanceof Boolean) {
				stat.setBoolean(index++, (boolean) obj);
			} else if (obj instanceof Long) {
				stat.setLong(index++, (long) obj);
			} else if (obj instanceof Date) {
				stat.setTimestamp(index++, new Timestamp(((Date) obj).getTime()));
			} else if (obj instanceof String) {
				stat.setString(index++, (String) obj);
//				stat.setString(index++, new String(((String) obj).getBytes(), Charset.forName("UTF-8")));
			} else if (obj instanceof Float) {
				stat.setFloat(index++, (float) obj);
			} else if (obj instanceof Double) {
				stat.setDouble(index++, (double) obj);
			} else {
				stat.setString(index++, obj.toString());
			}
		}
	}

    public boolean createDatabase(String schemaName) {
        return this.simpleExecute(String.format("CREATE DATABASE `%s` DEFAULT CHARACTER SET 'utf8' COLLATE 'utf8_general_ci';"
            , schemaName));
    }

    public boolean dropDatabase(String schemaName) {
        return this.simpleExecute(String.format("DROP DATABASE IF EXISTS `%s`", schemaName));
    }
}