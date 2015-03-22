package toonly.dbmanager.lowlevel;

import toonly.debugger.Debugger;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Queue;
import java.util.Timer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * Created by caoyouxin on 14-12-31.
 */
public class DS implements DataSource {

    private final String url;
    private final String username;
    private final String password;
    private Queue<Connection> connections;
    private int max = 0;

    public DS() {

        Properties props = this.getConnInfo();
        this.url = props.getProperty("url", "jdbc:mysql://localhost:3306?useUnicode=true&amp;characterEncoding=utf8&amp;autoReconnect=true&amp;characterSetResults=utf8&amp;failOverReadOnly");
        this.username = props.getProperty("username", "root");
        this.password = props.getProperty("password", "root");
        try {
            Class.forName(props.getProperty("driverClassName", "com.mysql.jdbc.Driver"));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        this.connections = new ConcurrentLinkedQueue<>();
        this.makeMoreConnections(10);
    }

    public void close() {
        connections.forEach((conn) -> {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
        try {
            com.mysql.jdbc.AbandonedConnectionCleanupThread.shutdown();
        } catch (Throwable t) {}
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        try {
            while (drivers.hasMoreElements())
                DriverManager.deregisterDriver(drivers.nextElement());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Properties getConnInfo() {
        return new Properties();
    }

    boolean returnConn(Connection conn) {
        return this.connections.offer(conn);
    }

    @Override
    public String toString() {
        return "SimpleDataSource{" +
                "max=" + max +
                ", connections num=" + connections.size() +
                ", password='" + password + '\'' +
                ", username='" + username + '\'' +
                ", url='" + url + '\'' +
                '}';
    }

    /**
     * <p>Attempts to establish a connection with the data source that
     * this {@code DataSource} object represents.
     *
     * @return a connection to the data source
     * @throws java.sql.SQLException        if a database access error occurs
     * @throws java.sql.SQLTimeoutException when the driver has determined that the
     *                                      timeout value specified by the {@code setLoginTimeout} method
     *                                      has been exceeded and has at least tried to cancel the
     *                                      current database connection attempt
     */
    @Override
    public synchronized Connection getConnection() throws SQLException {
        if (this.connections.isEmpty())
            this.makeMoreConnections(2 * this.max);
        return new Conn(this, this.connections.poll());
    }

    private boolean makeMoreConnections(int newMax) {
        try {
            for (int i = this.max; i < newMax; i++) {
                connections.add(DriverManager.getConnection(url, username, password));
            }
            this.max = newMax;
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * <p>Attempts to establish a connection with the data source that
     * this {@code DataSource} object represents.
     *
     * @param username the database user on whose behalf the connection is
     *                 being made
     * @param password the user's password
     * @return a connection to the data source
     * @throws java.sql.SQLException        if a database access error occurs
     * @throws java.sql.SQLTimeoutException when the driver has determined that the
     *                                      timeout value specified by the {@code setLoginTimeout} method
     *                                      has been exceeded and has at least tried to cancel the
     *                                      current database connection attempt
     * @since 1.4
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return null;
    }

    /**
     * <p>Retrieves the log writer for this <code>DataSource</code>
     * object.
     * <p>
     * <p>The log writer is a character output stream to which all logging
     * and tracing messages for this data source will be
     * printed.  This includes messages printed by the methods of this
     * object, messages printed by methods of other objects manufactured
     * by this object, and so on.  Messages printed to a data source
     * specific log writer are not printed to the log writer associated
     * with the <code>java.sql.DriverManager</code> class.  When a
     * <code>DataSource</code> object is
     * created, the log writer is initially null; in other words, the
     * default is for logging to be disabled.
     *
     * @return the log writer for this data source or null if
     * logging is disabled
     * @throws java.sql.SQLException if a database access error occurs
     * @see #setLogWriter
     * @since 1.4
     */
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    /**
     * <p>Sets the log writer for this <code>DataSource</code>
     * object to the given <code>java.io.PrintWriter</code> object.
     * <p>
     * <p>The log writer is a character output stream to which all logging
     * and tracing messages for this data source will be
     * printed.  This includes messages printed by the methods of this
     * object, messages printed by methods of other objects manufactured
     * by this object, and so on.  Messages printed to a data source-
     * specific log writer are not printed to the log writer associated
     * with the <code>java.sql.DriverManager</code> class. When a
     * <code>DataSource</code> object is created the log writer is
     * initially null; in other words, the default is for logging to be
     * disabled.
     *
     * @param out the new log writer; to disable logging, set to null
     * @throws java.sql.SQLException if a database access error occurs
     * @see #getLogWriter
     * @since 1.4
     */
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {

    }

    /**
     * <p>Sets the maximum time in seconds that this data source will wait
     * while attempting to connect to a database.  A value of zero
     * specifies that the timeout is the default system timeout
     * if there is one; otherwise, it specifies that there is no timeout.
     * When a <code>DataSource</code> object is created, the login timeout is
     * initially zero.
     *
     * @param seconds the data source login time limit
     * @throws java.sql.SQLException if a database access error occurs.
     * @see #getLoginTimeout
     * @since 1.4
     */
    @Override
    public void setLoginTimeout(int seconds) throws SQLException {

    }

    /**
     * Gets the maximum time in seconds that this data source can wait
     * while attempting to connect to a database.  A value of zero
     * means that the timeout is the default system timeout
     * if there is one; otherwise, it means that there is no timeout.
     * When a <code>DataSource</code> object is created, the login timeout is
     * initially zero.
     *
     * @return the data source login time limit
     * @throws java.sql.SQLException if a database access error occurs.
     * @see #setLoginTimeout
     * @since 1.4
     */
    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    /**
     * Return the parent Logger of all the Loggers used by this data source. This
     * should be the Logger farthest from the root Logger that is
     * still an ancestor of all of the Loggers used by this data source. Configuring
     * this Logger will affect all of the log messages generated by the data source.
     * In the worst case, this may be the root Logger.
     *
     * @return the parent Logger for this data source
     * @throws java.sql.SQLFeatureNotSupportedException if the data source does not use
     *                                                  {@code java.util.logging}
     * @since 1.7
     */
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    /**
     * Returns an object that implements the given interface to allow access to
     * non-standard methods, or standard methods not exposed by the proxy.
     * <p>
     * If the receiver implements the interface then the result is the receiver
     * or a proxy for the receiver. If the receiver is a wrapper
     * and the wrapped object implements the interface then the result is the
     * wrapped object or a proxy for the wrapped object. Otherwise return the
     * the result of calling <code>unwrap</code> recursively on the wrapped object
     * or a proxy for that result. If the receiver is not a
     * wrapper and does not implement the interface, then an <code>SQLException</code> is thrown.
     *
     * @param iface A Class defining an interface that the result must implement.
     * @return an object that implements the interface. May be a proxy for the actual implementing object.
     * @throws java.sql.SQLException If no object found that implements the interface
     * @since 1.6
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    /**
     * Returns true if this either implements the interface argument or is directly or indirectly a wrapper
     * for an object that does. Returns false otherwise. If this implements the interface then return true,
     * else if this is a wrapper then return the result of recursively calling <code>isWrapperFor</code> on the wrapped
     * object. If this does not implement the interface and is not a wrapper, return false.
     * This method should be implemented as a low-cost operation compared to <code>unwrap</code> so that
     * callers can use this method to avoid expensive <code>unwrap</code> calls that may fail. If this method
     * returns true then calling <code>unwrap</code> with the same argument should succeed.
     *
     * @param iface a Class defining an interface.
     * @return true if this implements the interface or directly or indirectly wraps an object that does.
     * @throws java.sql.SQLException if an error occurs while determining whether this is a wrapper
     *                               for an object with the given interface.
     * @since 1.6
     */
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }
}
