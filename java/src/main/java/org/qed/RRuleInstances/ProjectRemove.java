package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

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
