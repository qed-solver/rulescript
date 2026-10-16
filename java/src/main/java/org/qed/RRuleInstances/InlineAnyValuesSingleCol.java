package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — modeled with a 1-column uninterpreted input relation and a concrete 2-row, 1-column Values, with the comparison fixed to EQUALS and encoded as a single column equality under a SEMI JOIN, rather than as a general uninterpreted Any/Tuple operator.
public record InlineAnyValuesSingleCol() implements RRule {
    record LiteralValues(Object[] rowValues) implements RelRN {
        @Override
        public RelNode semantics() {
            String[] fields = new String[rowValues.length];
            for (int i = 0; i < rowValues.length; i++) fields[i] = "c" + i;
            return RuleBuilder.create().values(fields, rowValues).build();
        }
    }

    // Bypasses RelBuilder.filter's condition simplification (which rewrites
    // OR(EQ(x,1), EQ(x,3)) into a SEARCH/Sarg node that QED cannot interpret).
    record RawFilter(RexRN cond, RelRN source) implements RelRN {
        @Override
        public RelNode semantics() {
            return LogicalFilter.create(source.semantics(), cond.semantics());
        }
    }

    record IntLit(int value) implements RexRN {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().literal(value);
        }
    }

    // Outer (correlated) input relation: 1 column
    static final RelRN source = RelRN.scan("S", "S_Type");

    // The Values relation: 2 rows x 1 column, built as a UNION ALL of two
    // single-row Values (the shape the DSL/Calcite API reliably exposes).
    static final RelRN values = new LiteralValues(new Object[]{1})
            .union(true, new LiteralValues(new Object[]{3}));

    // Column equality: S.c0 = V.c0
    static final RexRN colEq = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(source.joinField(0, values), source.joinField(1, values)));

    // Inlined predicate: S.c0 = 1 OR S.c0 = 3
    static final RexRN inlined = new RexRN.Or(Seq.of(
            new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(0), new IntLit(1))),
            new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(0), new IntLit(3)))));

    @Override
    public RelRN before() {
        // S SEMI JOIN Values ON (S.c0 = V.c0)
        return source.join(JoinRelType.SEMI, colEq, values);
    }

    @Override
    public RelRN after() {
        // S FILTER (S.c0 = 1 OR S.c0 = 3)
        return new RawFilter(inlined, source);
    }
}