//THIS FILE CONTAINS METHOD TO TAKE MEMORY DATA FROM REDIS AND TRANSFER IT TO NEO4J

package com.project.arc.memory.store;

import dev.langchain4j.data.message.ChatMessage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.project.arc.config.ArcConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.neo4j.driver.Values;


public class MemorySync {
    //Defining Variables
    private final GoogleAiGeminiChatModel arcBrain;
    private final ArcConfig config;

    //Constructing Variables
    public MemorySync(ArcConfig config) {
        this.config = config;
        this.arcBrain = config.model();
    }

    //Finding fixed messges out of L1 Window and adding them to L3
    public void maintainSlidingWindow(String sessionID, int Max_l2Size){
        List<ChatMessage> history = config.redisStore().getMessages(sessionID);

        if (history.size() < Max_l2Size) {
            return;
        }
        System.out.println("ARC: L2 Capacity Exceeded. Evicting oldest" + (history.size() - Max_l2Size)+"messages to L3");

        int evictionCount = history.size() - Max_l2Size;
        List<ChatMessage> evictedMessages = history.subList(0, evictionCount);

        String overFlowMessages = evictedMessages.stream()
                .map(msg -> String.format("[%s] %s: %s",
                        LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                        msg.type().name(),
                        msg))
                .collect(Collectors.joining("\n"));

        String triagePrompt = "The following messages are being evicted from short-term memory. Extract knowledge into a unified Graph structure." +
                "For each piece of information, provide a triplet in the format:" +
                "TRIPLET: [Subject] -> (Relationship) -> [Object] | Context: 'Full descriptive sentence'" +
                "Example: TRIPLET: [Project ARC] -> (uses) -> [Redis] | Context: 'Project ARC uses Redis as its L2 persistent memory for conversation history.'";

        String triagedData = arcBrain.generate(triagePrompt + overFlowMessages);
        processTriage(triagedData);



        System.out.println("ARC: L2 pruned. L3 Graph updated with evicted context.");

    }

    //Finding all messges out of L1 Window and adding them to L3
    public void maintainSlidingWindow(String sessionID){
        List<ChatMessage> history = config.redisStore().getMessages(sessionID);

        if (history.isEmpty()) return;

        System.out.println("ARC: Clearing L2. Evicting all messages to L3");

        String msges = history.stream()
                .map(msg -> String.format("[%s] %s: %s",
                        LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                        msg.type().name(),
                        msg))
                .collect(Collectors.joining("\n"));

        String triagePrompt = "Analyze this conversation history. Extract knowledge into a unified Graph structure." +
                "For each piece of information, provide a triplet in the format:" +
                "TRIPLET: [Subject] -> (Relationship) -> [Object] | Context: 'Full descriptive sentence'" +
                "Example: TRIPLET: [Project ARC] -> (uses) -> [Redis] | Context: 'Project ARC uses Redis as its L2 persistent memory for conversation history.'";

        String triagedData = arcBrain.generate(triagePrompt + msges);
        processTriage(triagedData);


        // 3. Maintenance: Flush L2 to keep system fast
        config.redisStore().deleteMessages(sessionID);
        System.out.println("ARC: Session crystallized. L2 cache flushed.");

    }

    //Processing data and adding to L3
    public void processTriage(String triagedResult){
        Pattern tripletPattern = Pattern.compile("TRIPLET:\\s*\\[(.*?)]\\s*->\\s*\\((.*?)\\)\\s*->\\s*\\[(.*?)]\\s*\\|\\s*Context:\\s*['\"]?(.*?)['\"]?$",
                Pattern.MULTILINE| Pattern.CASE_INSENSITIVE
        );

        String[] lines = triagedResult.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            Matcher matcher = tripletPattern.matcher(line.trim());

            if (matcher.find()) {
                String subject = matcher.group(1).trim();
                String predicate = matcher.group(2).trim().toUpperCase().replace(" ", "_");
                String object = matcher.group(3).trim();
                String context = matcher.group(4).trim();

                // 1. Update the Graph (Relational Memory)
                updateGraphMemory(subject, predicate, object, context);

                // 2. Update the Vector Store (Semantic Memory)
                updateVectorMemory(subject, object, context);
            } else if (line.trim().startsWith("TRIPLET:")) {
                System.out.println("ARC Diagnostic: Failed to parse potential triplet ->" + line.trim());
            }
        }
    }

    /**
     * Executes a Cypher query to create or merge nodes and their relationship.
     * This ensures the graph is always connected.
     */
    private void updateGraphMemory(String subject, String predicate, String object, String context) {
        try(var session = config.neo4jDriver().session()) {
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

    //Embeds the descriptive context and stores it in the Neo4j vector index.
    private void updateVectorMemory(String subject, String object, String context) {
        String enrichedText = String.format("Relationship: %s and %s. Detail: %s", subject, object, context);
        TextSegment segment = TextSegment.from(enrichedText);
        config.neo4jStore().add(config.embeddingModel().embed(segment).content(), segment);
    }
}
