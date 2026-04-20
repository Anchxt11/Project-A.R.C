//THIS FILE CONTAINS METHOD TO TAKE MEMORY DATA FROM REDIS AND TRANSFER IT TO NEO4J

package com.project.arc.memory.store;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import org.neo4j.driver.Values;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.project.arc.config.ArcConfig;


public class MemorySync {
    private final ChatModel arcBrain;
    private final ArcConfig config;

    public MemorySync(ArcConfig config) {
        this.config = config;
        this.arcBrain = config.geminiModel();
    }

    /**
     * Safely evicts the oldest messages from Redis while guaranteeing:
     *  1. We never cut inside a tool-call pair (AiMessage with requests + its ToolExecutionResultMessage)
     *  2. The history that remains always starts at a USER or SYSTEM message boundary
     *  3. Data evicted to L3 is a complete, self-consistent slice
     */
    public List<ChatMessage> getAndPrune(String sessionID, int maxL2Size) {
        List<ChatMessage> history = config.redisStore().getMessages(sessionID);

        if (history.size() < maxL2Size) {
            return Collections.emptyList();
        }

        System.out.println("ARC: L2 Capacity Exceeded. Calculating safe eviction boundary...");

        // Find the first safe cut point — must land on a USER message
        // so the retained history always begins cleanly.
        int targetEvict = history.size() - maxL2Size + 2; // aim to evict ~2 messages
        int cutIndex = findSafeCutIndex(history, targetEvict);

        if (cutIndex <= 0 || cutIndex >= history.size()) {
            // Cannot find a safe boundary — do nothing to avoid corrupting history
            System.out.println("ARC: No safe eviction boundary found. Skipping pruning to protect history integrity.");
            return Collections.emptyList();
        }

        List<ChatMessage> evicted  = new ArrayList<>(history.subList(0, cutIndex));
        List<ChatMessage> retained = new ArrayList<>(history.subList(cutIndex, history.size()));

        // Validate retained slice before writing — Gemini requires strict turn ordering
        if (!isHistoryValid(retained)) {
            System.out.println("ARC: Retained slice failed validation. Aborting pruning.");
            return Collections.emptyList();
        }

        // Atomic replace: delete then rewrite in the same store instance
        config.redisStore().deleteMessages(sessionID);
        config.redisStore().updateMessages(sessionID, retained);

        System.out.println("ARC: Evicted " + evicted.size() + " messages. " + retained.size() + " retained in L2.");
        return evicted;
    }

    /**
     * Finds the lowest index >= minCut that is safe to cut BEFORE.
     * Safe means: the message AT cutIndex is a USER message,
     * and the message just BEFORE cutIndex is NOT an AiMessage with pending tool calls.
     */
    private int findSafeCutIndex(List<ChatMessage> history, int minCut) {
        for (int i = minCut; i < history.size(); i++) {
            ChatMessage current = history.get(i);

            // Cut point must be at a USER message (clean conversation boundary)
            if (current.type() != ChatMessageType.USER) continue;

            // The message just before the cut must not be a dangling tool call
            ChatMessage prev = history.get(i - 1);
            if (isAiMessageWithToolCalls(prev)) continue;

            // Also verify no ToolExecutionResultMessage is orphaned just before
            if (prev.type() == ChatMessageType.TOOL_EXECUTION_RESULT) continue;

            return i; // Safe boundary found
        }
        return -1; // No safe boundary
    }

    /**
     * Validates that a message list has no broken tool-call pairs.
     * Gemini requires: AiMessage(toolCall) MUST be immediately followed
     * by ToolExecutionResultMessage(s), never by UserMessage or another AiMessage.
     */
    private boolean isHistoryValid(List<ChatMessage> messages) {
        for (int i = 0; i < messages.size() - 1; i++) {
            ChatMessage curr = messages.get(i);
            ChatMessage next = messages.get(i + 1);

            if (isAiMessageWithToolCalls(curr) && !(next instanceof ToolExecutionResultMessage)) {
                System.out.println("ARC Diagnostic: History validation failed at index " + i +
                        " — AiMessage with tool calls not followed by ToolExecutionResultMessage.");
                return false;
            }
        }
        return true;
    }

    private boolean isAiMessageWithToolCalls(ChatMessage msg) {
        return msg instanceof AiMessage ai && ai.hasToolExecutionRequests();
    }

    /**
     * On shutdown: flush entire L2 to L3, then clear Redis.
     */
    public void exitFlush(String sessionID) {
        List<ChatMessage> history = config.redisStore().getMessages(sessionID);
        if (history.isEmpty()) return;

        System.out.println("ARC: Clearing L2. Evicting all messages to L3");
        archiveToL3(history);
        config.redisStore().deleteMessages(sessionID);
        System.out.println("ARC: Session crystallized. L2 cache flushed.");
    }

    /**
     * Summarizes messages into knowledge triplets and writes them to Neo4j (L3).
     */
    public void archiveToL3(List<ChatMessage> messages) {
        if (messages.isEmpty()) return;

        // Filter to only USER and AI text messages — skip raw tool results
        // (they contain API payloads, not semantic knowledge)
        String overFlowMessages = messages.stream()
                .filter(msg -> msg.type() == ChatMessageType.USER
                        || msg.type() == ChatMessageType.AI)
                .filter(msg -> {
                    // Skip AiMessages that are pure tool-call requests (no text content)
                    if (msg instanceof AiMessage ai) {
                        return ai.text() != null && !ai.text().isBlank();
                    }
                    return true;
                })
                .map(msg -> String.format("[%s] %s: %s",
                        LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                        msg.type().name(),
                        msg))
                .collect(Collectors.joining("\n"));

        if (overFlowMessages.isBlank()) return;

        String triagePrompt = "Extract key facts from these messages into triplets. " +
                "TRIPLET: [Subject] -> (Relationship) -> [Object] | Context: 'Full descriptive sentence'" +
                "Example: TRIPLET: [Project ARC] -> (uses) -> [Redis] | Context: 'Project ARC uses Redis as its L2 persistent memory for conversation history.'";

        String triagedData = arcBrain.chat(triagePrompt + overFlowMessages);
        processTriage(triagedData);
        System.out.println("ARC: L2 pruned. L3 Graph updated with evicted context.");
    }

    public void processTriage(String triagedResult) {
        Pattern tripletPattern = Pattern.compile(
                "TRIPLET:\\s*\\[(.*?)]\\s*->\\s*\\((.*?)\\)\\s*->\\s*\\[(.*?)]\\s*\\|\\s*Context:\\s*['\"]?(.*?)['\"]?$",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
        );

        for (String line : triagedResult.split("\n")) {
            if (line.isBlank()) continue;
            Matcher matcher = tripletPattern.matcher(line.trim());

            if (matcher.find()) {
                String subject   = matcher.group(1).trim();
                String predicate = matcher.group(2).trim().toUpperCase().replace(" ", "_");
                String object    = matcher.group(3).trim();
                String context   = matcher.group(4).trim();

                updateGraphMemory(subject, predicate, object, context);
                updateVectorMemory(subject, object, context);
            } else if (line.trim().startsWith("TRIPLET:")) {
                System.out.println("ARC Diagnostic: Failed to parse triplet -> " + line.trim());
            }
        }
    }

    private void updateGraphMemory(String subject, String predicate, String object, String context) {
        try (var session = config.neo4jDriver().session()) {
            session.executeWrite(tx -> {
                String cypher = String.format(
                        "MERGE (s:Entity {name: $subject}) " +
                                "MERGE (o:Entity {name: $object}) " +
                                "MERGE (s)-[r:%s]->(o) " +
                                "SET r.context = $context, r.timestamp = datetime() " +
                                "RETURN r", predicate);
                return tx.run(cypher, Values.parameters(
                        "subject", subject,
                        "object", object,
                        "context", context
                )).consume();
            });
        }
    }

    private void updateVectorMemory(String subject, String object, String context) {
        String enrichedText = String.format("Relationship: %s and %s. Detail: %s", subject, object, context);
        TextSegment segment = TextSegment.from(enrichedText);
        config.neo4jStore().add(config.embeddingModel().embed(segment).content(), segment);
    }
}