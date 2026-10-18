package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Mutants {
    private Mutants() {}

    record Mutant(String description, RelRN pattern) {}

    private enum Kind { JOIN_TYPE, UNIQUE_SCAN, NULLABLE_SCAN, SET_OP_ALL, LITERAL, CONNECTIVE, ABLATE }

    static List<Mutant> of(RelRN pattern) {
        var mutants = new ArrayList<Mutant>();
        for (var kind : Kind.values()) {
            var counter = new Rewriter(kind, -1, null);
            counter.rel(pattern);
            for (var site = 0; site < counter.sites; site++) {
                var rewriter = new Rewriter(kind, site, null);
                var mutated = rewriter.rel(pattern);
                mutants.add(new Mutant(rewriter.description, mutated));
            }
            for (var name : counter.scans) {
                var rewriter = new Rewriter(kind, -1, name);
                var mutated = rewriter.rel(pattern);
                if (rewriter.description != null) {
                    mutants.add(new Mutant(rewriter.description, mutated));
                }
            }
        }
        return mutants;
    }

    private static final class Rewriter {
        final Kind kind;
        final int target;
        final String scan;
        int sites = 0;
        String description;
        final Set<String> scans = new LinkedHashSet<>();

        Rewriter(Kind kind, int target, String scan) {
            this.kind = kind;
            this.target = target;
            this.scan = scan;
        }

        private boolean hit() {
            return sites++ == target;
        }

        private boolean ablate(RelRN node) {
            if (kind != Kind.ABLATE || !hit()) {
                return false;
            }
            description = "the " + node.getClass().getSimpleName() + " operator is missing";
            return true;
        }

        RelRN rel(RelRN node) {
            return switch (node) {
                case RelRN.Filter f when ablate(f) -> rel(f.source());
                case RelRN.Project p when ablate(p) -> rel(p.source());
                case RelRN.ProjectMany p when ablate(p) -> rel(p.source());
                case RelRN.Aggregate a when ablate(a) -> rel(a.source());
                case RelRN.Scan s -> scan(s);
                case RelRN.Filter f -> new RelRN.Filter(rex(f.cond()), rel(f.source()));
                case RelRN.Project p -> new RelRN.Project(rex(p.map()), rel(p.source()));
                case RelRN.ProjectMany p -> new RelRN.ProjectMany(p.exprs().map(this::rex), rel(p.source()));
                case RelRN.Join j -> new RelRN.Join(joinType(j.ty()), rex(j.cond()), rel(j.left()), rel(j.right()));
                case RelRN.JoinWithSeparateConds j -> new RelRN.JoinWithSeparateConds(joinType(j.ty()),
                        rex(j.cond()), rel(j.left()), rel(j.right()));
                case RelRN.JoinWithPushedConds j -> new RelRN.JoinWithPushedConds(joinType(j.ty()), rex(j.cond()),
                        rel(j.left()), rel(j.right()));
                case RelRN.Union u -> new RelRN.Union(all(u.all(), "UNION"), u.sources().map(this::rel));
                case RelRN.Intersect i -> new RelRN.Intersect(all(i.all(), "INTERSECT"), i.sources().map(this::rel));
                case RelRN.Minus m -> new RelRN.Minus(all(m.all(), "MINUS"), m.sources().map(this::rel));
                case RelRN.Aggregate a -> new RelRN.Aggregate(rel(a.source()), a.groupSet().map(this::rex),
                        a.aggCalls().map(this::call));
                case RelRN.Correlate c -> new RelRN.Correlate(c.ty(), c.cond(), rel(c.left()), rel(c.right()));
                default -> node;
            };
        }

        private RelRN.AggCall call(RelRN.AggCall call) {
            return new RelRN.AggCall(call.name(), call.operator(), call.distinct(), call.type(),
                    call.operands().map(this::rex));
        }

        private RelRN scan(RelRN.Scan s) {
            scans.add(s.name());
            if (!s.name().equals(scan)) {
                return s;
            }
            if (kind == Kind.UNIQUE_SCAN && s.unique()) {
                description = "scan " + s.name() + " is not unique";
                return new RelRN.Scan(s.name(), s.ty(), false);
            }
            if (kind == Kind.NULLABLE_SCAN && !s.ty().isNullable()) {
                description = "scan " + s.name() + " is nullable";
                return new RelRN.Scan(s.name(), new RelType.VarType(s.ty().toString().split("[: ]")[0], true),
                        s.unique());
            }
            return s;
        }

        private RelRN.Join.JoinType joinType(RelRN.Join.JoinType type) {
            if (kind != Kind.JOIN_TYPE || !(type instanceof RelRN.Join.JoinType.ConcreteJoinType concrete)) {
                return type;
            }
            if (!hit()) {
                return type;
            }
            var other = concrete.type() == JoinRelType.INNER ? JoinRelType.LEFT : JoinRelType.INNER;
            description = "join type " + concrete.type() + " replaced by " + other;
            return new RelRN.Join.JoinType.ConcreteJoinType(other);
        }

        private boolean all(boolean all, String name) {
            if (kind == Kind.SET_OP_ALL && hit()) {
                description = name + (all ? " ALL replaced by DISTINCT" : " DISTINCT replaced by ALL");
                return !all;
            }
            return all;
        }

        RexRN rex(RexRN node) {
            return switch (node) {
                case RexRN.Pred p -> new RexRN.Pred(p.operator(), p.sources().map(this::rex));
                case RexRN.Proj p -> new RexRN.Proj(p.operator(), p.sources().map(this::rex));
                case RexRN.GroupBy g -> new RexRN.GroupBy(g.operator(), g.sources().map(this::rex));
                case RexRN.And a -> {
                    var swap = kind == Kind.CONNECTIVE && hit();
                    var sources = a.sources().map(this::rex);
                    if (swap) {
                        description = "AND replaced by OR";
                    }
                    yield swap ? new RexRN.Or(sources) : new RexRN.And(sources);
                }
                case RexRN.Or o -> {
                    var swap = kind == Kind.CONNECTIVE && hit();
                    var sources = o.sources().map(this::rex);
                    if (swap) {
                        description = "OR replaced by AND";
                    }
                    yield swap ? new RexRN.And(sources) : new RexRN.Or(sources);
                }
                case RexRN.Not n -> new RexRN.Not(rex(n.source()));
                case RexRN.True t -> kind == Kind.LITERAL && hit() ? flipped("TRUE", new RexRN.False()) : t;
                case RexRN.False f -> kind == Kind.LITERAL && hit() ? flipped("FALSE", new RexRN.True()) : f;
                default -> node;
            };
        }

        private RexRN flipped(String from, RexRN to) {
            description = "literal " + from + " replaced by " + (to instanceof RexRN.True ? "TRUE" : "FALSE");
            return to;
        }
    }
}
