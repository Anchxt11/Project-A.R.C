package com.project.arc.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.*;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import io.github.cdimascio.dotenv.Dotenv;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;


public class ArcConfig {

    public final int MEMORY_THRESHOLD = 8;
    private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    private final Driver driver;
    private final Neo4jEmbeddingStore neo4jStore;

    // FIX: Redis store must be a singleton — creating a new instance per call
    // means deleteMessages() and updateMessages() can hit different connections,
    // leaving stale data in Redis and breaking history ordering.
    private final RedisChatMemoryStore redisStore;

    public ArcConfig() {
        this.driver = GraphDatabase.driver(
                "bolt://localhost:7687",
                AuthTokens.basic("neo4j", "password")
        );

        this.neo4jStore = Neo4jEmbeddingStore.builder()
                .driver(this.driver)   // reuse the same driver, no double connection
                .label("MemoryNode")
                .embeddingProperty("embedding")
                .textProperty("content")
                .indexName("memory_vector_finder")
                .dimension(3072)
                .build();

        // Singleton Redis store — all operations share one connection pool
        this.redisStore = RedisChatMemoryStore.builder()
                .host("localhost")
                .port(6379)
                .build();
    }

    private String getEnv(String key) {
        String val = dotenv.get(key);
        return (val != null) ? val : System.getenv(key);
    }

    // Non-streaming model — used for triage, routing, graph queries
    // FIX: "gemma-4-31b-it" is an Ollama model name. Use a valid Gemini API model.
    public ChatModel geminiModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(getEnv("GEMINI_KEY"))
                .modelName("gemma-4-31b-it")
                .temperature(0.2)
                .build();
    }

    public ChatModel ollamaModel() {
        return OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("gemma4:e2b")
                .temperature(1.0)
                .topK(64)
                .topP(0.95)
                .build();
    }

    public StreamingChatModel streamingOllamaModel() {
        return OllamaStreamingChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("gemma4:e2b")
                .temperature(1.0)
                .topK(64)
                .topP(0.95)
                .build();
    }

    // Streaming model — used for main ARC conversation
    // FIX: corrected model name + removed built-in tools (incompatible with @Tool methods)
    public StreamingChatModel streamingGeminiModel() {
        return GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(getEnv("GEMINI_KEY"))
                .modelName("gemma-4-31b-it")
                .returnThinking(false)
                .temperature(1.0)
                .build();
    }

    public ChatModel visionModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(getEnv("GEMINI_KEY"))
                .modelName("gemma-4-31b-it")
                .temperature(0.1)  // Low temp for precise, factual image analysis
                .build();
    }

    // FIX: returns the shared singleton — never creates a new instance
    public RedisChatMemoryStore redisStore() {
        return this.redisStore;
    }

    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(getEnv("GEMINI_KEY"))
                .modelName("gemini-embedding-2-preview")
                .build();
    }

    public Driver neo4jDriver() {
        return this.driver;
    }

    public Neo4jEmbeddingStore neo4jStore() {
        return this.neo4jStore;
    }
}