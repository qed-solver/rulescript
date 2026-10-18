package rulescript_test

import "testing"

func TestDeduplicateSelectFilters(t *testing.T) {
	rule := loadRule(t, "DeduplicateSelectFilters")
	e := newEnv(t)
	s, c := e.scan("t", 3)
	p := e.f.ConstructEq(e.v(c[0]), e.num(1))
	q := e.f.ConstructGt(e.v(c[1]), e.num(2))
	r := e.f.ConstructEq(e.v(c[2]), e.num(3))
	inner := e.f.ConstructSelect(s, e.filters(p))
	mid := e.f.ConstructSelect(inner, e.filters(q))
	before := e.f.ConstructSelect(mid, e.filters(p))
	want := e.f.ConstructSelect(inner, e.filters(q))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(mid, e.filters(r)))
	e.expectNoMatch(rule, e.f.ConstructSelect(inner, e.filters(p)))
}
