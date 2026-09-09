#!/bin/bash

# Script to generate JSON files for all RRule instances

./mvnw -q -DskipTests compile

# Create temporary Java file for JSON generation
GEN_TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/rulescript-json.XXXXXX")
trap 'rm -rf "$GEN_TMP_DIR"' EXIT
cat > "$GEN_TMP_DIR/JsonGenerator.java" << 'EOF'
import org.qed.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;

public class JsonGenerator {
    public static void main(String[] args) throws Exception {
        String className = args[0];
        Class<?> clazz = Class.forName(className);
        RRule rule = (RRule) clazz.getDeclaredConstructor().newInstance();
        ObjectMapper mapper = new ObjectMapper();
        String fileName = rule.name() + "-" + rule.info() + ".json";
        ObjectNode jsonNode = rule.toJson();
        mapper.writerWithDefaultPrettyPrinter().writeValue(
            Path.of("tmp-rules", fileName).toFile(),
            jsonNode
        );
    }
}
EOF

MAVEN_CP_FILE="target/rulescript-classpath.txt"
./mvnw dependency:build-classpath -Dmdep.outputFile="$MAVEN_CP_FILE" -q
MAVEN_CP=$(<"$MAVEN_CP_FILE")
CLASSPATH="target/classes:${MAVEN_CP}"

# Compile the generator
javac -cp "$CLASSPATH" -d "$GEN_TMP_DIR" "$GEN_TMP_DIR/JsonGenerator.java"

# Generate JSON for each rule
find src/main/java/org/qed/RRuleInstances -name '*.java' | while read file; do
    className=$(echo "$file" | sed 's|src/main/java/||; s|/|.|g; s|\.java$||')
    echo "Generating JSON for: $className"
    java -cp "$GEN_TMP_DIR:$CLASSPATH" JsonGenerator "$className"
done

echo "JSON generation complete. Files are in tmp-rules/"
