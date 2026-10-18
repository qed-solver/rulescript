package rulescript_test

import (
	"testing"
)

func TestSimplifyOrTrue(t *testing.T) {
	rule := loadRule(t, "SimplifyOrTrue")
	e := newEnv(t)
	scan, cols := e.scan("t", 3)
	before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructOr(e.v(cols[1]), e.f.ConstructTrue())))
	want := scan
	e.expectRewrite(rule, before, want)

	scan2, cols2 := e.scan("t", 3)
	before2 := e.f.ConstructSelect(scan2, e.filters(e.f.ConstructOr(e.v(cols2[2]), e.f.ConstructTrue())))
	want2 := scan2
	e.expectRewrite(rule, before2, want2)

	scan3, cols3 := e.scan("t", 3)
	nearMiss := e.f.ConstructSelect(scan3, e.filters(e.v(cols3[1])))
	e.expectNoMatch(rule, nearMiss)
}
