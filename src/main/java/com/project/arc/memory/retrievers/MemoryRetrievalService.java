/*THIS FILE CONTAINS CODE FOR RETRIEVING DATA FROM NEO4J*/


package com.project.arc.memory.retrievers;


import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;

import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.project.arc.config.ArcConfig;
import com.project.arc.config.prompts;

public class MemoryRetrievalService {
    private final RetrievalAugmentor augmentor;
    private final ArcConfig config;

    public MemoryRetrievalService(ArcConfig config){
        this.config = config;

        // 2. Initialize Structural Retriever (Neo4j)
        ContentRetriever shallowRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(config.neo4jStore())
                .embeddingModel(config.embeddingModel())
                .maxResults(3)
                .build();

        ContentRetriever deepRetriever = query -> {
            String sitrep = deepScan(query.text());
            return Collections.singletonList(Content.from(sitrep));
        };

        // 3. Define Routing Logic
        Map<ContentRetriever, String> routingRules = new HashMap<>();
        routingRules.put(shallowRetriever,
                "Personal notes, technical documentation, code snippets, and general and simple facts." +
                        "Use this for 90% of the queries.");
        routingRules.put(deepRetriever,
                "Use ONLY for mapping complex historical relationships, class hierarchies, and entity links. " +
                        "Use ONLY for architectural deep-dives into our local Neo4j graph." +
                        "DO NOT use this for current system status, file system navigation, or active directory queries. " +
                        "If the user asks what you can 'do' or 'see' right now, DO NOT use this retriever." +
                        "DO NOT use for routine file updates or status checks.");

        // Building the query Router
        var router = LanguageModelQueryRouter.builder()
                .chatModel(config.ollamaModel())
                .retrieverToDescription(routingRules)
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
        String breadcrumbAction = config.geminiModel().chat(
                String.format(prompts.BREADCRUMB_PROMPT, initialFindings.toString())
        );

        if (breadcrumbAction.contains("SUGGESTION:")) {
            String newCypher = breadcrumbAction.split("SUGGESTION:")[1].trim();
            initialFindings.addAll(graphRetriever.executeCustomCypher(newCypher));
        }

        // 3. PHASE 3: Explorer
        return config.geminiModel().chat(
                String.format(prompts.GRAPH_EXPLORER_PROMPT, initialFindings)
                + "\nUser Query: " + userQuery
        );
    }
}

