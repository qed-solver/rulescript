package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinToHyperGraph(t *testing.T) {
	rule := loadRule(t, "JoinToHyperGraph")
	e := newEnv(t)
	a, ac := e.scan("u", 1)
	b, bc := e.scan("t", 1)
	c, cc := e.scan("nn", 1)

	ab := e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))
	beforeLeft := e.f.ConstructInnerJoin(a, b, e.filters(ab), &memo.JoinPrivate{})
	acCond := e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))
	bcCond := e.f.ConstructEq(e.v(bc[0]), e.v(cc[0]))
	before := e.f.ConstructInnerJoin(beforeLeft, c, e.filters(acCond, bcCond), &memo.JoinPrivate{})

	wantRight := e.f.ConstructInnerJoin(b, c, e.filters(bcCond), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(a, wantRight, e.filters(ab, acCond), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	wrongBC := e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))
	nearMiss := e.f.ConstructInnerJoin(beforeLeft, c, e.filters(acCond, wrongBC), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
