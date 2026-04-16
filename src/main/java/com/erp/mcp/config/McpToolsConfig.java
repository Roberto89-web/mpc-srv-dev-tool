package com.erp.mcp.config;

import com.erp.mcp.tools.DocReaderTool;
import com.erp.mcp.tools.GitHubRepoTool;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Registra i tool MCP nel contesto Spring.
 * Ogni nuovo tool va aggiunto qui dentro withToolObjects().
 */
@Configuration
public class McpToolsConfig {

    @Bean
    @Primary
    public ObjectMapper mcpObjectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public ToolCallbackProvider erpTools(DocReaderTool docReaderTool,
                                          GitHubRepoTool gitHubRepoTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(docReaderTool, gitHubRepoTool)
                // Quando aggiungi nuovi tool, aggiungili qui:
                // .toolObjects(docReaderTool, gitHubRepoTool, altroTool)
                .build();
    }
}
