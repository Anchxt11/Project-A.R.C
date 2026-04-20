package com.project.arc.config;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.util.List;

public class MCPToolProvider {

    public McpToolProvider mcpToolProvider() {

        String projectRoot = System.getProperty("user.dir");
        String sensesPath = projectRoot + "/arc_senses";
        String scriptPath = sensesPath + "/system_monitor.py";

        // 1. System Senses (Python via UV)
        McpClient systemClient = DefaultMcpClient.builder()
                .key("sense")
                .transport(new StdioMcpTransport.Builder()
                        // Uses 'uv run' to ensure the virtual env is used
                        .command(List.of("uv", "--directory", sensesPath, "run", "python", scriptPath))
                        .build())
                .build();

        /*
        // 2. File Access
        McpClient allFileAccessClient = DefaultMcpClient.builder()
                .key("full")
                .transport(new StdioMcpTransport.Builder()
                        .command(List.of("cmd.exe", "/c", "npx.cmd", "-y", "@modelcontextprotocol/server-filesystem",
                                projectRoot,
                                "C:\\Users\\anchi\\Downloads",
                                "C:\\Users\\anchi\\OneDrive\\Documents"))
                        .build())
                .build();

        McpClient readOnlyAccessClient = DefaultMcpClient.builder()
                .key("readonly")
                .transport(new StdioMcpTransport.Builder()
                        .command(List.of("cmd.exe", "/c", "npx.cmd", "-y", "@danielsuguimoto/readonly-server-filesystem",
                                "C:\\Users\\anchi\\OneDrive\\Pictures"))
                        .build())
                .build();

         */

        return McpToolProvider.builder()
                .mcpClients(List.of(systemClient))
                //Prevents similar tool names from clashing
                .toolNameMapper(((client, toolSpec) -> client.key() + "_" + toolSpec.name()))
                .build();
    }
}
