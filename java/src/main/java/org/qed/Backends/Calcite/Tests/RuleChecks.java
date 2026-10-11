package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;

import java.util.List;

import org.apache.calcite.plan.RelOptListener;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.rel.RelNode;
import org.qed.RRule;

public final class RuleChecks {
    private RuleChecks() {}

    public static RelOptRule generated(String ruleName) {
        try {
            var config = Class.forName(CalciteTester.GENERATED_PACKAGE + "." + ruleName + "$Config", true,
                    CalciteTester.generatedLoader());
            var defaults = config.getField("DEFAULT").get(null);
            return (RelOptRule) config.getMethod("toRule").invoke(defaults);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No generated rule " + ruleName, e);
        }
    }

    public static void rewritesPattern(RRule rule) {
        rewrites(rule, FaithfulPlans.of(rule.before()), FaithfulPlans.expected(rule.after()));
    }

    public static void rewritesConcretePredicates(RRule rule) {
        rewrites(rule, FaithfulPlans.concrete(rule.before()), FaithfulPlans.expectedConcrete(rule.after()));
    }

    public static void rewrites(RRule rule, RelNode before, RelNode after) {
        var result = new RelNode[1];
        var productions = apply(rule, before, result);
        if (productions == 0) {
            CalciteTester.fail(rule.name() + " did not fire on:\n" + before.explain());
        } else if (!result[0].explain().equals(after.explain())) {
            CalciteTester.fail(rule.name() + " produced an unexpected plan\n> Given source RelNode:\n"
                    + before.explain() + "\n> Actual rewritten RelNode:\n" + result[0].explain()
                    + "\n> Expected rewritten RelNode:\n" + after.explain());
        } else {
            System.out.println("succeeded");
            if (before.explain().equals(after.explain())) {
                System.out.println("note: the rewrite is absorbed by the builder (the plan is unchanged)");
            }
        }
    }

    public static void doesNotApply(RRule rule, RelNode plan) {
        if (apply(rule, plan, new RelNode[1]) == 0) {
            System.out.println("succeeded");
        } else {
            CalciteTester.fail(rule.name() + " fired on a plan that does not match:\n" + plan.explain());
        }
    }

    public static void rejectsNearMisses(RRule rule) {
        var rejected = 0;
        var skipped = 0;
        for (var mutant : Mutants.of(rule.before())) {
            RelNode plan;
            try {
                plan = FaithfulPlans.of(mutant.pattern());
            } catch (RuntimeException e) {
                skipped++;
                continue;
            }
            if (apply(rule, plan, new RelNode[1]) != 0) {
                CalciteTester.fail(rule.name() + " fired although " + mutant.description() + ":\n" + plan.explain());
                return;
            }
            rejected++;
        }
        System.out.println("succeeded (" + rejected + " near misses rejected, " + skipped + " not constructible)");
    }

    private static int apply(RRule rule, RelNode plan, RelNode[] result) {
        var planner = CalciteTester.loadRule(generated(rule.name()), 1);
        var productions = new int[1];
        planner.addListener(new RelOptListener() {
            @Override
            public void relEquivalenceFound(RelEquivalenceEvent event) {}

            @Override
            public void ruleAttempted(RuleAttemptedEvent event) {}

            @Override
            public void ruleProductionSucceeded(RuleProductionEvent event) {
                if (event.isBefore()) {
                    productions[0]++;
                }
            }

            @Override
            public void relDiscarded(RelDiscardedEvent event) {}

            @Override
            public void relChosen(RelChosenEvent event) {}
        });
        planner.setRoot(plan);
        result[0] = planner.findBestExp();
        return productions[0];
    }

    public static String table(org.qed.RuleBuilder builder, int columns, boolean unique) {
        return table(builder, columns, unique, true);
    }

    public static String table(org.qed.RuleBuilder builder, int columns, boolean unique, boolean nullable) {
        var schema = new java.util.ArrayList<kala.tuple.Tuple2<org.qed.RelType, Boolean>>();
        for (var i = 0; i < columns; i++) {
            schema.add(kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", nullable), unique && i == 0));
        }
        var table = builder.createQedTable(kala.collection.Seq.from(schema));
        builder.addTable(table);
        return table.getName();
    }

    public static org.apache.calcite.tools.RelBuilder ruleBuilder(org.qed.RuleBuilder builder) {
        return builder.transform(CalciteRuntime::configure);
    }

    public static org.apache.calcite.rex.RexNode gt(org.apache.calcite.tools.RelBuilder b,
                                                    org.apache.calcite.rex.RexNode x, int n) {
        return b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.GREATER_THAN, x, b.literal(n));
    }

    public static org.apache.calcite.rex.RexNode eq(org.apache.calcite.tools.RelBuilder b,
                                                    org.apache.calcite.rex.RexNode x,
                                                    org.apache.calcite.rex.RexNode y) {
        return b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS, x, y);
    }

    public static org.apache.calcite.rel.RelNode correlate(org.apache.calcite.tools.RelBuilder b,
            org.apache.calcite.rel.RelNode left, org.apache.calcite.rel.RelNode right,
            org.apache.calcite.rel.core.JoinRelType type,
            java.util.function.Function<org.apache.calcite.rex.RexNode[], org.apache.calcite.rex.RexNode> cond) {
        var rexBuilder = b.getRexBuilder();
        var id = b.getCluster().createCorrel();
        var corr = rexBuilder.makeCorrel(left.getRowType(), id);
        var arguments = new org.apache.calcite.rex.RexNode[left.getRowType().getFieldCount()
                + right.getRowType().getFieldCount()];
        for (var i = 0; i < left.getRowType().getFieldCount(); i++) {
            arguments[i] = rexBuilder.makeFieldAccess(corr, i);
        }
        for (var j = 0; j < right.getRowType().getFieldCount(); j++) {
            arguments[left.getRowType().getFieldCount() + j] =
                    rexBuilder.makeInputRef(right.getRowType().getFieldList().get(j).getType(), j);
        }
        return org.apache.calcite.rel.logical.LogicalCorrelate.create(left,
                org.apache.calcite.rel.logical.LogicalFilter.create(right, cond.apply(arguments)), List.of(), id,
                org.apache.calcite.util.ImmutableBitSet.range(left.getRowType().getFieldCount()), type);
    }
}
