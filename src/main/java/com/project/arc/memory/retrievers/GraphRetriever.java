/**
 * Protocol: Dynamic Cypher Execution & Code for retrieval through the GRAPH
 * Executes LLM-generated breadcrumb queries to explore the graph deeper.
 */

package com.project.arc.memory.retrievers;

import dev.langchain4j.rag.content.Content;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.project.arc.config.ArcConfig;


public class GraphRetriever {
    private final ArcConfig config;

    public GraphRetriever(ArcConfig config) {
        this.config = config;
    }

    public List<Content> retrieveThroughTraversal(String query) {

        String cypherQuery = "MATCH (n:MemoryNode)-[r]-(m:MemoryNode) " +
                "WHERE n.name CONTAINS $query OR m.name CONTAINS $query " +
                "RETURN n.name AS source, type(r) AS relationship, r.context AS detail, m.name AS target LIMIT 10";

        try (Session session = config.neo4jDriver().session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypherQuery, Map.of("query", query));
                return result.list().stream()
                        .map(record -> Content.from(
                                String.format("[%s] --(%s: %s)--> [%s]",
                                        record.get("source").asString(),
                                        record.get("relationship").asString(),
                                        record.get("detail").asString(),
                                        record.get("target").asString())))
                        .collect(Collectors.toList());
            });
        }
    }

    public List<Content> executeCustomCypher(String cypherQuery) {
        try (Session session = config.neo4jDriver().session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypherQuery);

                return result.list().stream()
                        .map(record -> {
                            if (record.size() == 4) {
                                return Content.from(String.format("[%s] --(%s: %s)--> [%s]",
                                        record.get(0).asString(),
                                        record.get(1).asString(),
                                        record.get(2).asString(),
                                        record.get(3).asString()));
                            }
                            //Fallback
                            return Content.from(record.values().stream()
                                    .map(value -> value.type().name().equals("STRING") ? value.asString() : value.toString())
                                    .collect(Collectors.joining(" | ")));
                        })
                        .collect(Collectors.toList());
            });
        } catch (Exception e) {
            System.err.println("ARC Error: Breadcrumb query failed. Check Cypher syntax: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
