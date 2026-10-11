package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteGenerator;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.CodeGenerator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CalciteGeneratorTest {
    private static final String RENAMED = "SomeOtherName";

    public static void runTest() {
        independentOfName();
        deterministic();
        generatedRulesAreCurrent();
        refusesUnboundSymbol();
        refusesUntranslatablePattern();
        correlateOperandsFollowPreOrder();
    }

    private static void independentOfName() {
        for (var rule : CalciteTester.ruleList()) {
            var original = new CalciteGenerator().generate(rule);
            var renamed = new CalciteGenerator().generate(named(rule, RENAMED));
            check(original.replace(rule.name(), RENAMED).equals(renamed),
                    "the generated code of " + rule.name() + " depends on the name of the rule");
        }
        System.out.println("succeeded (generated code is independent of rule names)");
    }

    private static void deterministic() {
        for (var rule : CalciteTester.ruleList()) {
            check(new CalciteGenerator().generate(rule).equals(new CalciteGenerator().generate(rule)),
                    "generating " + rule.name() + " twice gives different code");
        }
        System.out.println("succeeded (generation is deterministic)");
    }

    private static void generatedRulesAreCurrent() {
        for (var rule : CalciteTester.ruleList()) {
            var file = Path.of(CalciteTester.genPath, rule.name() + ".java");
            try {
                check(Files.exists(file) && Files.readString(file).equals(new CalciteGenerator().generate(rule)),
                        "Generated/" + rule.name() + ".java is not the output of the generator");
            } catch (IOException e) {
                check(false, "cannot read " + file);
            }
        }
        System.out.println("succeeded (Generated/ is up to date)");
    }

    private static void refusesUnboundSymbol() {
        var source = RelRN.scan("Source", "Source_Type");
        var rule = rule("UnboundSymbol", source.filter(source.pred("matched")), source.filter(source.pred("fresh")));
        refuses(rule, "a symbol that only the rewrite applies");
    }

    private static void refusesUntranslatablePattern() {
        var node = RuleBuilder.create().addTable(new org.qed.QedTable("T", Seq.of("c"),
                Seq.of(RexRN.varType("T_Type", true)), kala.collection.Set.empty(), kala.collection.Set.empty()))
                .scan("T").sort(0).build();
        RelRN opaque = () -> node;
        refuses(rule("Opaque", opaque.filter(opaque.pred("p")), opaque), "an opaque sorted relation");
        var left = RelRN.scan("L", "T");
        var right = RelRN.scan("R", "T");
        var nonStandard = RuleBuilder.create().genericPredicateOp("p", true);
        refuses(rule("Fresh", left.join(JoinRelType.INNER, RexRN.trueLiteral(), right),
                left.join(JoinRelType.INNER, new RexRN.Pred(nonStandard, Seq.of(left.joinField(0, right))), right)),
                "a join condition that is a symbol of the rewrite only");
    }

    private static void correlateOperandsFollowPreOrder() {
        var left = RelRN.scan("L", "T");
        var right = RelRN.scan("R", "T");
        var cond = RuleBuilder.create().genericPredicateOp("cond", true);
        var rule = rule("CorrelateToJoin", left.correlate(JoinRelType.INNER, cond, right),
                left.join(JoinRelType.INNER, new RexRN.Pred(cond, left.joinFields(right)), right));
        var plan = FaithfulPlans.of(rule.before());
        var expected = FaithfulPlans.of(rule.after());
        var planner = CalciteTester.loadRule(CalciteTester.generateAndLoad(rule), 1);
        planner.setRoot(plan);
        check(planner.findBestExp().explain().equals(expected.explain()),
                "a rule matching a correlate does not rewrite it:\n" + plan.explain());
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var unfiltered = builder.scan(l).scan(r).join(JoinRelType.INNER, builder.literal(true)).build();
        var other = CalciteTester.loadRule(CalciteTester.generateAndLoad(rule), 1);
        other.setRoot(unfiltered);
        check(other.findBestExp().explain().equals(unfiltered.explain()), "the correlate rule fired on a join");
        System.out.println("succeeded (correlate operands follow pre-order)");
    }

    private static void refuses(RRule rule, String what) {
        try {
            var code = new CalciteGenerator().generate(rule);
            check(false, "code was generated for " + what + ":\n" + code);
        } catch (CodeGenerator.UnsupportedConstructException expected) {
            System.out.println("succeeded (refused " + what + ")");
        }
    }

    private static RRule rule(String name, RelRN before, RelRN after) {
        return new RRule() {
            @Override
            public RelRN before() {
                return before;
            }

            @Override
            public RelRN after() {
                return after;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    private static RRule named(RRule rule, String name) {
        return rule(name, rule.before(), rule.after());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            CalciteTester.fail(message);
        }
    }

    public static void main(String[] args) {
        System.out.println("Running generator test...");
        runTest();
    }
}
