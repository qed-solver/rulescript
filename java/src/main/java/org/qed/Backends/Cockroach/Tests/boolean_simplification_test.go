package rulescript_test

import "testing"

func TestBooleanSimplification(t *testing.T) {
	rule := loadRule(t, "BooleanSimplification")
	e := newEnv(t)
	src, c := e.scan("t", 3)
	a := e.f.ConstructGt(e.v(c[0]), e.num(1))
	b := e.f.ConstructLt(e.v(c[1]), e.num(5))
	d := e.f.ConstructEq(e.v(c[2]), e.num(2))
	a2 := e.f.ConstructGt(e.v(c[0]), e.num(2))
	want := e.f.ConstructSelect(src, e.filters(e.f.ConstructOr(a, e.f.ConstructAnd(b, d))))
	e.expectRewrite(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructOr(a, b), e.f.ConstructOr(a, d))), want)
	e.expectRewrite(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructAnd(e.f.ConstructOr(a, b), e.f.ConstructOr(a, d)))), want)
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructOr(a, b), e.f.ConstructOr(a2, d))))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructAnd(a, b), e.f.ConstructAnd(a, d))))
}
