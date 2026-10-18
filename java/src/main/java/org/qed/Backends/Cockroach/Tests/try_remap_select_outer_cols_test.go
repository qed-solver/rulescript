package rulescript_test

import (
	"testing"
)

func TestTryRemapSelectOuterCols(t *testing.T) {
	rule := loadRule(t, "TryRemapSelectOuterCols")
	e := newEnv(t)
	scan, cols := e.scan("nn3", 3)
	h := e.f.ConstructEq(e.v(cols[0]), e.num(7))
	inner := e.f.ConstructSelect(scan, e.filters(h))
	on := e.f.ConstructEq(e.v(cols[0]), e.v(cols[1]))
	before := e.f.ConstructSelect(inner, e.filters(on))
	innerWant := e.f.ConstructSelect(scan, e.filters(e.f.ConstructEq(e.v(cols[1]), e.num(7))))
	want := e.f.ConstructSelect(innerWant, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(cols[1]))))
	e.t.Logf("constructed input: %s", e.format(before))
	e.expectRewrite(rule, before, want)

	scan2, cols2 := e.scan("nn3", 3)
	inner2 := e.f.ConstructSelect(scan2, e.filters(e.f.ConstructEq(e.v(cols2[0]), e.num(7))))
	near := e.f.ConstructSelect(inner2, e.filters(e.f.ConstructEq(e.v(cols2[0]), e.num(0))))
	e.t.Logf("constructed near miss: %s", e.format(near))
	e.expectNoMatch(rule, near)
}
