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
// SCOPE: FULL — models x = ANY(tuple) as S SEMI JOIN V ON S.c0=V.c0 and x IN (tuple) as S FILTER (S.c0 IN (SELECT c0 FROM V)), with the scalar input x as an uninterpreted column S.c0 and the tuple as an uninterpreted 1-column relation V, so the equivalence holds for all instantiations of V.
public record SimplifyEqualsAnyTuple() implements RRule {
    // Relational meaning of x IN (tuple): S FILTER (S.c0 IN (SELECT c0 FROM tuple-as-relation))
    record InSubQueryFilter(RelRN outer, RelRN inner) implements RelRN {
        @Override
        public RelNode semantics() {
            var b = RuleBuilder.create();
            RelNode l = outer.semantics();
            RexNode in = b.in(inner.semantics(), java.util.List.of(b.getRexBuilder().makeInputRef(l, 0)));
            return LogicalFilter.create(l, in);
        }
    }

    // Outer (input) relation: 1 column; S.c0 stands for the scalar x
    static final RelRN source = RelRN.scan("S", "S_Type");

    // The tuple as an uninterpreted 1-column relation V (stands for the ANY/IN tuple)
    static final RelRN tuple = RelRN.scan("V", "V_Type");

    // Column equality: S.c0 = V.c0
    static final RexRN colEq = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(source.joinField(0, tuple), source.joinField(1, tuple)));

    @Override
    public RelRN before() {
        // x = ANY (tuple):  S SEMI JOIN V ON (S.c0 = V.c0)
        return source.join(JoinRelType.SEMI, colEq, tuple);
    }

    @Override
    public RelRN after() {
        // x IN (tuple):  S FILTER (S.c0 IN (SELECT c0 FROM V))
        return new InSubQueryFilter(source, tuple);
    }
}
