package org.qed.spark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GenericRulesTest {
    private static final int INSTANCES = 6;

    @TestFactory
    List<DynamicTest> everyGeneratedRuleIsSound() throws IOException {
        var tests = new ArrayList<DynamicTest>();
        try (Stream<java.nio.file.Path> files = Files.list(Support.generated())) {
            files.filter(f -> f.toString().endsWith(".json")).filter(f -> selected(f)).sorted().forEach(file -> {
                var name = file.getFileName().toString().replace(".json", "");
                tests.add(DynamicTest.dynamicTest(name, () -> {
                    try {
                        check(file);
                    } catch (org.opentest4j.TestAbortedException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new AssertionError("RULE " + name + ": " + e, e);
                    }
                }));
            });
        }
        return tests;
    }

    private static boolean selected(java.nio.file.Path file) {
        var only = System.getenv("RULE");
        return only == null || java.util.Set.of(only.split(",")).contains(file.getFileName().toString().replace(".json", ""));
    }

    private void check(java.nio.file.Path file) throws IOException {
        var rule = RuleScriptRule.load(file);
        var pattern = new ObjectMapper().readTree(Files.readString(file)).path("from");
        for (int seed = 0; seed < INSTANCES; seed++) {
            org.apache.spark.sql.catalyst.plans.logical.LogicalPlan before;
            try {
                before = new Instantiate(seed).plan(pattern);
            } catch (Instantiate.Unsupported e) {
                Assumptions.abort("cannot instantiate the pattern: " + e.getMessage());
                return;
            }
            assertEquals(true, rule.matches(before), rule.name() + " did not match its own pattern:\n" + before);
            var after = rule.apply(before);
            assertNotNull(after);
            if (System.getenv("PLANS") != null && seed == 0) {
                System.out.println("PLAN " + rule.name() + "\n" + before + "\n=>\n" + after);
            }
            if (after.fastEquals(before)) {
                continue;
            }
            assertEquals(Catalyst.list(before.output()).stream().map(a -> a.exprId()).toList(),
                    Catalyst.list(after.output()).stream().map(a -> a.exprId()).toList(),
                    rule.name() + " changed the output of the rewritten node");
            if (!Support.canExecute()) {
                continue;
            }
            java.util.List<String> expected;
            try {
                expected = Support.run(before);
            } catch (Exception e) {
                if (!(e instanceof org.apache.spark.sql.AnalysisException)) {
                    throw e;
                }
                Assumptions.abort("the instance of the pattern is not a valid query: " + e.getMessage());
                return;
            }
            assertEquals(expected, Support.run(after),
                    rule.name() + " changed the result on seed " + seed + ":\n" + before + "\nbecame\n" + after);
        }
    }
}
