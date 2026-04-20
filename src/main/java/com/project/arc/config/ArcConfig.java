package com.project.arc.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;

import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
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

    public ArcConfig(){
        this.driver=GraphDatabase.driver("bolt://localhost:7687",
                AuthTokens.basic("neo4j", "password"));

        String uri = "bolt://localhost:7687"; // e.g., "bolt://localhost:7687"
        String user = "neo4j";
        String password = "password";
        this.neo4jStore = Neo4jEmbeddingStore.builder()
                .driver(this.driver)
                .label("MemoryNode")
                .embeddingProperty("embedding")
                .textProperty("content")
                .indexName("memory_vector_finder")
                .withBasicAuth(uri, user, password)
                .dimension(3072)
                .build();


    }

    private String getEnv(String key) {
        String val = dotenv.get(key);
        return (val != null) ? val : System.getenv(key);
    }

    // 1. LLM Connection (Gemini 2.5 Flash)
    public ChatModel geminiModel(){
        return GoogleAiGeminiChatModel.builder()
                .apiKey(getEnv("GEMINI_KEY"))
                .modelName("gemma-4-31b-it")
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


    public StreamingChatModel streamingOllamaModel(){
        return OllamaStreamingChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("gemma4:e2b")
                .temperature(1.0)
                .topK(64)
                .topP(0.95)
                .build();
    }
    public StreamingChatModel streamingGeminiModel(){
        return GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(getEnv("GEMINI_KEY"))
                .modelName("gemma-4-31b-it")
                .temperature(1.0)
                .build();
    }

    // 2. L2 Persistent Memory (Redis)
    public RedisChatMemoryStore redisStore() {
        return RedisChatMemoryStore.builder()
                .host("localhost")
                .port(6379)
                .build();
    }

    // Embedding Model (GOOGLE)
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
