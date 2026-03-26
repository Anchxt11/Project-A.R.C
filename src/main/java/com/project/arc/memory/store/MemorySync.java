/*THIS FILE CONTAINS METHOD TO TAKE MEMORY DATA FROM REDIS AND TRANSFER IT TO NEO4J and CHROMADB
* ALSO THIS ONLY USES THE "VIBE" METHOD FOR NEO4J*/


package com.project.arc.memory.store;

import dev.langchain4j.data.message.ChatMessage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.project.arc.config.ArcConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;


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
        String triagePrompt = "Boss, I am analyzing our last session. " +
                "Extract any technical notes, project goals, or code logic as 'FACTS'. (e.g., 'User prefers dark mode', 'Project deadline is Friday'). " +
                "Extract any class relationships or teammate roles as 'CONNECTIONS'. (e.g., 'User is working on ArcConfig.java', 'Naman is a teammate'). " +
                "Conversation:\n";

        String triagedData = arcBrain.generate(triagePrompt + msgHistory(sessionID));
        processTriage(triagedData);

        // 2. Execute Autonomous Graph Linking
        GraphSchema graphSchema = new GraphSchema(config);
        graphSchema.executeAutonomousLinking(msgHistory(sessionID));

        // 3. Maintenance: Flush L2 to keep system fast
        config.redisStore().deleteMessages(sessionID);
        System.out.println("ARC: Session crystallized. L2 cache flushed.");

    }

    private void processTriage(String triagedResult){
        // 1. Identify the 'Sectors' from Gemini's response
        for (String line: triagedResult.split("\n")) {
            String content = line.substring(line.indexOf(":") + 1).trim();

            String chunkID = UUID.randomUUID().toString();
            TextSegment segment = TextSegment.from(content);
            segment.metadata().put("chunk_id", chunkID);

            if (line.startsWith("FACT:")) {
                crystallizeSemantic(segment); // Route to CHROMA
            } else if (line.startsWith("CONNECTION:")){
                crystallizeRelational(segment); // Route to NEO4J

            }
        }
    }
    public void crystallizeSemantic(TextSegment segment){
        dev.langchain4j.data.embedding.Embedding embedding = config.embeddingModel().embed(segment).content();

        config.chromaStore().add(embedding, segment);
    }
    public void crystallizeRelational(TextSegment segment){
        dev.langchain4j.data.embedding.Embedding embedding = config.embeddingModel().embed(segment).content();

        config.neo4jStore().add(embedding, segment);
    }


}
