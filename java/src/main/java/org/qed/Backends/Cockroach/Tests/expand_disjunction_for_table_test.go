package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestExpandDisjunctionForTable(t *testing.T) {
	rule := loadRule(t, "ExpandDisjunctionForTable")
	e := newEnv(t)
	left, lc := e.scan("t", 1)
	right, rc := e.scan("u", 1)

	pL1 := e.f.ConstructGt(e.v(lc[0]), e.num(1))
	pR1 := e.f.ConstructLt(e.v(rc[0]), e.num(5))
	pL2 := e.f.ConstructEq(e.v(lc[0]), e.num(7))
	pR2 := e.f.ConstructNe(e.v(rc[0]), e.num(3))
	pJ := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	core := e.f.ConstructOr(e.f.ConstructAnd(pL1, pR1), e.f.ConstructAnd(pL2, pR2))

	before := e.f.ConstructInnerJoin(left, right, e.filters(pJ, core), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(left, right, e.filters(
		e.f.ConstructAnd(
			e.f.ConstructAnd(e.f.ConstructAnd(pJ, core), e.f.ConstructOr(pL1, pL2)),
			e.f.ConstructOr(pR1, pR2),
		),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(left, right, e.filters(pJ, core), &memo.JoinPrivate{}))

	nearCore := e.f.ConstructOr(
		e.f.ConstructAnd(pL1, pR1),
		e.f.ConstructAnd(e.f.ConstructEq(e.v(rc[0]), e.num(7)), pR2),
	)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(left, right, e.filters(pJ, nearCore), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(left, right, e.filters(core), &memo.JoinPrivate{}))
}
