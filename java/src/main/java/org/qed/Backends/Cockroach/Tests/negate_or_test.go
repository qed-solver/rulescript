package rulescript_test

import (
	"testing"

)

func TestNegateOr(t *testing.T) {
	rule := loadRule(t, "NegateOr")
	e := newEnv(t)
	source, cols := e.scan("t", 3)
	left := e.f.ConstructEq(e.v(cols[0]), e.v(cols[1]))
	right := e.f.ConstructEq(e.v(cols[1]), e.v(cols[2]))
	before := e.f.ConstructSelect(source, e.filters(e.f.ConstructNot(e.f.ConstructOr(left, right))))
	want := e.f.ConstructSelect(source, e.filters(
		e.f.ConstructNot(left), e.f.ConstructNot(right),
	))
	e.expectRewrite(rule, before, want)

	nearSource, nearCols := e.scan("t", 3)
	nearLeft := e.f.ConstructEq(e.v(nearCols[0]), e.v(nearCols[1]))
	nearRight := e.f.ConstructEq(e.v(nearCols[1]), e.v(nearCols[2]))
	nearBefore := e.f.ConstructSelect(
		nearSource, e.filters(e.f.ConstructNot(e.f.ConstructAnd(nearLeft, nearRight))),
	)
	e.expectNoMatch(rule, nearBefore)
}
