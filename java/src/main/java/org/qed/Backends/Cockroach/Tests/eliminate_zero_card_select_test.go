package rulescript_test

import (
	"testing"
)

func TestEliminateZeroCardSelect(t *testing.T) {
	rule := loadRule(t, "EliminateZeroCardSelect")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	empty := e.empty(sc[0])
	f1 := e.filters(e.f.ConstructEq(e.v(sc[0]), e.num(1)))
	f2 := e.filters(e.f.ConstructEq(e.v(sc[0]), e.num(2)))
	before := e.f.ConstructSelect(e.f.ConstructSelect(empty, f1), f2)
	e.expectRewrite(rule, before, e.empty(sc[0]))
	e.expectNoMatch(rule, e.f.ConstructSelect(empty, f1))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructSelect(s, f1), f2))
	one := e.intValues(sc, []int{7})
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructSelect(one, f1), f2))
}
