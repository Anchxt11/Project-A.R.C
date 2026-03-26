package com.project.arc;

import com.project.arc.config.ArcAssistant;
import com.project.arc.config.ArcConfig;
import com.project.arc.memory.retrievers.MemoryRetrievalService;
import com.project.arc.memory.store.MemorySync;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;

import java.util.Scanner;
import java.util.UUID;

public class ArcLauncher {
    public static void main(String[] args) {
        ArcConfig config = new ArcConfig();
        MemoryRetrievalService memoryRetrievalService = new MemoryRetrievalService(config, config.model());
        MemorySync syncEngine = new MemorySync(config);

        ArcAssistant ARC = AiServices.builder(ArcAssistant.class)
                .chatLanguageModel(config.model())
                .tools(new ArcTools(memoryRetrievalService))
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .chatMemoryStore(config.redisStore())
                        .maxMessages(20)
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
                syncEngine.executeMemorySync(sessionID);
                System.out.println("ARC: Offline. Safe travels, Sir.");
                break;
            }

            if (input.toLowerCase().contains("deep scan")) {
                System.out.println("\nARC: " + memoryRetrievalService.deepScan(input));
            } else {
                System.out.println("\nARC: " + ARC.chat(sessionID, input));
            }

        }
    }
}
