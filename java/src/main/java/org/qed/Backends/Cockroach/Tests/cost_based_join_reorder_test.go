package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestCostBasedJoinReorder(t *testing.T) {
	rule := loadRule(t, "CostBasedJoinReorder")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)
	d, dc := e.scan("nn3", 1)
	va, vb, vc, vd := e.v(ac[0]), e.v(bc[0]), e.v(cc[0]), e.v(dc[0])

	ab := e.f.ConstructInnerJoin(a, b, e.filters(e.f.ConstructEq(va, vb)), &memo.JoinPrivate{})
	abc := e.f.ConstructInnerJoin(ab, c, e.filters(
		e.f.ConstructEq(va, vc), e.f.ConstructEq(vb, vc)), &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(abc, d, e.filters(e.f.ConstructEq(vc, vd)), &memo.JoinPrivate{})

	cd := e.f.ConstructInnerJoin(c, d, e.filters(e.f.ConstructEq(vc, vd)), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(ab, cd, e.filters(
		e.f.ConstructEq(va, vc), e.f.ConstructEq(vb, vc)), &memo.JoinPrivate{})

	e.expectRewrite(rule, before, want)

	leftRoot := e.f.ConstructLeftJoin(abc, d, e.filters(e.f.ConstructEq(vc, vd)), &memo.JoinPrivate{})
	e.expectNoMatch(rule, leftRoot)

	abOnlyAC := e.f.ConstructInnerJoin(ab, c, e.filters(e.f.ConstructEq(va, vc)), &memo.JoinPrivate{})
	missingConjunct := e.f.ConstructInnerJoin(abOnlyAC, d, e.filters(e.f.ConstructEq(vc, vd)), &memo.JoinPrivate{})
	e.expectRewrite(rule, missingConjunct, e.f.ConstructInnerJoin(ab, cd, e.filters(e.f.ConstructEq(va, vc)), &memo.JoinPrivate{}))

	wrongColumn := e.f.ConstructInnerJoin(abc, d, e.filters(e.f.ConstructEq(vb, vd)), &memo.JoinPrivate{})
	e.expectNoMatch(rule, wrongColumn)
}
