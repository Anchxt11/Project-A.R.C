/*THIS FILE CONTAINS METHOD TO TAKE MEMORY DATA FROM REDIS AND TRANSFER IT TO NEO4J and CHROMADB
* ALSO THIS ONLY USES THE "VIBE" METHOD FOR NEO4J*/


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
    private final GoogleAiGeminiChatModel arcBrain;
    private final ArcConfig config;


    public MemorySync(ArcConfig config) {
        this.config = config;
        this.arcBrain = config.model();
    }


    // 1. Fetch the raw conversation from L2(redis)
    public String msgHistory(String sessionID){
        List<ChatMessage> history = config.redisStore().getMessages(sessionID);
        if(history.isEmpty()) return null;

        return history.stream()
                .map(msg-> String.format("[%s] %s: %s",
                        LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                        msg.type().name(),
                        msg))
                .collect(Collectors.joining("\n"));
    }

    // Transfers data from L2 (Redis) to L3 (Chroma/Neo4j)
    public void executeMemorySync(String sessionID) {

        // 1. Perform Triage & Crystallization
        String triagePrompt = "Analyze this conversation history. Extract knowledge into a unified Graph structure." +
                "For each piece of information, provide a triplet in the format:" +
                "TRIPLET: [Subject] -> (Relationship) -> [Object] | Context: 'Full descriptive sentence'" +
                "Example: TRIPLET: [Project ARC] -> (uses) -> [Redis] | Context: 'Project ARC uses Redis as its L2 persistent memory for conversation history.'";

        String triagedData = arcBrain.generate(triagePrompt + msgHistory(sessionID));
        processTriage(triagedData);

        // 3. Maintenance: Flush L2 to keep system fast
        config.redisStore().deleteMessages(sessionID);
        System.out.println("ARC: Session crystallized. L2 cache flushed.");

    }

    private void processTriage(String triagedResult){
        Pattern tripletPattern = Pattern.compile("TRIPLET:\\s*\\[(.*?)\\]\\s*->\\s*\\((.*?)\\)\\s*->\\s*\\[(.*?)\\]\\s*\\|\\s*Context:\\s*'(.*?)'");

        String[] lines = triagedResult.split("\n");

        for (String line : lines) {
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
