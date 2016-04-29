package com.nengjun.tool.data.migrate.service;

import com.nengjun.tool.data.migrate.pojo.MigrateTask;
import com.nengjun.tool.data.migrate.pojo.MigrateTaskList;
import jxl.Workbook;
import jxl.write.Boolean;
import jxl.write.*;
import jxl.write.Number;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.lang.StringUtils;
import org.ho.yaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by Henry on 15/12/5.
 */
@Component
public class MigrateService {
    private static final int BATCH_WRITE_SIZE = 520000;

    private Logger logger = LoggerFactory.getLogger(MigrateService.class);

    @Autowired
    private DataSource dataSource;
    private String migrateTaskListFile;

    private Pattern pattern = Pattern.compile("(group\\s+by|order\\s+by|having|limit)", Pattern.CASE_INSENSITIVE);

    public void run() throws SQLException, IOException, WriteException {
        MigrateTaskList migrateTaskList = null;
        try {
            logger.info("Load file {}.", new File(migrateTaskListFile).getAbsolutePath());
            migrateTaskList = (MigrateTaskList) Yaml.load(new File(migrateTaskListFile));
        } catch (FileNotFoundException e) {
            logger.error("File not found.", e);
        }
        Connection connection = dataSource.getConnection();

        assert migrateTaskList != null;
        for (MigrateTask migrateTask : migrateTaskList.getMigrateTasks()) {
            Connection conn = migrateTask.getDataSource().getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = null;

            if (migrateTask.getSourceTable() != null && migrateTask.getSourceSql() == null) {
                migrateTask.setSourceSql(String.format("SELECT * FROM %s", migrateTask.getSourceTable()));
            }

            if (migrateTask.getTargetTable() == null) {
                migrateTask.setTargetTable(getTableName(stmt, migrateTask.getSourceSql()));
            }

            String gutter = classifySql(migrateTask.getSourceSql());
            String table = migrateTask.getTargetTable();
            if (gutter.length() > 0 && !table.endsWith(".xls")) {
                List<String> conditions = getPageConditions(stmt, migrateTask.getSourceSql());
                for (String condition : conditions) {
                    String sql = String.format("%s %s %s", migrateTask.getSourceSql(), gutter, condition);
                    logger.info(sql);
                    rs = stmt.executeQuery(sql);
                    writeToDB(rs, connection, table);
                }
            } else {
                String sql = migrateTask.getSourceSql();
                logger.info(sql);
                rs = stmt.executeQuery(sql);
                if (table.endsWith(".xls")) {
                    writeToExcel(rs, table);
                } else {
                    writeToDB(rs, connection, table);
                }
            }

            DbUtils.closeQuietly(rs);
            DbUtils.closeQuietly(stmt);
            DbUtils.closeQuietly(conn);
        }

        DbUtils.closeQuietly(connection);

    }

    private String getTableName(Statement stmt, String sql) throws SQLException {
        ResultSet rs = stmt.executeQuery(String.format("%s LIMIT 0", sql));
        return rs.getMetaData().getTableName(1);
    }

    private List<String> getPageConditions(Statement stmt, String sql) throws SQLException {
        String tableName = getTableName(stmt, sql);
        ResultSet rs = stmt.executeQuery(String.format("SELECT min(id), max(id) FROM %s WHERE id>0 LIMIT 1", tableName));
        rs.next();
        int minId = rs.getInt(1);
        int maxId = rs.getInt(2);
        logger.info("minId = {}, maxId = {}", minId, maxId);
        List<String> conditions = new ArrayList<String>();
        for (int i = minId; i <= maxId + 1; i += BATCH_WRITE_SIZE) {
            conditions.add(String.format("id BETWEEN %d AND %d", i, i + BATCH_WRITE_SIZE - 1));
        }
        return conditions;
    }

    private String classifySql(String sql) {
        if (pattern.matcher(sql).find()) {
            return "";
        } else if (sql.indexOf("where") > 0) {
            return "AND";
        }
        return "WHERE";
    }

    private void writeToDB(ResultSet rs, Connection conn, String table) throws SQLException {
        String[] columnLabels = getColumnLabels(rs);
        String[] columnClazz = getColumnClazz(rs);
        int num = columnLabels.length;

        String[] pos = new String[num];
        Arrays.fill(pos, "?");
        String insertStr = String.format("INSERT INTO %s (%s) VALUES (%s);", table, StringUtils.join(columnLabels, ", "), StringUtils.join(pos, ", "));
        logger.info(insertStr);
        PreparedStatement pstmt = conn.prepareStatement(insertStr);

        int n = 0;
        while (rs.next()) {
            for (int i = 1; i <= num; i++) {
                if ("java.lang.String".equals(columnClazz[i - 1]) && StringUtils.isEmpty(rs.getString(i))) {
                    pstmt.setString(i, "");
                } else {
                    pstmt.setString(i, rs.getString(i) == null ? null : rs.getString(i).trim());
                }
            }
            try {
                pstmt.executeUpdate();
            } catch (Exception e) {
                logger.error("Insert error.", e);
            }
            n++;
            if (n % BATCH_WRITE_SIZE == 0) {
                logger.info("Migrate {} {} lines", table, n);
            }
        }
        logger.info("Migrate {} {} lines", table, n);

        DbUtils.closeQuietly(pstmt);
    }

    private void writeToExcel(ResultSet rs, String fileName) throws SQLException, IOException, WriteException {
        String[] columnLabels = getColumnLabels(rs);
        String[] columnClazz = getColumnClazz(rs);
        int num = columnLabels.length;

        File file = new File("/tmp", fileName);
        logger.info("Write to Excel {}", file.getAbsolutePath());
        OutputStream os = new FileOutputStream(file);
        WritableWorkbook wwb = Workbook.createWorkbook(os);
        WritableSheet ws = wwb.createSheet("Sheet 1", 0);

        for (int i = 0; i < num; i++) {
            ws.addCell(new Label(i, 0, columnLabels[i]));
        }

        int n = 0;
        while (rs.next()) {
            n++;
            for (int i = 0; i < num; i++) {
                if ("java.lang.Integer".equals(columnClazz[i]) || columnLabels[i].startsWith("num") || "cnt".equals(columnLabels[i])) {
                    ws.addCell(new Number(i, n, rs.getDouble(i + 1)));
                } else if ("java.lang.Boolean".equals(columnClazz[i])) {
                    ws.addCell(new Boolean(i, n, rs.getBoolean(i + 1)));
                } else {
                    ws.addCell(new Label(i, n, rs.getString(i + 1)));
                }
            }
            if (n % BATCH_WRITE_SIZE == 0) {
                logger.info("Migrate {} {} lines", file.getAbsolutePath(), n);
            }
        }
        logger.info("Migrate {} {} lines", file.getAbsolutePath(), n);

        wwb.write();
        wwb.close();
        os.close();
    }

    private String[] getColumnLabels(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int num = metaData.getColumnCount();
        String[] columns = new String[num];

        for (int i = 1; i <= num; i++) {
            columns[i - 1] = metaData.getColumnLabel(i);
        }
        return columns;
    }

    private String[] getColumnClazz(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int num = metaData.getColumnCount();
        String[] columns = new String[num];

        for (int i = 1; i <= num; i++) {
            columns[i - 1] = metaData.getColumnClassName(i);
        }
        return columns;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String getMigrateTaskListFile() {
        return migrateTaskListFile;
    }

    public void setMigrateTaskListFile(String migrateTaskListFile) {
        this.migrateTaskListFile = migrateTaskListFile;
    }
}
