package org.qed.Backends.Cockroach;

import org.qed.ProjectPaths;
import org.qed.RRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

public class CockroachTester {
    public static final String RULE_PACKAGE = "org.qed.RRuleInstances";

    public static Path rulePath =
            ProjectPaths.baseDir().resolve("src/main/java/org/qed/RRuleInstances");
    public static Path generatedPath =
            ProjectPaths.baseDir().resolve("src/main/java/org/qed/Backends/Cockroach/Generated");

    public static java.util.List<RRule> ruleList() {
        var files = rulePath.toFile().listFiles((dir, name) -> name.endsWith(".java"));
        var rules = new ArrayList<RRule>();
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                var className = file.getName().replace(".java", "");
                try {
                    rules.add((RRule) Class.forName(RULE_PACKAGE + "." + className).getConstructor().newInstance());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load rule: " + className, e);
                }
            }
        }
        return rules;
    }

    public static void main(String[] args) throws IOException {
        var only = Set.of(args);
        Files.createDirectories(generatedPath);
        var requested = 0;
        var generated = 0;
        for (var rule : ruleList()) {
            if (!only.isEmpty() && !only.contains(rule.name())) {
                continue;
            }
            requested++;
            var file = generatedPath.resolve(rule.name() + ".json");
            try {
                Files.writeString(file, new CockroachGenerator().generate(rule));
                generated++;
            } catch (UnsupportedOperationException e) {
                Files.deleteIfExists(file);
                System.out.println("unsupported " + rule.name() + ": " + e.getMessage());
            }
        }
        System.out.printf("Generated %d/%d rules into %s%n", generated, requested, generatedPath);
    }
}
