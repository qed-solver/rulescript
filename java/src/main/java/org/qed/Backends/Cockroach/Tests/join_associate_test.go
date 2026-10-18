package rulescript_test

import (
    "testing"

    "github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinAssociate(t *testing.T) {
    rule := loadRule(t, "JoinAssociate")
    e := newEnv(t)
    a, ac := e.scan("u", 1)
    b, bc := e.scan("t", 1)
    c, cc := e.scan("u", 1)

    pab := e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))
    pb := e.f.ConstructEq(e.v(bc[0]), e.num(7))
    pabc := e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))
    pbc := e.f.ConstructEq(e.v(bc[0]), e.v(cc[0]))
    pc := e.f.ConstructEq(e.v(cc[0]), e.num(11))

    ab := e.f.ConstructInnerJoin(a, b, e.filters(pab, pb), &memo.JoinPrivate{})
    before := e.f.ConstructInnerJoin(ab, c, e.filters(pabc, pbc, pc), &memo.JoinPrivate{})

    bcJoin := e.f.ConstructInnerJoin(b, c, e.filters(pb, pbc, pc), &memo.JoinPrivate{})
    want := e.f.ConstructInnerJoin(a, bcJoin, e.filters(pab, pabc), &memo.JoinPrivate{})
    e.expectRewrite(rule, before, want)

    near := e.f.ConstructInnerJoin(ab, c, e.filters(pabc, pbc), &memo.JoinPrivate{})
    e.expectNoMatch(rule, near)
}
