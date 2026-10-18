package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the identity project is over a single-column input (it projects its input's sole field); the original rule applies to any arity
public record ProjectRemove() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    static final RelRN identityProject = source.project(source.field(0));

    @Override
    public RelRN before() {
        return identityProject;
    }

    @Override
    public RelRN after() {
        return source;
    }
}
