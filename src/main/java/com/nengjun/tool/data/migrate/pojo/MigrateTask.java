package com.nengjun.tool.data.migrate.pojo;

import lombok.Data;

import javax.sql.DataSource;

/**
 * Created by Henry on 15/12/5.
 */
@Data
public class MigrateTask {
    private DataSource dataSource;
    private String sourceTable;
    private String sourceSql;
    private String targetTable;
}
