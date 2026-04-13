package com.project.arc;

import com.project.arc.config.ArcAssistant;
import com.project.arc.config.ArcConfig;
import com.project.arc.memory.retrievers.MemoryRetrievalService;
import com.project.arc.memory.store.MemorySync;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;

import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ArcLauncher {
    public static void main(String[] args) {

        int MAX_L2_MESSAGES = 10; // The desired threshold


        ArcConfig config = new ArcConfig();
        MemoryRetrievalService memoryRetrievalService = new MemoryRetrievalService(config);
        MemorySync syncEngine = new MemorySync(config);

        ArcAssistant ARC = AiServices.builder(ArcAssistant.class)
                .chatLanguageModel(config.model())
                //.tools(new ArcTools(memoryRetrievalService))
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .chatMemoryStore(config.redisStore())
                        .maxMessages(10)
                        .build())
                .retrievalAugmentor(memoryRetrievalService.getAugmenter())
                .build();

        String sessionID = UUID.randomUUID().toString();
        Scanner scanner = new Scanner(System.in);
        System.out.println("ARC: All sectors online. Protocols stabilized. Standing by, Boss.");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine();

            if(input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("shutdown")) {
                System.out.println("ARC: Crystallizing session before shutdown...");
                syncEngine.maintainSlidingWindow(sessionID);
                System.out.println("ARC: Offline. Safe travels, Sir.");
                break;
            }

            System.out.println("\nARC: " + ARC.chat(sessionID, input));
            CompletableFuture.runAsync(() -> {
                syncEngine.maintainSlidingWindow(sessionID, MAX_L2_MESSAGES);
            });
        }
    }
}
