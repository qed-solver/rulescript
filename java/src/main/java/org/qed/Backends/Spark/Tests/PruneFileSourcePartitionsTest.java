package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruneFileSourcePartitionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneFileSourcePartitions.json"));

    private static LogicalPlan query(LogicalPlan child, List<Attribute> cols, Expression cond) {
        var projects = Catalyst.<NamedExpression>seq(List.of(cols.get(0)));
        return new Project(projects, new Filter(cond, child));
    }

    @Test
    void pushesPartitionConjunctIntoScanAndKeepsFullFilter() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var partition = new GreaterThan(cols.get(0), cols.get(1));
        var data = new GreaterThan(cols.get(2), Literal$.MODULE$.apply(2));
        var full = new And(partition, data);
        var plan = query(base, cols, full);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Filter outer && outer.condition().equals(full)
                && outer.child() instanceof Filter inner && inner.condition().equals(partition) && inner.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterWhosePartitionConjunctReadsDataColumnAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var notPartition = new GreaterThan(cols.get(0), cols.get(2));
        var data = new GreaterThan(cols.get(2), Literal$.MODULE$.apply(2));
        var plan = query(base, cols, new And(notPartition, data));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterWithoutDataConjunctAlone() {
        var base = Support.relation("t", 3, List.of(), new Random(1));
        var cols = Catalyst.<Attribute>list(base.output());
        var plan = query(base, cols, new GreaterThan(cols.get(0), cols.get(1)));
        assertEquals(plan, rule.apply(plan));
    }
}
