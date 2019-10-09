package com.hazelcast.qe.k8s.coordinator;

import com.hazelcast.qe.k8s.commands.Scenario;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Slf4j
@Getter
public class Coordinator {
    private static String SIMULATOR = "/home/qe/hazelcast/simulator/hazelcast-simulator-0.11/bin/simulator-wizard";
    private static String PROVISIONER = "/home/qe/hazelcast/simulator/hazelcast-simulator-0.11/bin/provisioner";

    private static String SIMULATOR_USER = "SIMULATOR_USER=app-admin\n";
    private static String WORKING_DIR = "/home/qe/hazelcast/simulator/hazelcast-simulator-0.11";
    private static String TEST_DIR = "tests";

    @Builder.Default
    private String deploymentName = "k8s-deployment";
    @Builder.Default
    private String serviceAccount = "coordinator";
    @Builder.Default
    private String imageName = "agent:11";
    @Builder.Default
    private String namespace = "default";
    @Builder.Default
    private String replicatorName = "agent-replicator";
    @Builder.Default
    private String serviceLabel = "agent";
    @Builder.Default
    private List<Scenario> coordinators = new ArrayList<>();
    @NonNull
    private final KubernetesClient k8s;

    public Deployment deploy() {

        val ns = new NamespaceBuilder()
                .withNewMetadata()
                .withName(namespace)
                .addToLabels("qe", serviceLabel)
                .endMetadata()
                .build();

        k8s.namespaces().createOrReplace(ns);

        val serviceAccount = new ServiceAccountBuilder()
                .withNewMetadata()
                .withName(this.serviceAccount)
                .endMetadata()
                .build();

        k8s.serviceAccounts().inNamespace(namespace).createOrReplace(serviceAccount);

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(deploymentName)
                .endMetadata()
                .withNewSpec()
                .withReplicas(1)
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(serviceLabel, serviceLabel)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(serviceLabel)
                .withImage(imageName)
                .addNewPort()
                .withContainerPort(9000)
                .endPort()
                .addNewPort()
                .withContainerPort(9001)
                .endPort()
                .addNewPort()
                .withContainerPort(22)
                .endPort()
                .addNewPort()
                .withContainerPort(5701)
                .endPort()
                .addNewPort()
                .withContainerPort(5702)
                .endPort()
                .endContainer()
                .endSpec()
                .endTemplate()
                .withNewSelector()
                .addToMatchLabels(serviceLabel, serviceLabel)
                .endSelector()
                .endSpec()
                .build();

        deployment = k8s.apps().deployments().inNamespace(namespace).create(deployment);
        log.info("Created deployment {}", deployment);
        return deployment;
    }

    public Deployment scale(int size) {
        return k8s.apps()
                .deployments()
                .inNamespace(namespace)
                .withName(deploymentName)
                .scale(size);
    }

    @SneakyThrows
    public Boolean terminate() {
        if (Files.exists(Paths.get(WORKING_DIR, TEST_DIR))) {
            FileUtils.deleteDirectory(Paths.get(WORKING_DIR, TEST_DIR).toFile());
        }
        return k8s.apps().deployments()
                .inNamespace(namespace)
                .withName(deploymentName)
                .delete();
    }

    public List<String> agents() {
        return k8s.pods().inNamespace(namespace).withLabel(serviceLabel).list().getItems()
                .stream()
                .map(it -> it.getStatus().getPodIP())
                .collect(Collectors.toList());
    }

    @SneakyThrows
    public List<String> createTests() {
        if (Files.exists(Paths.get(WORKING_DIR, TEST_DIR, "agents.txt"))) {
            log.warn("Already created agents file.");
        }

        val exec = new DefaultExecutor();
        val cmd = new CommandLine(SIMULATOR)
                .addArgument("--createWorkDir")
                .addArgument(TEST_DIR)
                .addArgument("--cloudProvider")
                .addArgument("static");

        log.info("Command is {}", cmd);
        exec.setWorkingDirectory(Paths.get(WORKING_DIR).toFile());
        exec.execute(cmd);

        Files.write(
                Paths.get(WORKING_DIR, TEST_DIR, "simulator.properties"),
                SIMULATOR_USER.getBytes(),
                StandardOpenOption.APPEND);

        val agents = agents();
        Files.write(Paths.get(WORKING_DIR, TEST_DIR, "agents.txt"), agents);
        return Files.readAllLines(Paths.get(WORKING_DIR, TEST_DIR, "agents.txt"));
    }

    @SneakyThrows
    public String upload(InputStream tests) {
        val destination = Paths.get(WORKING_DIR, TEST_DIR, "test.properties");
        if (!Files.exists(destination)) {
            throw new RuntimeException("create tests first");
        }
        Files.copy(tests, destination, StandardCopyOption.REPLACE_EXISTING);
        return String.valueOf((long) Files.readAllLines(destination).size());
    }

    public String checkSsh() {
        val exec = new DefaultExecutor();
        val cmd = new CommandLine(SIMULATOR)
                .addArgument("--sshConnectionCheck");
        val outputStream = new ByteArrayOutputStream();

        val streamHandler = new PumpStreamHandler(outputStream);
        exec.setStreamHandler(streamHandler);
        exec.setWorkingDirectory(Paths.get(WORKING_DIR, TEST_DIR).toFile());

        try {
            log.info("Command is {}", cmd);
            exec.execute(cmd);
        } catch (IOException ex) {
            throw new RuntimeException(outputStream.toString(), ex);
        }
        return outputStream.toString();
    }

    @SneakyThrows
    public String prepare() {
        val path = Paths.get(WORKING_DIR, TEST_DIR);
        if (!Files.exists(path)) {
            throw new RuntimeException("create tests first");
        }

        val exec = new DefaultExecutor();
        exec.setWorkingDirectory(path.toFile());
        val cmd = new CommandLine(PROVISIONER)
                .addArgument("--install");

        return String.valueOf(exec.execute(cmd, EnvironmentUtils.getProcEnvironment()));
    }

    @SneakyThrows
    public String run() {
        val path = Paths.get(WORKING_DIR, TEST_DIR);
        if (!Files.exists(path)) {
            throw new RuntimeException("create tests first");
        }

        val pending = coordinators.stream().filter(it -> !it.hasResult()).count();
        if (pending > 0) {
            return "Pending coordinator(s) found, please wait for completion";
        }

        val coordinator = Scenario.builder()
                .workingPath(path)
                .build();

        coordinator.run();
        coordinators.add(coordinator);
        return "Started coordinator run";
    }

    public Boolean sanity(int expected) {
        if (agents().stream().filter(StringUtils::isNotBlank).count() != expected) {
            throw new RuntimeException("sanity check fails!");
        }
        return true;
    }
}
