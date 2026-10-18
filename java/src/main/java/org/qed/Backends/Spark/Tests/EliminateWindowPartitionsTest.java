package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Count;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateWindowPartitionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateWindowPartitions.json"));

    @Test
    void removesConstantGroupKey() {
        var base = Support.relation("r", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var withConst = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), constant(0, "k"))), base);
        var outs = Catalyst.<Attribute>list(withConst.output());
        var count = countAlias(outs.get(0), false, "c");
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(outs.get(0), outs.get(1))),
                Catalyst.<NamedExpression>seq(List.of(outs.get(0), outs.get(1), count)), withConst,
                Option.<AggregateHint>empty());

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var project = (Project) rewritten;
        assertEquals(3, project.projectList().size());
        assertTrue(project.child() instanceof Project);
        var inner = (Project) project.child();
        assertEquals(3, inner.projectList().size());
        assertTrue(inner.child() instanceof Aggregate);
        var grouped = (Aggregate) inner.child();
        assertEquals(1, grouped.groupingExpressions().size());
        assertEquals(Catalyst.list(base.output()), Catalyst.list(grouped.child().output()));
        var literal = inner.projectList().apply(1);
        assertTrue(literal instanceof Alias alias && alias.child() instanceof Literal lit && Integer.valueOf(0).equals(lit.value()));

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesNonConstantPartitionAlone() {
        var base = Support.relation("r", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var withVarying = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), cols.get(1))), base);
        var outs = Catalyst.<Attribute>list(withVarying.output());
        var count = countAlias(outs.get(0), false, "c");
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(outs.get(0), outs.get(1))),
                Catalyst.<NamedExpression>seq(List.of(outs.get(0), outs.get(1), count)), withVarying,
                Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesDistinctCountAlone() {
        var base = Support.relation("r", 2, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var withConst = new Project(Catalyst.<NamedExpression>seq(List.of(cols.get(0), constant(0, "k"))), base);
        var outs = Catalyst.<Attribute>list(withConst.output());
        var count = countAlias(outs.get(0), true, "c");
        var plan = new Aggregate(Catalyst.<Expression>seq(List.of(outs.get(0), outs.get(1))),
                Catalyst.<NamedExpression>seq(List.of(outs.get(0), outs.get(1), count)), withConst,
                Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }

    private static Alias constant(int value, String name) {
        return Alias$.MODULE$.apply(Literal$.MODULE$.apply(value), name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Alias countAlias(Attribute arg, boolean distinct, String name) {
        var function = new Count(Catalyst.<Expression>seq(List.of(arg)));
        var aggregate = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, distinct,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return Alias$.MODULE$.apply(aggregate, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }
}
