package org.qed.Backends.Calcite;

import kala.collection.Seq;
import kala.tuple.Tuple;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.hep.HepPlanner;
import org.apache.calcite.plan.hep.HepProgramBuilder;
import org.apache.calcite.rel.RelNode;
import org.qed.JSONDeserializer;
import org.qed.ProjectPaths;
import org.qed.RRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CalciteTester {
    public static final String RULE_PACKAGE = "org.qed.RRuleInstances";
    public static final String TEST_PACKAGE = "org.qed.Backends.Calcite.Tests";

    public static String genPath = ProjectPaths.baseDir().resolve("src/main/java/org/qed/Backends/Calcite/Generated").toString();
    public static String rulePath = ProjectPaths.baseDir().resolve("src/main/java/org/qed/RRuleInstances").toString();
    public static String testPath = ProjectPaths.baseDir().resolve("src/main/java/org/qed/Backends/Calcite/Tests").toString();

    private static int failures = 0;

    public static int failures() {
        return failures;
    }

    public static void fail(String details) {
        failures++;
        System.out.println("failed");
        System.out.println("> " + details);
    }

    public static HepPlanner loadRule(RelOptRule rule) {
        System.out.printf("Loading Rule: %s\n", rule.getClass().getSimpleName());
        return new HepPlanner(new HepProgramBuilder().addRuleInstance(rule).build());
    }

    public static HepPlanner loadRule(RelOptRule rule, int matchLimit) {
        System.out.printf("Loading Rule: %s (match limit: %d)\n", rule.getClass().getSimpleName(), matchLimit);
        return new HepPlanner(new HepProgramBuilder().addMatchLimit(matchLimit).addRuleInstance(rule).build());
    }

    public static Seq<RRule> ruleList() {
        var files = new File(rulePath).listFiles((dir, name) -> name.endsWith(".java"));
        var rules = new ArrayList<RRule>();
        if (files != null) {
            java.util.Arrays.sort(files);
            for (var file : files) {
                var className = file.getName().replace(".java", "");
                try {
                    rules.add((RRule) Class.forName(RULE_PACKAGE + "." + className).getConstructor().newInstance());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load rule: " + className, e);
                }
            }
        }
        return Seq.from(rules);
    }

    public static List<String> generate(java.util.Set<String> only) {
        var tester = new CalciteTester();
        var generated = new ArrayList<String>();
        for (var rule : ruleList().filter(r -> only.isEmpty() || only.contains(r.name()))) {
            try {
                tester.serialize(rule, genPath);
                generated.add(rule.name());
            } catch (RuntimeException e) {
                System.err.println("Failed to generate " + rule.name() + ": " + e);
                try {
                    Files.deleteIfExists(Path.of(genPath, rule.name() + ".java"));
                } catch (IOException ioe) {
                    System.err.println(ioe.getMessage());
                }
            }
        }
        return generated;
    }

    public static List<String> compileGenerated(List<String> names, Path outDir) {
        return compileGenerated(Path.of(genPath), names, outDir);
    }

    public static List<String> compileGenerated(Path sourceDir, List<String> names, Path outDir) {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        var compiled = new ArrayList<String>();
        for (var name : names) {
            var source = sourceDir.resolve(name + ".java");
            var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
            try (var fm = compiler.getStandardFileManager(diagnostics, null, null)) {
                Files.createDirectories(outDir);
                var ok = compiler.getTask(null, fm, diagnostics, List.of("-d", outDir.toString(), "-classpath", compileClasspath()), null, fm.getJavaFileObjects(source)).call();
                if (ok) {
                    compiled.add(name);
                    continue;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.err.println("Generated rule does not compile: " + name);
            diagnostics.getDiagnostics().forEach(d -> System.err.println("  " + d));
            try {
                Files.deleteIfExists(source);
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
        return compiled;
    }

    public static RelOptRule generateAndLoad(RRule rule) {
        try {
            var dir = Files.createTempDirectory("calcite-probe");
            new CalciteTester().serialize(rule, dir.toString());
            var classes = dir.resolve("classes");
            if (compileGenerated(dir, List.of(rule.name()), classes).isEmpty()) throw new IllegalStateException("The generated code of " + rule.name() + " does not compile");
            var loader = new GeneratedClassLoader(classes, CalciteTester.class.getClassLoader());
            var config = Class.forName(GENERATED_PACKAGE + "." + rule.name() + "$Config", true, loader);
            return (RelOptRule) config.getMethod("toRule").invoke(config.getField("DEFAULT").get(null));
        } catch (IOException | ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String compileClasspath() {
        var entries = new java.util.LinkedHashSet<String>();
        entries.add(System.getProperty("java.class.path"));
        for (var loader = Thread.currentThread().getContextClassLoader(); loader != null;
             loader = loader.getParent()) {
            if (loader instanceof java.net.URLClassLoader urls) for (var url : urls.getURLs()) entries.add(url.getPath());
        }
        return String.join(File.pathSeparator, entries);
    }

    private static final class GeneratedClassLoader extends java.net.URLClassLoader {
        GeneratedClassLoader(Path dir, ClassLoader parent) throws IOException {
            super(new java.net.URL[]{dir.toUri().toURL()}, parent);
        }
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(GENERATED_PACKAGE + ".")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                var loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException e) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }

    public static final String GENERATED_PACKAGE = "org.qed.Backends.Calcite.Generated";

    private static ClassLoader generatedLoader = CalciteTester.class.getClassLoader();

    public static ClassLoader generatedLoader() {
        return generatedLoader;
    }

    public static void runAllTests(java.util.Set<String> only) {
        var testFiles = new File(testPath).listFiles((dir, name) -> name.endsWith("Test.java") && (only.isEmpty() || only.contains(name.substring(0, name.length() - "Test.java".length()))));
        if (testFiles == null) return;
        java.util.Arrays.sort(testFiles);
        for (var testFile : testFiles) {
            var className = TEST_PACKAGE + "." + testFile.getName().replace(".java", "");
            System.out.println("Running " + className);
            try {
                Class.forName(className).getMethod("runTest").invoke(null);
            } catch (Exception e) {
                System.out.println("failed");
                System.out.println("> " + className + " threw " + (e.getCause() != null ? e.getCause() : e));
                failures++;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        var only = java.util.Set.of(args);
        var requested = ruleList().count(r -> only.isEmpty() || only.contains(r.name()));
        var generated = generate(only);
        var outDir = Files.createTempDirectory("calcite-generated");
        var compiled = compileGenerated(generated, outDir);
        generatedLoader = new GeneratedClassLoader(outDir, CalciteTester.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(generatedLoader);
        runAllTests(only);
        try (var files = Files.walk(outDir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        System.out.printf("Generated %d/%d rules, %d compiled, %d test failures%n", generated.size(), requested, compiled.size(), failures);
        if (generated.size() != requested || compiled.size() != generated.size() || failures > 0) System.exit(1);
    }

    public void serialize(RRule rule, String path) {
        var code = new CalciteGenerator().generate(rule);
        try {
            Files.createDirectories(Path.of(path));
            Files.write(Path.of(path, rule.name() + ".java"), code.getBytes());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }
    }

    public void test(RelOptRule rule, Seq<String> tests) {
        System.out.println("Testing rule " + rule.getClass().getSimpleName());
        var runner = loadRule(rule);
        var exams = tests.mapUnchecked(t -> Tuple.of(t, JSONDeserializer.load(new File(t))));
        for (var entry : exams) {
            if (entry.getValue().size() != 2) {
                System.err.println(entry.getKey() + " does not have exactly two nodes, and thus is not a valid test");
                continue;
            }
            verify(runner, entry.getValue().get(0), entry.getValue().get(1));
        }
    }

    public void verify(HepPlanner runner, RelNode source, RelNode target) {
        runner.setRoot(source);
        var answer = runner.findBestExp();
        var answerExplain = answer.explain();
        var targetExplain = target.explain();
        if (answerExplain.equals(targetExplain)) {
            System.out.println("succeeded");
            return;
        }
        failures++;
        System.out.println("failed");
        System.out.println("> Given source RelNode:\n" + source.explain());
        System.out.println("> Actual rewritten RelNode:\n" + answerExplain);
        System.out.println("> Expected rewritten RelNode:\n" + targetExplain);
    }
}
