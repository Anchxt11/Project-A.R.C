package com.project.arc.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import dev.langchain4j.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
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

    // 3. L3 Semantic: ChromaDB (Vector store)
    public ChromaEmbeddingStore chromaStore() {
        return ChromaEmbeddingStore.builder()
                .baseUrl("http://localhost:8000")
                .collectionName("project-arc-knowledge")
                .build();
    }

    // 4. L3 Relational: Neo4j (Graph Store)
    public Neo4jEmbeddingStore neo4jStore() {
        return Neo4jEmbeddingStore.builder()
                .withBasicAuth("bolt://localhost:7687", "neo4j", "password")
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
        return GraphDatabase.driver("bolt://localhost:7687",
                AuthTokens.basic("neo4j", "password")
        );
    }
}
