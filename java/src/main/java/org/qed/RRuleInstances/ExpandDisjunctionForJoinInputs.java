package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — INNER join whose condition is a two-branch disjunction of conjunctions, each branch pairing a left-only with a right-only predicate, expanded by AND-ing in the per-side disjunctions.
public record ExpandDisjunctionForJoinInputs() implements RRule {
    static final RelRN L = RelRN.scanMany("L", Seq.of(RexRN.varType("L_Type", true), RexRN.varType("L_Type2", true)));
    static final RelRN R = RelRN.scanMany("R", Seq.of(RexRN.varType("R_Type", true), RexRN.varType("R_Type2", true)));

    static final RexRN pl = L.joinField(0, R).pred("pl");
    static final RexRN rl = L.joinField(1, R).pred("rl");
    static final RexRN pr = L.joinField(2, R).pred("pr");
    static final RexRN sr = L.joinField(3, R).pred("sr");

    static final RexRN branchA = RexRN.and(pl, pr);
    static final RexRN branchB = RexRN.and(rl, sr);
    static final RexRN cond = new RexRN.Or(Seq.of(branchA, branchB));

    @Override
    public RelRN before() {
        return L.join(JoinRelType.INNER, cond, R);
    }

    @Override
    public RelRN after() {
        return L.join(JoinRelType.INNER,
            RexRN.and(cond, new RexRN.Or(Seq.of(pl, rl)), new RexRN.Or(Seq.of(pr, sr))), R);
    }
}
