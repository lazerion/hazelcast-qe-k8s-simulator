package com.hazelcast.qe.k8s.coordinator;

import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

@Slf4j
public class CoordinatorTest {

    @Rule
    public KubernetesServer server = new KubernetesServer(true, true);

    private Coordinator coordinator;

    @Before
    public void before(){
        coordinator = Coordinator.builder()
                .k8s(server.getClient())
                .build();
    }

    @Test
    public void shouldDeploy() {
        val actual = coordinator.deploy();
        assertThat(actual, is(notNullValue()));
    }

    @Test
    public void shouldScale(){
        coordinator.deploy();
        val deployment = coordinator.scale(2);
        log.info("{}", deployment.getStatus());
    }

    @Test
    public void shouldTerminate(){
        coordinator.deploy();
        coordinator.scale(1);
        assertThat(coordinator.terminate(), is(true));
    }
}