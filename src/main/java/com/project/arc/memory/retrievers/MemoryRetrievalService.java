/*THIS FILE CONTAINS CODE FOR RETRIEVING DATA FROM CHROMADB OR NEO4J*/


package com.project.arc.memory.retrievers;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;

import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.project.arc.config.ArcConfig;
import com.project.arc.config.prompts;

public class MemoryRetrievalService {
    private final RetrievalAugmentor augmentor;
    private final ArcConfig config;

    public MemoryRetrievalService(ArcConfig config, GoogleAiGeminiChatModel routingModel){
        this.config = config;

        // 1. Initialize Semantic Retriever (ChromaDB)
        ContentRetriever semanticRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(config.chromaStore())
                .embeddingModel(config.embeddingModel())
                .maxResults(3)
                .build();

        // 2. Initialize Structural Retriever (Neo4j)
        ContentRetriever graphRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(config.neo4jStore())
                .embeddingModel(config.embeddingModel())
                .maxResults(3)
                .build();

        // 3. Define Routing Logic
        Map<ContentRetriever, String> selection = new HashMap<>();
        selection.put(semanticRetriever,
                "Personal notes, technical documentation, code snippets, and general facts." +
                        "Use this for retrieving the 'what' and 'content' of any saved information.");
        selection.put(graphRetriever,
                "Use this for mapping relationships, class hierarchies, file dependencies, and project " +
                        "structure. It contains the architectural 'how' and the links between different entities.");

        // Building the query Router
        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatLanguageModel(routingModel)
                .retrieverToDescription(selection)
                .build();

        //4. Central Router
        this.augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(router)
                .build();

    }

    public RetrievalAugmentor getAugmenter() {
        return this.augmentor;
    }

    public String deepScan(String userQuery) {
        System.out.println("ARC: Initiating Deep Scan Protocol...");
        GraphRetriever graphRetriever = new GraphRetriever(config);


        // 1. PHASE 1: Traversal
        List<Content> initialFindings = graphRetriever.retrieveThroughTraversal(userQuery);

        // 2. PHASE 2: Breadcrumb
        String breadcrumbAction = config.model().generate(
                String.format(prompts.BREADCRUMB_PROMPT, initialFindings.toString())
        );

        if (breadcrumbAction.contains("SUGGESTION:")) {
            String newCypher = breadcrumbAction.split("SUGGESTION:")[1].trim();
            initialFindings.addAll(graphRetriever.executeCustomCypher(newCypher));
        }

        // 3. PHASE 3: Explorer
        return config.model().generate(
                String.format(prompts.GRAPH_EXPLORER_PROMPT, initialFindings)
                + "\nUser Query: " + userQuery
        );
    }
}

