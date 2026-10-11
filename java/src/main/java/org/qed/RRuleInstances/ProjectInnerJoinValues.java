package org.qed.RRuleInstances;

// SCOPE: PARTIAL — modeled with a representative 1-column left input, a single-row 2-column Values (1, 2), and an uninterpreted on-predicate over all join columns; the correlated InnerJoinApply variant is not modeled.

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record ProjectInnerJoinValues() implements RRule {
    // $right:(Values) & (HasOneRow $right): a one-row, 2-column Values relation.
    record LiteralValues(Object[] rowValues) implements RelRN {
        @Override
        public RelNode semantics() {
            String[] fields = new String[rowValues.length];
            for (int i = 0; i < rowValues.length; i++) fields[i] = "v" + i;
            return RuleBuilder.create().values(fields, rowValues).build();
        }
    }

    record IntLit(int value) implements RexRN {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().literal(value);
        }
    }

    // $left: arbitrary 1-column input.
    static final RelRN left = RelRN.scan("L", "L_Type");
    // The single-row Values (1, 2).
    static final RelRN values = new LiteralValues(new Object[]{1, 2});

    @Override
    public RelRN before() {
        // InnerJoin($left, $right, $on): the on-clause is an uninterpreted
        // predicate "on" over all join columns (L's column, then the two
        // Values columns).
        return left.join(JoinRelType.INNER, left.joinPred("on", values), values);
    }

    @Override
    public RelRN after() {
        // (Select (Project $left (MakeProjectionsFromValues $right)
        // (OutputCols $left)) $on): project L with the Values' row contents
        // as constant columns, then filter with the same "on" symbol.
        RelRN proj = left.project(Seq.of(left.field(0), new IntLit(1), new IntLit(2)));
        return proj.filter(proj.pred("on"));
    }
}