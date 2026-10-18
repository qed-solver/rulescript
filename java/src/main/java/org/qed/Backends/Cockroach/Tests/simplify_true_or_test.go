package rulescript_test

import (
	"testing"

)

func TestSimplifyTrueOr(t *testing.T) {
	rule := loadRule(t, "SimplifyTrueOr")
	e := newEnv(t)

	scan, cols := e.scan("t", 3)
	right := e.v(cols[1])
	before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructOr(e.f.ConstructTrue(), right)))
	want := e.f.ConstructSelect(scan, e.filters(e.f.ConstructTrue()))
	e.expectRewrite(rule, before, want)

	scan2, cols2 := e.scan("t", 3)
	right2 := e.v(cols2[2])
	before2 := e.f.ConstructSelect(scan2, e.filters(e.f.ConstructOr(e.f.ConstructTrue(), right2)))
	want2 := e.f.ConstructSelect(scan2, e.filters(e.f.ConstructTrue()))
	e.expectRewrite(rule, before2, want2)

	scan3, cols3 := e.scan("t", 3)
	miss := e.f.ConstructSelect(scan3, e.filters(e.f.ConstructAnd(e.f.ConstructTrue(), e.v(cols3[1]))))
	e.expectNoMatch(rule, miss)
}
