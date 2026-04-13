package com.project.arc.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import dev.langchain4j.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;


public class ArcConfig {

    // 1. LLM Connection (Gemini 2.5 Flash)
    public GoogleAiGeminiChatModel model(){
        return GoogleAiGeminiChatModel.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .modelName("gemini-2.5-flash")
                .temperature(0.6)
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
        String uri = System.getenv("NEO4J_URI"); // e.g., "bolt://localhost:7687"
        String user = System.getenv("NEO4J_USER");
        String password = System.getenv("NEO4J_PASSWORD");
        return Neo4jEmbeddingStore.builder()
                .driver(neo4jDriver())
                .label("MemoryNode")
                .embeddingProperty("embedding")
                .textProperty("content")
                .indexName("memory_vector_finder")
                .withBasicAuth(uri, user, password)
                .dimension(768)
                .build();
    }

    // Embedding Model (GOOGLE)
    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .modelName("gemini-embedding-001")
                .build();
    }

    // Direct Driver for Autonomous Linking
    public Driver neo4jDriver(){
        return GraphDatabase.driver(System.getenv("NEO4J_URI"),
                AuthTokens.basic(System.getenv("NEO4J_USER"), System.getenv("NEO4J_PASSWORD"))
        );
    }

}
