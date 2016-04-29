package com.nengjun.tool.data.migrate.pojo;

import java.util.List;

/**
 * Created by Henry on 15/12/5.
 */
public class MigrateTaskList {
    private List<MigrateTask> migrateTasks;

    public List<MigrateTask> getMigrateTasks() {
        return migrateTasks;
    }

    public void setMigrateTasks(List<MigrateTask> migrateTasks) {
        this.migrateTasks = migrateTasks;
    }
}
