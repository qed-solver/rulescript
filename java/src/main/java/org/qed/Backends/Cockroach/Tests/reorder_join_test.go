package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestReorderJoin(t *testing.T) {
	rule := loadRule(t, "ReorderJoin")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	d, dc := e.scan("t", 1)
	c, cc := e.scan("t", 1)
	b, bc := e.scan("t", 1)
	ad := e.f.ConstructInnerJoin(a, d, nil, &memo.JoinPrivate{})
	adc := e.f.ConstructInnerJoin(ad, c, e.filters(e.f.ConstructEq(e.v(dc[0]), e.v(cc[0]))), &memo.JoinPrivate{})
	q := e.f.ConstructAnd(e.f.ConstructEq(e.v(dc[0]), e.v(cc[0])), e.f.ConstructEq(e.v(bc[0]), e.v(cc[0])))
	before := e.f.ConstructInnerJoin(adc, b, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0])), q), &memo.JoinPrivate{})

	ab := e.f.ConstructInnerJoin(a, b, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))), &memo.JoinPrivate{})
	abd := e.f.ConstructInnerJoin(ab, d, nil, &memo.JoinPrivate{})
	qAfter := e.f.ConstructAnd(e.f.ConstructEq(e.v(dc[0]), e.v(cc[0])), e.f.ConstructEq(e.v(bc[0]), e.v(cc[0])))
	finalJoin := e.f.ConstructInnerJoin(abd, c, e.filters(e.f.ConstructEq(e.v(dc[0]), e.v(cc[0])), qAfter), &memo.JoinPrivate{})
	want := e.f.ConstructProject(finalJoin, memo.ProjectionsExpr{}, opt.MakeColSet(ac[0], dc[0], cc[0], bc[0]))
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructInnerJoin(adc, b, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
