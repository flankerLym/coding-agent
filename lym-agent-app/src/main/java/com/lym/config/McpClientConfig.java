package com.lym.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;


public class McpClientConfig {

    // ====================== 可用服务：3000端口（MCP Browser Server） ======================
    @Bean("mcpSyncClient03")
    public McpSyncClient sseMcpClient03() {
        // 【修复】必须加上/sse路径，对应文档中的legacy SSE endpoint
        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                .builder("http://175.178.182.172:3000/sse")
                .build();

        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport)
                .requestTimeout(Duration.ofMinutes(180))
                .build();

        // 初始化（仅执行一次，Spring单例保证）
        var init = mcpSyncClient.initialize();
        System.out.println("SSE MCP 3000 Initialized: " + init);

        return mcpSyncClient;
    }

    // ====================== 不可用服务：8101端口（link dead） ======================
    @Bean("mcpSyncClient02")
    public McpSyncClient sseMcpClient02() {
        // 服务已挂，仅保留代码结构，建议修复服务后使用
        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                .builder("http://175.178.182.172:8101")
                .build();

        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport)
                .requestTimeout(Duration.ofMinutes(180))
                .build();

        // 会报错，建议注释或修复服务
        // var init = mcpSyncClient.initialize();
        // System.out.println("SSE MCP 8101 Initialized: " + init);

        return mcpSyncClient;
    }

    // ====================== 不可用服务：8102端口（link fetch error） ======================
//    @Bean("mcpSyncClient01")
//    public McpSyncClient sseMcpClient01() {
//        // 服务已挂，仅保留代码结构，建议修复服务后使用
//        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
//                .builder("http://175.178.182.172:8102")
//                .build();
//
//        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport)
//                .requestTimeout(Duration.ofMinutes(180))
//                .build();
//
//        // 会报错，建议注释或修复服务
//        // var init = mcpSyncClient.initialize();
//        // System.out.println("SSE MCP 8102 Initialized: " + init);
//
//        return mcpSyncClient;
//    }
}