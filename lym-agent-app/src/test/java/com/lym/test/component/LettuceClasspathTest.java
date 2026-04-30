package com.lym.test.component;

import org.junit.Assert;
import org.junit.Test;

public class LettuceClasspathTest {

    @Test
    public void testLettuceCoreClassExists() throws Exception {
        Class<?> clazz = Class.forName("io.lettuce.core.SslVerifyMode");
        Assert.assertNotNull(clazz);

        System.out.println("SslVerifyMode loaded from: "
                + clazz.getProtectionDomain().getCodeSource().getLocation());
    }
}