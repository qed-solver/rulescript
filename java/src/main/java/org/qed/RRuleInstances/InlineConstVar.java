package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — "a variable restricted to a constant c" is modeled as an INNER equality join a = c against a unique single-column relation (the DSL has no typed constant literals), and one occurrence of the variable in one uninterpreted filter conjunct is inlined to c (a single self-retriggering iteration of the rule).
public record InlineConstVar() implements RRule {
    // Source scan: the table `foo` with one column `a` (the variable column).
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    // The constant `4` of `a = 4` modeled as a unique single-column relation
    // (a constant value => its column is a key / at most one row).
    static final RelRN constRel = RelRN.scan("Const", RexRN.varType("Const_Type", false), true);

    // Join-row references: field 0 = a (source column), field 1 = c (the constant).
    static final RexRN aRef = source.joinField(0, constRel);
    static final RexRN cRef = source.joinField(1, constRel);

    // The equality conjunct a = c (i.e. `a = 4` in the real query), which is the
    // join condition enforcing that `a` is restricted to the constant.
    static final RexRN eqCond = new Pred(SqlStdOperatorTable.EQUALS, Seq.of(aRef, cRef));

    // The other filter conjunct f: an uninterpreted predicate standing in for the
    // variable (e.g. `a IN (1,2,3,4)`).
    static final SqlOperator fOp = RuleBuilder.create().genericPredicateOp("f", true);
    static final RexRN fOnA = new Pred(fOp, Seq.of(aRef));
    static final RexRN fOnC = new Pred(fOp, Seq.of(cRef));

    @Override
    public RelRN before() {
        // Source ⋈_{a = c} Const, filtered by f(a): the variable not yet inlined.
        return source.join(JoinRelType.INNER, eqCond, constRel).filter(fOnA);
    }

    @Override
    public RelRN after() {
        // Source ⋈_{a = c} Const, filtered by f(c): the variable inlined to the constant.
        // Under the join condition a = c, f(a) ≡ f(c) by congruence, so the two
        // filtered bags are equal.
        return source.join(JoinRelType.INNER, eqCond, constRel).filter(fOnC);
    }
}
