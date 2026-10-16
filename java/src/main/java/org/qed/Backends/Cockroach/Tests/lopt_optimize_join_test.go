package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestLoptOptimizeJoin(t *testing.T) {
	rule := loadRule(t, "LoptOptimizeJoin")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)
	pAB := e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))
	pAC := e.f.ConstructEq(e.v(ac[0]), e.v(cc[0]))
	ab := e.f.ConstructInnerJoin(a, b, nil, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(ab, c, e.filters(pAB, pAC), &memo.JoinPrivate{})
	ac2 := e.f.ConstructInnerJoin(a, c, e.filters(pAC), &memo.JoinPrivate{})
	acb := e.f.ConstructInnerJoin(ac2, b, e.filters(pAB), &memo.JoinPrivate{})
	want := e.f.ConstructProject(acb, nil, opt.MakeColSet(ac[0], bc[0], cc[0]))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(ab, c, e.filters(pAB), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(ab, c, e.filters(pAB, pAC), &memo.JoinPrivate{}))
}
