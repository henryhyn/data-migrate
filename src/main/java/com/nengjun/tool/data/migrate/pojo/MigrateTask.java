package com.nengjun.tool.data.migrate.pojo;

import javax.sql.DataSource;

/**
 * Created by Henry on 15/12/5.
 */
public class MigrateTask {
    private DataSource dataSource;
    private String sourceTable;
    private String sourceSql;
    private String targetTable;

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getSourceSql() {
        return sourceSql;
    }

    public void setSourceSql(String sourceSql) {
        this.sourceSql = sourceSql;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }
}
