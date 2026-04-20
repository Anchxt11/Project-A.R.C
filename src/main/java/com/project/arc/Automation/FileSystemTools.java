package com.project.arc.Automation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileSystemTools {

    private final String userHome = System.getProperty("user.home");
    private final String projectDir = System.getProperty("user.dir");

    private Path resolvePath(String folder) {
        return switch (folder.toLowerCase()) {
            case "downloads" -> Paths.get(userHome, "Downloads");
            case "documents" -> Paths.get(userHome, "Documents");
            case "pictures" -> Paths.get(userHome, "Pictures");
            default -> Paths.get(projectDir);
        };
    }

    @Tool("Lists the directory sectors that ARC has authorization to access.")
    public List<String> listAccessibleSectors() {
        return Arrays.asList(
                "Downloads (" + userHome + "\\Downloads)",
                "Documents (" + userHome + "\\Documents)",
                "Pictures (" + userHome + "\\Pictures)",
                "Project Root (" + projectDir + ")"
        );
    }

    @Tool("Lists all files in a specific authorized sector (downloads, documents, pictures, or project)")
    public List<String> listFiles(String folder) throws IOException {
        Path path = resolvePath(folder);
        try (Stream<Path> stream = Files.list(path)) {
            return stream.map(p -> p.getFileName().toString()).collect(Collectors.toList());
        }
    }

    @Tool("Reads the content of a file from a specific authorized sector")
    public String readFile(String folder, String fileName) throws IOException {
        Path filePath = resolvePath(folder).resolve(fileName);
        return Files.readString(filePath);
    }

    @Tool("Writes new content to a file. Warning: Overwrites existing data.")
    public String writeFile(
            @P("The sector name: downloads, documents, pictures, or project") String folder,
            @P("The name of the file to be created, e.g., 'test.txt'") String fileName,
            @P("The text data to be written into the file") String content) throws IOException {
        Path filePath = resolvePath(folder).resolve(fileName);
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return "SUCCESS: Data written to " + fileName + " in sector " + folder;
    }

    @Tool("Appends content to an existing file.")
    public String appendToFile(
            @P("The sector name: downloads, documents, pictures, or project") String folder,
            @P("The name of the file") String fileName,
            @P("The text data to append") String content) throws IOException {

        Path filePath = resolvePath(folder).resolve(Paths.get(fileName).getFileName().toString());
        Files.writeString(filePath, "\n" + content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return "SUCCESS: Content appended to " + fileName;
    }
}