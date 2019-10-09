package com.hazelcast.qe.k8s.provisioner;

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
public class ProvisionerTest {

    @Rule
    public KubernetesServer server = new KubernetesServer(true, true);

    private Provisioner provisioner;

    @Before
    public void before(){
        provisioner = Provisioner.builder()
                .k8s(server.getClient())
                .build();
    }

    @Test
    public void shouldDeploy() {
        val actual = provisioner.deploy();
        assertThat(actual, is(notNullValue()));
    }

    @Test
    public void shouldScale(){
        provisioner.deploy();
        val deployment = provisioner.scale(2);
        log.info("{}", deployment.getStatus());
    }

    @Test
    public void shouldTerminate(){
        provisioner.deploy();
        provisioner.scale(1);
        assertThat(provisioner.terminate(), is(true));
    }
}