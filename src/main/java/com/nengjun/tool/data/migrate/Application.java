package com.nengjun.tool.data.migrate;

import com.nengjun.tool.data.migrate.service.MigrateService;
import jxl.write.WriteException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Created by Henry on 16/4/29.
 */
@SpringBootApplication
@Slf4j
public class Application implements CommandLineRunner {
    @Autowired
    private MigrateService migrateService;

    @Override
    public void run(String[] args) throws Exception {
        if (args.length < 1) {
            log.error("Migrate task list file is missing");
            System.exit(-1);
        }
        String file = args[0];
        migrateService.setMigrateTaskListFile(file);
        try {
            migrateService.run();
        } catch (SQLException e) {
            log.error("SQLException", e);
        } catch (WriteException e) {
            log.error("Write to Excel", e);
        } catch (IOException e) {
            log.error("Input & Output", e);
        } catch (Exception e) {
            log.error("Exception", e);
        } finally {
            System.exit(0);
        }

    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
