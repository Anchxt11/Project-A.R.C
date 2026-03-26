/*THIS FILE CONTAINS THE METHODS TO CREATE THE RELATIONAL/GRAPH THE NODES AND EDGES OF THE REDIS CONVERSATION.*/

package com.project.arc.memory.store;

import com.project.arc.config.ArcConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.neo4j.driver.Session;


public class GraphSchema {
    private final GoogleAiGeminiChatModel arcBrain;
    private final ArcConfig config;
    private static final String GRAPH_ARCHITECT_PROMPT =
            "You are the ARC Graph Architect. Analyze the following conversation history and " +
                    "extract structured relationships for a Neo4j database.\n" +
                    "Rules:\n" +
                    "- Format every relationship as a Cypher MERGE statement.\n" +
                    "- Use labels: Person, File, Project, Concept.\n" +
                    "- Example: MERGE (a:Person {name: 'Naman'}) MERGE (b:Project {name: 'ARC'}) MERGE (a)-[:CONTRIBUTES_TO]->(b);\n" +
                    "Conversation:\n%s";

    public GraphSchema(ArcConfig config){
        this.config = config;
        this.arcBrain = config.model();
    }

    public void executeAutonomousLinking(String transcript){
        // 1. Ask Gemini to design the Graph
        String cypherCommands = arcBrain.generate(
                String.format(GRAPH_ARCHITECT_PROMPT, transcript));

        // 2. Execute Protocols (This builds the nodes and edges)
        try (Session session = config.neo4jDriver().session()){
            session.executeWrite(tx -> {
                for (String command: cypherCommands.split(";")){
                    if (!command.isBlank()) {
                        tx.run(command.trim());
                    }
                }
                return  null;
            });
            System.out.println("ARC: Graph synchronization complete. Relationships established.");
        }
    }
}
