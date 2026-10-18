package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestExtractRedundantConjunct(t *testing.T) {
	rule := loadRule(t, "ExtractRedundantConjunct")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	eqA := func() opt.ScalarExpr { return e.f.ConstructEq(e.v(cols[0]), e.num(1)) }
	eqB := func() opt.ScalarExpr { return e.f.ConstructEq(e.v(cols[1]), e.num(2)) }
	eqC := func() opt.ScalarExpr { return e.f.ConstructEq(e.v(cols[2]), e.num(3)) }
	eqA5 := func() opt.ScalarExpr { return e.f.ConstructEq(e.v(cols[0]), e.num(5)) }
	before := e.f.ConstructSelect(s, e.filters(
		e.f.ConstructOr(e.f.ConstructAnd(eqA(), eqB()), e.f.ConstructAnd(eqA(), eqC())),
	))
	want := e.f.ConstructSelect(s, e.filters(
		eqA(),
		e.f.ConstructOr(eqB(), eqC()),
	))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(
		e.f.ConstructOr(e.f.ConstructAnd(eqA(), eqB()), e.f.ConstructAnd(eqA5(), eqC())),
	)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(
		e.f.ConstructOr(e.f.ConstructAnd(eqA(), eqB()), eqC()),
	)))
}
