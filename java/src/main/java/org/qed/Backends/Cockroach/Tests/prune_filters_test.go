package rulescript_test

import (
	"testing"
)

func TestPruneFilters(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneFilters")
	scan, cols := e.scan("t", 3)
	shared := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	innerRest := e.f.ConstructEq(e.v(cols[1]), e.num(2))
	outerRest := e.f.ConstructEq(e.v(cols[2]), e.num(3))
	before := e.f.ConstructSelect(
		e.f.ConstructSelect(scan, e.filters(shared, innerRest)),
		e.filters(shared, outerRest),
	)
	want := e.f.ConstructSelect(
		e.f.ConstructSelect(scan, e.filters(shared, innerRest)),
		e.filters(outerRest),
	)
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructSelect(
		e.f.ConstructSelect(scan, e.filters(shared, innerRest)),
		e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(4)), outerRest),
	)
	e.expectNoMatch(rule, nearMiss)
}
