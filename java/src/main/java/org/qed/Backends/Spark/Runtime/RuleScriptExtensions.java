package org.qed.spark;

import org.apache.spark.sql.SparkSessionExtensions;
import scala.Function1;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class RuleScriptExtensions extends AbstractFunction1<SparkSessionExtensions, BoxedUnit>
        implements Function1<SparkSessionExtensions, BoxedUnit> {
    @Override
    public BoxedUnit apply(SparkSessionExtensions extensions) {
        var dir = System.getProperty("rulescript.dir", System.getenv("RULESCRIPT_DIR"));
        if (dir != null) {
            install(extensions, load(Path.of(dir)));
        }
        return BoxedUnit.UNIT;
    }

    public static List<RuleScriptRule> load(Path dir) {
        var rules = new ArrayList<RuleScriptRule>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.toString().endsWith(".json")).sorted().forEach(f -> rules.add(RuleScriptRule.load(f)));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot list " + dir, e);
        }
        return rules;
    }

    public static void installAfterAnalysis(SparkSessionExtensions extensions, List<RuleScriptRule> rules) {
        for (var rule : rules) {
            extensions.injectPostHocResolutionRule(session -> rule);
        }
    }

    public static void install(SparkSessionExtensions extensions, List<RuleScriptRule> rules) {
        for (var rule : rules) {
            extensions.injectOptimizerRule(session -> rule);
        }
    }
}
