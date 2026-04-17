package com.project.arc.config;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;

import dev.langchain4j.model.ollama.OllamaChatModel;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

import java.util.List;


public class ArcConfig {

    public final int MEMORY_THRESHOLD = 8;

    // 1. LLM Connection (Gemini 2.5 Flash)
    public ChatModel geminiModel(){
        return GoogleAiGeminiChatModel.builder()
                .apiKey("AIzaSyBXzm1GzA7sdVAnVH_nbULMiCA6Wpus28A")
                .modelName("gemini-2.5-flash")
                .temperature(0.2)
                .build();
    }

    // 2. LLM Connection (OLLAMA3)
    public ChatModel ollamaModel(){
        return OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("gemma4:e2b")
                .temperature(1.0)
                .topK(64)
                .topP(0.95)
                .build();
    }

    // 2. L2 Persistent Memory (Redis)
    public RedisChatMemoryStore redisStore() {
        return RedisChatMemoryStore.builder()
                .host("localhost")
                .port(6379)
                .build();
    }

    // 3. L3 Relational: Neo4j (Graph Store)
    public Neo4jEmbeddingStore neo4jStore() {
        String uri = "bolt://localhost:7687"; // e.g., "bolt://localhost:7687"
        String user = "neo4j";
        String password = "password";
        return Neo4jEmbeddingStore.builder()
                .driver(neo4jDriver())
                .label("MemoryNode")
                .embeddingProperty("embedding")
                .textProperty("content")
                .indexName("memory_vector_finder")
                .withBasicAuth(uri, user, password)
                .dimension(3072)
                .build();
    }

    // Embedding Model (GOOGLE)
    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey("AIzaSyBXzm1GzA7sdVAnVH_nbULMiCA6Wpus28A")
                .modelName("gemini-embedding-2-preview")
                .build();
    }

    // Direct Driver for Autonomous Linking
    public Driver neo4jDriver(){
        return GraphDatabase.driver("bolt://localhost:7687",
                AuthTokens.basic("neo4j", "password")
        );
    }

    public McpToolProvider mcpToolProvider() {
        McpClient pythonSenses = DefaultMcpClient.builder()
                .transport(new StdioMcpTransport.Builder()
                        .command(List.of("python", "C:/path/to/arc_senses.py"))
                       .build())
                .build();

        return McpToolProvider.builder()
                .mcpClients(List.of(pythonSenses))
                .build();
    }

}
