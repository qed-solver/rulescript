package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestExpandDisjunctionForTable(t *testing.T) {
	rule := loadRule(t, "ExpandDisjunctionForTable")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	x := e.v(lc[0])
	a := e.v(rc[0])
	pJ := e.f.ConstructLt(x, a)
	pL1 := e.f.ConstructEq(x, e.num(1))
	pL2 := e.f.ConstructEq(x, e.num(2))
	pR1 := e.f.ConstructEq(a, e.num(3))
	pR2 := e.f.ConstructEq(a, e.num(4))
	core := e.f.ConstructOr(
		e.f.ConstructAnd(pL1, pR1),
		e.f.ConstructAnd(pL2, pR2),
	)
	orLeft := e.f.ConstructOr(pL1, pL2)
	orRight := e.f.ConstructOr(pR1, pR2)
	cond := e.f.ConstructAnd(pJ, core)
	expanded := e.f.ConstructAnd(e.f.ConstructAnd(cond, orLeft), orRight)

	before := e.f.ConstructInnerJoin(l, r, e.filters(cond), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(expanded), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(cond), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r,
		e.filters(e.f.ConstructAnd(pJ, e.f.ConstructAnd(pL1, pR1))), &memo.JoinPrivate{}))
}
