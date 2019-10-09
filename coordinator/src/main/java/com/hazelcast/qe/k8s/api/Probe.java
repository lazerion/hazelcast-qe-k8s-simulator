package com.hazelcast.qe.k8s.api;

import com.hazelcast.qe.k8s.coordinator.Coordinator;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.RandomStringUtils;
import spark.Request;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import java.io.IOException;

import static spark.Spark.*;

@Slf4j
public class Probe {
    private static Coordinator coordinator;

    public static void main(String[] args) {
        new Probe().start();
    }

    private void start() {
        coordinator = Coordinator.builder()
                .k8s(new DefaultKubernetesClient())
                .build();
        // simple health
        get("/ping", (req, res) -> RandomStringUtils.randomAlphabetic(36));
        // k8s commands
        get("/deploy", (req, res) -> coordinator.deploy());
        get("/scale/:count", (req, res) -> coordinator.scale(Integer.parseInt(req.params(":count"))));
        get("/agents/ip", (req, res) -> coordinator.agents());
        get("/agents/sanity/:count", (req, res) -> coordinator.sanity(Integer.parseInt(req.params(":count"))));
        // simulator commands
        post("/create-tests/", (req, res) -> coordinator.createTests());

        get("/check-ssh", (req, res) -> coordinator.checkSsh());
        get("/prepare", (req, res) -> coordinator.prepare());
        get("/run", (req, res) -> coordinator.run());
        // delete tests dir and delete k8s agent deployment
        delete("/terminate", (req, res) -> coordinator.terminate());

        // upload test.properties
        post("/upload/", (req, res) -> upload(req));
    }

    @SneakyThrows
    private Object upload(Request req) {
        req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement(""));
        try {
            val filePart = req.raw().getPart("file");
            val stream = filePart.getInputStream();
            return coordinator.upload(stream);
        } catch (IOException | ServletException e) {
            return "Exception occurred while uploading file" + e.getMessage();
        }
    }
}
