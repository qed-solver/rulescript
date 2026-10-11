package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — one passthrough input column and one synthesized column, with exactly two filter conjuncts: one bound to the input column (pushed below the Project) and one referencing the synthesized column (kept above); the IsBoundBy/ExtractBoundConditions/ExtractUnboundConditions guards are enforced structurally by which column each conjunct references.
public record PushSelectIntoProject() implements RRule {
    // The input relation: one physical column a.
    static final RelType.VarType aT = new RelType.VarType("A_Type", true);
    static final RelRN scan = RelRN.scanMany("Source", Seq.of(aT));

    // The Project: one synthesized column f = F(a) (the "expensive computed
    // column") plus passthrough of a. Output: [f, a].
    static final RexRN f = scan.proj("F", "F_Type");
    static final RelRN project = scan.project(Seq.of(f, scan.field(0)));

    // Bound filter P: references only the input column a (available as
    // passthrough output column 1 above the Project).
    // Unbound filter Q: references the synthesized column f (output column 0).

    // Before: Select(Project(a, f, a), P(a) AND Q(f)).
    static final RexRN pAbove = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("P", true), Seq.of(project.field(1)));
    static final RexRN qAbove = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("Q", true), Seq.of(project.field(0)));
    static final RelRN before = project.filter(RexRN.and(pAbove, qAbove));

    // After: the bound conjunct P is pushed onto the input (column a = 0)
    // below the Project; the unbound conjunct Q stays above the Project, on
    // its synthesized output column.
    static final RexRN pBelow = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("P", true), Seq.of(scan.field(0)));
    static final RelRN selectInner = scan.filter(pBelow);
    static final RelRN projectBelow = selectInner.project(Seq.of(f, scan.field(0)));
    static final RexRN qTop = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("Q", true), Seq.of(projectBelow.field(0)));
    static final RelRN after = projectBelow.filter(qTop);

    @Override
    public RelRN before() {
        return before;
    }

    @Override
    public RelRN after() {
        return after;
    }
}
