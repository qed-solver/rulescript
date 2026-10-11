package org.qed.RRuleInstances;

// SCOPE: PARTIAL — modeled with a representative 2-column, single-row Values
// relation with one passthrough column and one input-independent synthesized
// (literal) column; generalizes to any row width by the same reduction, but
// this instance fixes concrete arity/content rather than being universally
// quantified over it.

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record MergeProjectWithValues() implements RRule {
    record LiteralValues(Object[] rowValues) implements RelRN {
        @Override
        public RelNode semantics() {
            String[] fields = new String[rowValues.length];
            for (int i = 0; i < rowValues.length; i++) fields[i] = "c" + i;
            return RuleBuilder.create().values(fields, rowValues).build();
        }
    }

    record IntLit(int value) implements RexRN {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().literal(value);
        }
    }

    static final RelRN values = new LiteralValues(new Object[]{1, 2});

    @Override
    public RelRN before() {
        return new RelRN.ProjectMany(Seq.of(values.field(0), new IntLit(3)), values);
    }

    @Override
    public RelRN after() {
        return new LiteralValues(new Object[]{1, 3});
    }
}
