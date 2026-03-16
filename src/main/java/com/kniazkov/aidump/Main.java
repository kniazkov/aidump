package com.kniazkov.aidump;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar aidump.jar <path-to-maven-project>");
            System.exit(1);
        }

        Path projectRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path pomPath = projectRoot.resolve("pom.xml");

        if (!Files.isDirectory(projectRoot)) {
            System.err.println("The specified path is not a directory: " + projectRoot);
            System.exit(2);
        }

        if (!Files.exists(pomPath)) {
            System.err.println("pom.xml not found in: " + projectRoot);
            System.exit(3);
        }

        Model.ProjectPom projectPom = ProjectPomReader.read(projectRoot);
        Model.LibraryDump dump = ProjectParser.parse(projectRoot, projectPom);

        String outputFileName = projectRoot.getFileName().toString() + ".json";
        Path outputPath = Paths.get(outputFileName).toAbsolutePath().normalize();

        ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        mapper.writeValue(outputPath.toFile(), dump);

        System.out.println("JSON written to: " + outputPath);
    }
}
