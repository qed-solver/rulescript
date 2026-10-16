package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — modeled with a 2-column uninterpreted input relation and a concrete 2-row, 2-column Values; the tuple equality is encoded row-wise as a conjunction of column equalities under a SEMI JOIN, rather than as a general uninterpreted Any/Tuple operator.
public record InlineAnyValuesMultiCol() implements RRule {
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

    // Outer (correlated) input relation: 2 columns
    static final RelRN source = RelRN.scanMany("S",
            Seq.of(RexRN.varType("T", true), RexRN.varType("T", true)));

    // The Values relation: 2 rows x 2 columns, built as a UNION ALL of two
    // single-row Values (the shape the DSL/Calcite API reliably exposes).
    static final RelRN values = new LiteralValues(new Object[]{1, 2})
            .union(true, new LiteralValues(new Object[]{3, 4}));

    // Row-wise tuple equality: S.c0 = V.c0 AND S.c1 = V.c1
    static final RexRN tupleEq = RexRN.and(
            new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                    Seq.of(source.joinField(0, values), source.joinField(2, values))),
            new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                    Seq.of(source.joinField(1, values), source.joinField(3, values))));

    @Override
    public RelRN before() {
        // S SEMI JOIN Values ON (S.c0 = V.c0 AND S.c1 = V.c1)
        return source.join(JoinRelType.SEMI, tupleEq, values);
    }

    @Override
    public RelRN after() {
        // S FILTER ((S.c0 = 1 AND S.c1 = 2) OR (S.c0 = 3 AND S.c1 = 4))
        return source.filter(new RexRN.Or(Seq.of(
                RexRN.and(
                        new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(0), new IntLit(1))),
                        new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(1), new IntLit(2)))),
                RexRN.and(
                        new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(0), new IntLit(3))),
                        new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(1), new IntLit(4))))
        )));
    }
}
