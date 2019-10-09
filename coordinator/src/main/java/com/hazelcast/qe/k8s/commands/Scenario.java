package com.hazelcast.qe.k8s.commands;

import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.exec.*;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Builder
public class Scenario extends DefaultExecuteResultHandler {
    private static String COORDINATOR = "/home/qe/hazelcast/simulator/hazelcast-simulator-0.11/bin/coordinator";

    private static final String gcArgs = "-verbose:gc -Xloggc:verbosegc.log -XX:+PrintGCTimeStamps -XX:+PrintGCDetails " +
            "-XX:+PrintTenuringDistribution -XX:+PrintGCApplicationStoppedTime " +
            "-XX:+PrintGCApplicationConcurrentTime";

    private static final String memberJvmArgs = "-Dhazelcast.partition.count=271 -Dhazelcast.health.monitoring.level=NOISY " +
            "-Dhazelcast.health.monitoring.delay.seconds=30 -Xmx1G -XX:+HeapDumpOnOutOfMemoryError " + gcArgs;

    private static final String clientJvmArgs = "-Xmx512M -XX:+HeapDumpOnOutOfMemoryError " + gcArgs;

    @Builder.Default
    private Integer members = 2;
    @Builder.Default
    private Integer clients = 4;
    @Builder.Default
    private String duration = "1m";
    @Builder.Default
    private String testSuite = "test.properties";

    @NonNull
    private Path workingPath;

    @SneakyThrows
    public void run() {
        val exec = new DefaultExecutor();
        exec.setWorkingDirectory(workingPath.toFile());

        val cmd = new CommandLine(COORDINATOR)
                .addArgument("--members")
                .addArgument(members.toString())
                .addArgument("--clients")
                .addArgument(clients.toString())
                .addArgument("--duration")
                .addArgument(duration)
                .addArgument("--memberArgs")
                .addArgument(memberJvmArgs, false)
                .addArgument("--clientArgs")
                .addArgument(clientJvmArgs, false)
                .addArgument("--parallel")
                .addArgument(testSuite);

        log.info("Run cmd {}", cmd);
        val watchdog = new ExecuteWatchdog(10 * 60 * 1000);
        exec.setExitValue(1);
        exec.setWatchdog(watchdog);
        exec.execute(cmd, this);
    }

    @Override
    public void onProcessComplete(int exitValue) {
        super.onProcessComplete(exitValue);
        log.info("Coordinator process complete with {}", exitValue);
        clean();
    }

    @Override
    public void onProcessFailed(ExecuteException e) {
        super.onProcessFailed(e);
        if (0 == e.getExitValue()) {
            log.info("Coordinator process completed with {}", e.getExitValue());
        } else {
            log.warn("Coordinator process failed with {}", e.getExitValue(), e.getCause());
        }
        clean();
    }

    private void clean() {
        val cmd = new CommandLine(COORDINATOR)
                .addArgument("--clean");

        val exec = new DefaultExecutor();
        exec.setWorkingDirectory(workingPath.toFile());

        try {
            exec.execute(cmd);
        } catch (IOException e) {
            log.error("Coordinator clean failed", e.getCause());
        }
    }
}
