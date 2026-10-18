package rulescript_test

import "testing"

func TestSimplifyAndTrue(t *testing.T) {
	rule := loadRule(t, "SimplifyAndTrue")
	e := newEnv(t)

	scan1, cols1 := e.scan("t", 2)
	pred1 := e.f.ConstructEq(e.v(cols1[0]), e.num(7))
	before1 := e.f.ConstructSelect(scan1, e.filters(e.f.ConstructAnd(pred1, e.f.ConstructTrue())))
	want1 := e.f.ConstructSelect(scan1, e.filters(pred1))
	e.expectRewrite(rule, before1, want1)

	scan2, cols2 := e.scan("u", 2)
	pred2 := e.f.ConstructEq(e.v(cols2[1]), e.num(12))
	before2 := e.f.ConstructSelect(scan2, e.filters(e.f.ConstructAnd(pred2, e.f.ConstructTrue())))
	want2 := e.f.ConstructSelect(scan2, e.filters(pred2))
	e.expectRewrite(rule, before2, want2)

	scan3, cols3 := e.scan("t", 1)
	pred3 := e.f.ConstructEq(e.v(cols3[0]), e.num(3))
	nearMiss := e.f.ConstructSelect(scan3, e.filters(e.f.ConstructOr(pred3, e.f.ConstructTrue())))
	e.expectNoMatch(rule, nearMiss)
}
