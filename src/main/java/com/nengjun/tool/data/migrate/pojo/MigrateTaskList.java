package com.nengjun.tool.data.migrate.pojo;

import lombok.Data;

import java.util.List;

/**
 * Created by Henry on 15/12/5.
 */
@Data
public class MigrateTaskList {
    private List<MigrateTask> migrateTasks;
}
