package rulescript_test

import "testing"

func TestHoistUnboundFilterFromExistsSubquery(t *testing.T) {
	rule := loadRule(t, "HoistUnboundFilterFromExistsSubquery")
	e := newEnv(t)
	outer, _ := e.scan("u", 2)
	inner, ic := e.scan("t", 3)
	unboundRel, _ := e.scan("nn", 2)
	unbound := e.exists(unboundRel)
	other := e.f.ConstructEq(e.v(ic[1]), e.num(1))
	innerBefore := e.f.ConstructSelect(inner, e.filters(unbound, other))
	innerAfter := e.f.ConstructSelect(inner, e.filters(other))
	before := e.f.ConstructSelect(outer, e.filters(e.exists(innerBefore)))
	want := e.f.ConstructSelect(outer, e.filters(e.exists(innerAfter), unbound))
	e.expectRewrite(rule, before, want)
	outerCols := outer.Relational().OutputCols.ToList()
	correlated := e.f.ConstructGt(e.v(outerCols[0]), e.num(3))
	corrBefore := e.f.ConstructSelect(outer, e.filters(e.exists(e.f.ConstructSelect(inner, e.filters(correlated, other)))))
	corrWant := e.f.ConstructSelect(outer, e.filters(e.exists(innerAfter), correlated))
	e.expectRewrite(rule, corrBefore, corrWant)
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(inner))))
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(innerAfter))))
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.f.ConstructNot(e.exists(innerBefore)))))
}
