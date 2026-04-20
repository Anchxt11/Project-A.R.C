package com.project.arc;

import com.project.arc.Automation.ArcTools;
import com.project.arc.Automation.AutomationService;
import com.project.arc.Automation.FileSystemTools;
import com.project.arc.Automation.MediaTools;
import com.project.arc.Automation.ScreenshotTool;
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

    private static Scanner scanner;

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
        ScreenshotTool screenshotTool = new ScreenshotTool(config.visionModel(), automationService);
        MediaTools mediaTools = new MediaTools(automationService); // NEW

        scanner = new Scanner(System.in);

        // 2. Build ARC Assistant Service
        ArcAssistant ARC = AiServices.builder(ArcAssistant.class)
                .streamingChatModel(config.streamingGeminiModel())
                .chatModel(config.geminiModel())
                // Added mediaTools to the tool list
                .tools(arcTools, fileSystemTools, webTool, screenshotTool, mediaTools)
                .toolProvider(mcpTool.mcpToolProvider())
                .beforeToolExecution(event -> {
                    ToolExecutionRequest toolRequest = event.request();
                    if (toolRequest.name().contains("write") || toolRequest.name().contains("append")) {
                        authorizeModification(toolRequest, scanner);
                    }
                })
                .afterToolExecution(event -> {
                    String toolName = event.request().name();
                    // Don't echo raw output for search or vision — ARC summarizes these
                    if (!toolName.equalsIgnoreCase("searchWeb")
                            && !toolName.equalsIgnoreCase("screenshotAndAnalyze")) {
                        Object result = event.result();
                        if (result != null) {
                            System.out.println("\n[PROTOCOL RESULT]: " + result);
                        }
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
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                System.out.print("> ");
                continue;
            }

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("shutdown")) {
                System.out.println("ARC: Crystallizing session before shutdown...");
                syncEngine.exitFlush(sessionID);
                config.neo4jDriver().close();
                scanner.close();
                System.out.println("ARC: Offline. Safe travels, Sir.");
                break;
            }

            if (input.equalsIgnoreCase("flush")) {
                config.redisStore().deleteMessages(sessionID);
                System.out.println("ARC: L2 memory wiped. Starting fresh context, Boss.");
                System.out.print("> ");
                continue;
            }

            CompletableFuture<Void> turnComplete = new CompletableFuture<>();

            ARC.chat(sessionID, input)
                    .onPartialResponse(token -> {
                        System.out.print(token);
                        System.out.flush();
                    })
                    .onCompleteResponse(tokenResponse -> {
                        System.out.println();

                        List<ChatMessage> evictedMessages = syncEngine.getAndPrune(
                                sessionID, config.MEMORY_THRESHOLD);
                        if (!evictedMessages.isEmpty()) {
                            syncEngine.archiveToL3(evictedMessages);
                        }

                        System.out.print("\n> ");
                        turnComplete.complete(null);
                    })
                    .onError(t -> {
                        System.err.println("\nARC DIAGNOSTIC ERROR: "
                                + t.getClass().getSimpleName() + ": " + t.getMessage());
                        t.printStackTrace(System.err);
                        System.out.print("\n> ");
                        turnComplete.complete(null);
                    })
                    .start();

            turnComplete.join();
        }
    }

    public static void authorizeModification(ToolExecutionRequest request, Scanner sharedScanner) {
        synchronized (System.in) {
            System.out.println("\n\nARC: Boss, I am requesting permission to modify a file.");
            System.out.println("Protocol:  " + request.name());
            System.out.println("Arguments: " + request.arguments());
            System.out.print("Authorize modification? (Yes/No): ");

            if (sharedScanner.hasNextLine()) {
                String choice = sharedScanner.nextLine().trim();
                if (!choice.equalsIgnoreCase("yes")) {
                    throw new RuntimeException("Protocol Denied: Manual override by Boss.");
                }
                System.out.println("ARC: Authorization confirmed. Resuming...\n");
            }
        }
    }
}