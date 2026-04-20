package com.project.arc;

import com.project.arc.Automation.ArcTools;
import com.project.arc.Automation.AutomationService;
import com.project.arc.Automation.FileSystemTools;
import com.project.arc.Automation.WebTool;
import com.project.arc.config.ArcAssistant;
import com.project.arc.config.ArcConfig;
import com.project.arc.config.MCPToolProvider;
import com.project.arc.memory.retrievers.MemoryRetrievalService;
import com.project.arc.memory.store.MemorySync;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ARC Launcher: The central command hub for Project A.R.C.
 * Handles system initialization, AI service orchestration, and synchronized UI loops.
 */
public class ArcLauncher {
    public static void main(String[] args) {

        // 1. Initialize Core Services
        ArcConfig config = new ArcConfig();
        MemoryRetrievalService memoryRetrievalService = new MemoryRetrievalService(config);
        MemorySync syncEngine = new MemorySync(config);
        AutomationService automationService = new AutomationService();
        ArcTools arcTools = new ArcTools(automationService);
        FileSystemTools fileSystemTools = new FileSystemTools();
        MCPToolProvider mcpTool = new MCPToolProvider();
        WebTool webTool = new WebTool(automationService);
        Scanner scanner = new Scanner(System.in);


        // 2. Build ARC Assistant Service
        ArcAssistant ARC = AiServices.builder(ArcAssistant.class)
                .streamingChatModel(config.streamingGeminiModel())
                .chatModel(config.ollamaModel())
                .tools(arcTools, fileSystemTools)
                .toolProvider(mcpTool.mcpToolProvider())
                .beforeToolExecution(event -> {
                    ToolExecutionRequest toolRequest = event.request();
                    // Intercept and authorize any modification attempts
                    if (toolRequest.name().contains("write") || toolRequest.name().contains("append")) {
                        authorizeModification(toolRequest);
                    }
                })
                .afterToolExecution(event -> {
                    Object result = event.result();
                    if (result != null) {
                        System.out.println("\n[PROTOCOL RESULT]: " + result.toString());
                    }
                })
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .chatMemoryStore(config.redisStore())
                        .maxMessages(10)
                        .build())
                .retrievalAugmentor(memoryRetrievalService.getAugmenter())
                .build();

        String sessionID = UUID.randomUUID().toString();
        System.out.println("ARC: All sectors online. Protocols stabilized. Standing by, Boss.");
        System.out.print("> ");

        // 3. Main Command Loop
        while (true) {
            String input = scanner.nextLine();

            // Handle System Shutdown
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("shutdown")) {
                System.out.println("ARC: Crystallizing session before shutdown...");
                syncEngine.exitFlush(sessionID);
                config.neo4jDriver().close();
                System.out.println("ARC: Offline. Safe travels, Sir.");
                break;
            }

            // Synchronization primitive to keep the AI and the Scanner from fighting for focus
            CompletableFuture<Void> turnComplete = new CompletableFuture<>();

            ARC.chat(sessionID, input)
                    .onPartialResponse(token -> {
                        System.out.print(token);
                        System.out.flush();
                    })
                    .onCompleteResponse(tokenResponse -> {
                        System.out.println();

                        // Perform L2 Maintenance ONLY after the AI has finished its response
                        List<ChatMessage> evictedMessages = syncEngine.getAndPrune(sessionID, config.MEMORY_THRESHOLD);
                        if (!evictedMessages.isEmpty()) {
                            // Archive to L3 immediately to keep logs ordered
                            syncEngine.archiveToL3(evictedMessages);
                        }

                        // Release the main thread to allow the next user input
                        System.out.print("\n> ");
                        turnComplete.complete(null);
                    })
                    .onError(t -> {
                        System.err.println("\nARC DIAGNOSTIC ERROR: " + t.getMessage());
                        System.out.print("\n> ");
                        turnComplete.complete(null);
                    })
                    .start();

            // BLOCK the main thread until the AI turn is fully resolved
            turnComplete.join();
        }
    }


     //Manual Override Protocol: Requires user input to proceed with file system modifications.
    public static void authorizeModification(ToolExecutionRequest request) {
        // Synchronized block ensures we don't have multiple prompts overlapping
        synchronized (System.in) {
            System.out.println("\n\nARC: Boss, I am requesting permission to modify a file.");
            System.out.println("Protocol: " + request.name());
            System.out.println("Arguments: " + request.arguments());
            System.out.print("Authorize modification? (Yes/No): ");

            Scanner authScanner = new Scanner(System.in);
            if (authScanner.hasNextLine()) {
                String choice = authScanner.nextLine();
                if (!choice.equalsIgnoreCase("yes")) {
                    throw new RuntimeException("Protocol Denied: Manual override by Boss.");
                }
                System.out.println("ARC: Authorization confirmed. Resuming...\n");
            }
        }
    }
}