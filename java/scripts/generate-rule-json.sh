#!/bin/bash

cat > JsonGenerator.java << 'EOF'
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

MAVEN_CP=$(./mvnw dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)
CLASSPATH="target/classes:${MAVEN_CP}"

javac -cp "$CLASSPATH" JsonGenerator.java

find src/main/java/org/qed/RRuleInstances -name '*.java' | while read file; do
    className=$(echo "$file" | sed 's|src/main/java/||; s|/|.|g; s|\.java$||')
    echo "Generating JSON for: $className"
    java -cp ".:$CLASSPATH" JsonGenerator "$className"
done

rm -f JsonGenerator.java JsonGenerator.class

echo "JSON generation complete. Files are in tmp-rules/"
